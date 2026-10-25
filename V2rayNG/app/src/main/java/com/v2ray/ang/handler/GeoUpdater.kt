package com.v2ray.ang.handler

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * In-app updates of geoip.dat / geosite.dat from the region's rules release (like v2rayN):
 * Iran = Chocolate4U/Iran-v2ray-rules, Russia = runetfreedom/russia-v2ray-rules-dat,
 * China = Loyalsoldier/v2ray-rules-dat. A downloaded file only replaces the current one if it
 * has the expected size and contains every geosite:/geoip: tag the routing rules use (otherwise
 * the core would refuse to start). The previous file is kept as a rollback copy.
 * The Xray core itself lives in libv2ray.aar and is updated with the app, not here.
 */
object GeoUpdater {
    val FILES = listOf(AppConfig.GEOIP_DAT, AppConfig.GEOSITE_DAT)
    const val REPO_IRAN = "Chocolate4U/Iran-v2ray-rules"
    const val REPO_RUSSIA = "runetfreedom/russia-v2ray-rules-dat"
    const val REPO_CHINA = "Loyalsoldier/v2ray-rules-dat"

    private const val PREF_TAG = "pref_satra_geo_tag"
    private const val PREF_TAG_REPO = "pref_satra_geo_tag_repo"
    private const val PREF_LATEST = "pref_satra_geo_latest"
    private const val PREF_CHECKED = "pref_satra_geo_checked"
    private const val PREF_AUTO = "pref_satra_geo_auto"
    private const val WORK = "satra_geo_weekly"
    const val LAUNCH_CHECK_MS = 12L * 3600_000

    data class Remote(val repo: String, val tag: String, val assets: Map<String, Pair<String, Long>>)

    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data class Downloading(val file: String, val done: Long, val total: Long) : Phase
        data class Done(val updated: Boolean, val message: String? = null) : Phase
        data class Failed(val reason: String) : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    // ---------- pure, unit-tested ----------

    /** Region repo from the stored routing rules. */
    fun repoFor(stored: List<RulesetItem>?): String = when {
        RoutingPolicy.isRussia(stored) -> REPO_RUSSIA
        RoutingPolicy.isChina(stored) -> REPO_CHINA
        else -> REPO_IRAN
    }

    /** Parses GitHub "releases/latest" JSON. */
    fun parseRelease(repo: String, json: String): Remote? = try {
        val o = com.google.gson.JsonParser.parseString(json).asJsonObject
        val tag = o.get("tag_name").asString
        val assets = o.getAsJsonArray("assets").associate { a ->
            val ao = a.asJsonObject
            ao.get("name").asString to (ao.get("browser_download_url").asString to ao.get("size").asLong)
        }.filterKeys { it in FILES }
        if (assets.keys.containsAll(FILES)) Remote(repo, tag, assets) else null
    } catch (e: Exception) {
        null
    }

    fun isNewer(installedTag: String?, installedRepo: String?, remote: Remote): Boolean =
        installedRepo != remote.repo || installedTag != remote.tag

    /** Uppercase tag names a file must contain, e.g. geosite:category-ir -> CATEGORY-IR (skips ext: and attributes). */
    fun requiredTags(rules: List<RulesetItem>, prefix: String): Set<String> =
        rules.filter { it.enabled }.flatMap { it.domain.orEmpty() + it.ip.orEmpty() }
            .filter { it.startsWith("$prefix:") }
            .map { it.removePrefix("$prefix:").substringBefore('@').uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Byte search for each tag (they are stored as plain strings in the .dat protobuf). */
    fun missingTags(input: java.io.InputStream, tags: Set<String>): Set<String> {
        val left = tags.flatMap { listOf(it, it.lowercase()) }.toMutableSet()
        if (left.isEmpty()) return emptySet()
        val overlap = left.maxOf { it.length }
        val buf = ByteArray(1 shl 20); var carry = ""
        while (left.isNotEmpty()) {
            val n = input.read(buf); if (n < 0) break
            val text = carry + String(buf, 0, n, Charsets.ISO_8859_1)
            left.removeAll { text.contains(it) }
            carry = text.takeLast(overlap)
        }
        return tags.filter { it in left && it.lowercase() in left }.toSet()
    }

    // ---------- Android ----------

    fun installedTag(): String? = MmkvManager.decodeSettingsString(PREF_TAG)
    fun installedRepo(): String? = MmkvManager.decodeSettingsString(PREF_TAG_REPO)
    fun latestTag(): String? = MmkvManager.decodeSettingsString(PREF_LATEST)
    fun lastChecked(): Long = MmkvManager.decodeSettingsString(PREF_CHECKED)?.toLongOrNull() ?: 0L
    fun autoOn(): Boolean = MmkvManager.decodeSettingsBool(PREF_AUTO, true)
    fun setAuto(context: Context, on: Boolean) {
        MmkvManager.encodeSettings(PREF_AUTO, on)
        schedule(context)
    }

    fun currentRepo(): String = repoFor(MmkvManager.decodeRoutingRulesets())

    fun dir(context: Context) = File(Utils.userAssetPath(context))
    fun hasRollback(context: Context) = FILES.all { File(dir(context), "$it.bak").exists() }

    private fun proxy(): Proxy =
        if (CoreServiceManager.isRunning()) Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort()))
        else Proxy.NO_PROXY

    private fun open(url: String, proxy: Proxy): HttpURLConnection =
        (URL(url).openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SatraNG")
        }

    private fun <T> viaProxyThenDirect(block: (Proxy) -> T): T {
        val p = proxy()
        return try { block(p) } catch (e: Exception) { if (p == Proxy.NO_PROXY) throw e else block(Proxy.NO_PROXY) }
    }

    suspend fun check(): Remote? = withContext(Dispatchers.IO) {
        _phase.value = Phase.Checking
        val repo = currentRepo()
        try {
            val json = viaProxyThenDirect { p ->
                open("https://api.github.com/repos/$repo/releases/latest", p).run {
                    setRequestProperty("Accept", "application/vnd.github+json")
                    try { inputStream.bufferedReader().readText() } finally { disconnect() }
                }
            }
            val r = parseRelease(repo, json) ?: error("bad release")
            MmkvManager.encodeSettings(PREF_LATEST, r.tag)
            MmkvManager.encodeSettings(PREF_CHECKED, System.currentTimeMillis().toString())
            _phase.value = Phase.Idle
            r
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Geo check failed", e)
            _phase.value = Phase.Failed(e.message ?: "network")
            null
        }
    }

    /** Downloads and atomically installs both files; true when updated. */
    suspend fun update(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val r = check() ?: return@withContext false
        if (!force && !isNewer(installedTag(), installedRepo(), r)) {
            _phase.value = Phase.Done(false); return@withContext false
        }
        val d = dir(context)
        val rules = MmkvManager.decodeRoutingRulesets().orEmpty()
        val temps = mutableMapOf<String, File>()
        try {
            for (name in FILES) {
                val (url, size) = r.assets.getValue(name)
                val tmp = File(d, "$name.dl")
                viaProxyThenDirect { p ->
                    val c = open(url, p)
                    try {
                        c.inputStream.use { input ->
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024); var done = 0L; var last = 0L
                                while (true) {
                                    val n = input.read(buf); if (n < 0) break
                                    out.write(buf, 0, n); done += n
                                    if (done - last > 256 * 1024 || done == size) { last = done; _phase.value = Phase.Downloading(name, done, size) }
                                }
                            }
                        }
                    } finally { c.disconnect() }
                }
                if (tmp.length() != size) error("$name: size ${tmp.length()} != $size")
                val prefix = if (name == AppConfig.GEOIP_DAT) "geoip" else "geosite"
                val missing = tmp.inputStream().buffered().use { missingTags(it, requiredTags(rules, prefix)) }
                if (missing.isNotEmpty()) error("$name lacks ${missing.joinToString()}")
                temps[name] = tmp
            }
            for ((name, tmp) in temps) {
                val cur = File(d, name)
                if (cur.exists()) cur.copyTo(File(d, "$name.bak"), overwrite = true)
                if (!tmp.renameTo(cur)) { tmp.copyTo(cur, overwrite = true); tmp.delete() }
            }
            MmkvManager.encodeSettings(PREF_TAG, r.tag)
            MmkvManager.encodeSettings(PREF_TAG_REPO, r.repo)
            SettingsChangeManager.makeRestartService()
            _phase.value = Phase.Done(true)
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Geo update failed", e)
            temps.values.forEach { it.delete() }
            FILES.forEach { File(d, "$it.dl").delete() }
            _phase.value = Phase.Failed(e.message ?: "error")
            false
        }
    }

    /** Swaps the rollback copy back in. */
    fun rollback(context: Context): Boolean {
        val d = dir(context)
        if (!hasRollback(context)) return false
        for (name in FILES) {
            val cur = File(d, name); val bak = File(d, "$name.bak"); val t = File(d, "$name.swap")
            cur.renameTo(t); bak.renameTo(cur); t.renameTo(bak)
        }
        MmkvManager.encodeSettings(PREF_TAG, "rollback")
        SettingsChangeManager.makeRestartService()
        _phase.value = Phase.Done(true)
        return true
    }

    /** Weekly, unmetered, battery not low; no other wakeups. */
    fun schedule(context: Context) {
        val wm = RemoteWorkManager.getInstance(context)
        if (!autoOn()) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<Task>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** On launch: at most every 12 h, check and (when auto is on) install new geo/rules; update() validates and keeps .bak for rollback. */
    suspend fun checkOnLaunch(context: Context) {
        schedule(context)
        if (autoOn() && System.currentTimeMillis() - lastChecked() > LAUNCH_CHECK_MS) {
            check()
            if (updateAvailable()) runCatching { update(context) }
        }
    }

    fun updateAvailable(): Boolean {
        val l = latestTag() ?: return false
        return l != installedTag() || installedRepo() != currentRepo()
    }

    class Task(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (autoOn()) update(applicationContext)
            return Result.success()
        }
    }
}

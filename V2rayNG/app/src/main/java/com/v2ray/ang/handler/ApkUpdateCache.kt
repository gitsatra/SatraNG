package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads SatraNG APKs into cache/updates/{version}.apk and reuses a complete file on the next tap.
 */
object ApkUpdateCache {

    data class Progress(val downloaded: Long, val total: Long?)

    fun cachedApk(context: Context, version: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        return File(dir, "SatraNG-$version.apk")
    }

    fun isComplete(file: File, expectedBytes: Long?): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        if (expectedBytes != null && expectedBytes > 0L) return file.length() >= expectedBytes
        return true
    }

    suspend fun download(
        context: Context,
        version: String,
        url: String,
        onProgress: (Progress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dest = cachedApk(context, version)
        if (isComplete(dest, null) && dest.length() > 1_000_000L) {
            onProgress(Progress(dest.length(), dest.length()))
            return@withContext dest
        }
        val tmp = File(dest.absolutePath + ".part")
        if (HubNet.isHub(url)) {
            val req = okhttp3.Request.Builder().url(url)
                .header("User-Agent", "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}").build()
            HubNet.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val total = r.body.contentLength().takeIf { it > 0 }
                r.body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var got = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n); got += n
                            onProgress(Progress(got, total))
                        }
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) error("rename failed")
            return@withContext dest
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        got += n
                        onProgress(Progress(got, total))
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            dest
        } finally {
            conn.disconnect()
        }
    }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.cache",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openOrInstall(context: Context, apk: File) {
        try {
            context.startActivity(installIntent(context, apk))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ApkUpdateCache: install failed", e)
        }
    }
}

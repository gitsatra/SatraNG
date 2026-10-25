package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.v2ray.ang.util.JsonUtil
import java.util.Locale

/**
 * Iranian apps that must not see the VPN at all (banks and many local apps refuse to open when a
 * VPN transport is up; domain routing cannot hide that). With smart routing (Iran) on, these are
 * excluded from the VpnService (addDisallowedApplication) and use the normal network directly.
 */
object IranApps {
    const val PREF_ADDED = "pref_satra_ir_apps_added"
    const val PREF_REMOVED = "pref_satra_ir_apps_removed"

    /** Curated list (banks, payments, operators, stores, maps, messengers, video, shopping). */
    val CURATED: Set<String> = setOf(
        // Banks
        "ir.bmi.bam.nativeweb", "com.bmi.mobilebank", "mob.banking.android", "com.pmb.mobile",
        "com.isc.bsinew", "com.sepah.mobilebank", "ir.tejaratbank.tata.mobile.android.tejarat",
        "com.parsian.mobilebank", "ir.parsian.mobilebank", "com.bpi.ng.mbank", "ir.bpi.mobilebank",
        "com.refahbank.dpi.android", "ir.refahbank.mobilebank", "com.bki.mobilebank", "ir.bki.mobilebank",
        "com.bankmaskan.mobilebank", "ir.bankmaskan.mobilebank", "com.shahr.mobilebank", "ir.shahr_bank.mobilebank",
        "com.ba24.key", "ir.ayandeh.mobilebank", "com.saderat.mobilebank", "ir.saderat.mobilebank",
        "com.ada.mbank.mehr", "com.samanpr.blu", "ir.sb24.blu", "com.tosantechno.mobilebank", "ir.enbank.mobilebank",
        "com.postbank.mobilebank", "ir.postbank.mobilebank", "com.sina.mobilebank", "ir.sinabank.mobilebank",
        // Payments / wallets / shetab
        "com.sadadpsp.eva", "ir.sep.sepino", "ir.asanpardakht.android.ap", "com.asanpardakht.android",
        "com.top.app", "ir.top.app", "com.mydigipay.app.android", "ir.ghesta.app", "ir.tara.app", "com.tara360.app",
        "ir.shaparak.app", "com.kipaad.app", "com.bpm.sekeh", "com.hamrahcard.app", "ir.hamrahcard.app",
        // Operators
        "ir.mci.ecareapp", "com.myirancell", "ir.irancell.myirancell", "com.rightel.myrightel", "ir.rightel.app",
        // Messengers
        "ir.nasim", "ir.eitaa.messenger", "ir.resaneh1.iptv", "mobi.mmdt.ottplus", "ir.ilmili.telegraph",
        // Ride / delivery / shopping
        "cab.snapp.passenger", "cab.snapp.driver", "ir.snappfood", "taxi.tap30.passenger", "taxi.tap30.driver",
        "com.digikala", "ir.divar", "com.torob", "ir.basalam.app", "com.sheypoor.mobile", "ir.okala.app",
        // Stores / maps
        "com.farsitel.bazaar", "ir.mservices.market", "ir.balad", "org.rajman.neshan.traffic.tehran",
        // Video
        "com.aparat", "com.namava.android", "com.sabaidea.filimo", "ir.filimo", "net.telewebion", "com.telewebion",
        // Government / services
        "ir.mygov.app", "ir.adliran.sana", "ir.tax.app", "ir.sabtahval.app",
        // More banks (Mellat, Melli, Saman, Pasargad, Tejarat, Day, Resalat, Ansar, Tosee, Sepah, Keshavarzi, Karafarin, Iran Zamin, Gardeshgari, Middle East, Khavarmianeh, Sarmayeh)
        "com.mellatbank.mobilebank", "ir.mellat.mobilebank", "com.mellat.mobilebank", "com.bankmellat.mobile", "ir.bankmellat.hamrahbank",
        "ir.bmi.bale", "com.bmi.bam", "ir.bmi.bam", "com.sb24.mobilebank", "ir.sb24.mobilebank", "ir.sb24.sibi",
        "com.pasargad.mobilebank", "ir.bpi.hamrahbank", "com.bpi.mobilebank", "ir.tejaratbank.mobilebank", "com.tejaratbank.mobile",
        "ir.daybank.mobilebank", "com.daybank.mobile", "ir.rqbank.mobilebank", "com.rqbank.mobilebank", "ir.ansarbank.mobilebank",
        "com.ttbank.mobilebank", "ir.ttbank.mobilebank", "ir.sepahbank.mobilebank", "com.bki.bankinoo", "ir.karafarinbank.mobilebank",
        "com.izbank.mobilebank", "ir.izbank.mobilebank", "ir.tourismbank.mobilebank", "ir.middleeastbank.mobilebank", "com.mebank.mobile",
        "ir.sarmayeh.mobilebank", "com.sbank.mobilebank", "ir.bsi.mobilebank", "com.edbi.mobilebank", "ir.bim.mobilebank",
        "com.bankino.app", "ir.bankino.app", "com.ada.mbank.mehriran", "ir.qmb.mehr", "com.wepod.app", "ir.pod.wepod", "com.hamrahbank.app",
        // Payments / fintech / crypto exchanges
        "com.sadadpsp.sadad", "ir.sadadpsp.bahamta", "com.sepehr.pay", "ir.sepehr.pay", "com.pec.app", "ir.pec.mobile", "com.irankish.app",
        "com.zarinpal.app", "ir.zarinpal.app", "com.payping.app", "com.jibit.app", "com.iranicard.app", "com.vandar.app",
        "ir.nobitex.app", "market.nobitex", "com.wallex.app", "ir.wallex.app", "com.excoino.app", "com.ramzinex.app", "com.bitpin.app",
        "ir.snapppay.app", "com.snapp.pay", "com.azki.app", "ir.azki.app", "com.bimebazar.app", "ir.bimeh.app", "com.bimito.app",
        "com.digipay.app", "ir.taaghche", "com.ketabrah.app", "ir.fidibo", "ir.fidibo.app",
        // Operators / ISPs
        "com.mci.hamrahman", "ir.mci.hamrahman", "com.irancell.app", "ir.shatel.app", "com.asiatech.app", "ir.mobinnet.app", "ir.tci.app",
        // Messengers / social / news
        "ir.gap.android", "ir.soroush", "mobi.mmdt.ott", "ir.shad", "ir.medu.shad", "com.rubika", "app.rbmain.a", "ir.virasty.app",
        "com.cloob.app", "ir.isna.app", "com.varzesh3", "ir.tasnimnews.android", "ir.farsnews.app", "ir.khabaronline.android",
        // Ride / delivery / travel / shopping / services
        "com.snapp.box", "cab.snapp.box", "ir.snappdoctor", "ir.snapp.market", "com.snappmarket", "com.snapptrip.app", "ir.snapptrip",
        "com.alibaba.ir", "ir.alibaba", "ir.alibaba.app", "com.flytoday", "com.mrbilit.app", "ir.mrbilit", "com.raja.app", "ir.ghatar",
        "ir.digikala", "com.digikala.jet", "ir.digistyle", "com.digistyle", "com.timcheh.app", "com.dgkala.dgfresh", "ir.tapsishop",
        "ir.miare", "com.alopeyk.app", "com.reyhoon", "ir.chilivery", "com.zoodfood", "com.jabama", "ir.jabama", "com.otaghak.app",
        "ir.doctoreto", "ir.paziresh24", "com.nobat.app", "com.drsaina", "ir.ostadkar", "ir.achareh", "com.sibapp", "com.anardoni",
        "ir.myket.android", "com.sibche", "ir.sibche", "ir.iranapps.market",
        // Video / music
        "com.filimo.android", "com.sabaidea.aparat", "ir.lenz", "ir.anten", "com.tva.app", "ir.irib.sepehr", "ir.tamasha", "com.navaar",
        "com.radiojavan.androidradio", "ir.bia2", "com.melobit",
        // Maps / navigation
        "ir.neshan.app", "org.neshan.app", "ir.balad.app",
        // Government / e-gov / education
        "ir.sana.adliran", "ir.adliran", "ir.ssi.app", "ir.tamin.app", "ir.ssa.app", "ir.police.app", "ir.naja.app", "com.mygov",
        "ir.mohmd.salamat", "ir.salamat", "ir.bimehsalamat", "ir.dolat.app", "ir.medu.app", "ir.sanjesh.app", "ir.iranhealth",
        "ir.post.app", "ir.ihio.app", "ir.ecaptcha.app", "ir.tamin.mobile", "ir.irica.app", "ir.mporg.app",
    )

    /** Iranian app stores: anything they installed is treated as a local app (they distribute almost only Iranian apps). */
    val IRANIAN_INSTALLERS: Set<String> = setOf(
        "com.farsitel.bazaar", "ir.mservices.market", "ir.myket.android", "com.sibche", "ir.sibche", "com.sibapp",
        "ir.iranapps.market", "com.anardoni", "ir.cafebazaar", "com.parshub.market", "ir.charkhoneh", "com.avvalmarket",
    )

    /** Never bypassed even if heuristics match (global apps often shown with Persian labels). */
    private val NEVER = setOf(
        "org.telegram.messenger", "org.telegram.plus", "com.whatsapp", "com.instagram.android",
        "com.google.android.youtube", "com.android.chrome", "org.mozilla.firefox",
    )

    private val PERSIAN = Regex("[\u0600-\u06FF]")

    /**
     * Global vendors that Bazaar/Myket also redistribute (Google Keep, Microsoft RDP, MIUI player...).
     * "Installed from an Iranian store" alone must not bypass these: they need the open internet.
     */
    private val GLOBAL_VENDOR_PREFIXES = listOf(
        "com.google.", "com.android.", "com.microsoft.", "com.miui.", "com.xiaomi.", "com.mi.", "com.samsung.",
        "com.sec.", "com.huawei.", "com.oneplus.", "com.facebook.", "com.instagram.", "com.whatsapp", "org.telegram.",
        "com.twitter.", "com.zhiliaoapp.", "com.ss.android.", "com.discord", "com.snapchat.", "com.spotify.",
        "com.netflix.", "com.amazon.", "com.adobe.", "com.dropbox.", "com.skype.", "us.zoom.", "com.opera.",
        "org.mozilla.", "com.brave.", "com.duckduckgo.", "com.github.", "com.openai.", "com.anthropic.",
        "com.linkedin.", "com.reddit.", "com.pinterest", "com.viber.", "org.thoughtcrime.", "com.myvodafoneapp",
        "com.vodafone.", "com.paypal.", "com.binance.", "com.okx.", "com.bybit.", "com.wallet.crypto.", "io.metamask",
    )

    private fun isGlobalVendor(p: String) = GLOBAL_VENDOR_PREFIXES.any { p.startsWith(it) }

    /** Heuristic: ir.* / *.ir / *.ir.* package names, or a Persian default (non-localized) label. */
    fun looksIranian(pkg: String, defaultLabel: String?, installer: String? = null): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        if (p in NEVER) return false
        if (p.startsWith("ir.") || p.endsWith(".ir") || p.contains(".ir.") || p.contains("iran")) return true
        if (installer != null && installer in IRANIAN_INSTALLERS && !isGlobalVendor(p)) return true
        return defaultLabel != null && PERSIAN.containsMatchIn(defaultLabel)
    }

    /** Why an app was detected (shown in the picker). */
    fun reason(pkg: String, defaultLabel: String?, installer: String?): String? {
        val p = pkg.lowercase(Locale.ROOT)
        return when {
            p in NEVER -> null
            p in CURATED -> "list"
            installer != null && installer in IRANIAN_INSTALLERS && !isGlobalVendor(p) -> "store"
            p.startsWith("ir.") || p.endsWith(".ir") || p.contains(".ir.") || p.contains("iran") -> "package"
            defaultLabel != null && PERSIAN.containsMatchIn(defaultLabel) -> "label"
            else -> null
        }
    }

    /**
     * Packages to exclude from the VPN. Pure; unit-tested.
     * [installed] = (package, default label or null); [alwaysVpn] wins over everything.
     */
    fun bypassSet(
        installed: List<Pair<String, String?>>,
        added: Collection<String>,
        removed: Collection<String>,
        alwaysVpn: Collection<String>,
        self: String,
        installers: Map<String, String?> = emptyMap(),
    ): Set<String> {
        val names = installed.map { it.first }.toSet()
        val detected = installed.filter { (p, l) -> p in CURATED || looksIranian(p, l, installers[p]) }.map { it.first }
        return (detected + added.filter { it in names })
            .filterNot { it in removed || it in alwaysVpn || it == self || it in NEVER }
            .toSortedSet()
    }

    // ---------- Android ----------

    private fun list(key: String): List<String> =
        MmkvManager.decodeSettingsString(key)?.let { JsonUtil.fromJsonSafe(it, Array<String>::class.java)?.toList() }.orEmpty()

    fun added() = list(PREF_ADDED)
    fun removed() = list(PREF_REMOVED)

    fun setAdded(v: List<String>) { MmkvManager.encodeSettings(PREF_ADDED, JsonUtil.toJson(v.distinct())) }
    fun setRemoved(v: List<String>) { MmkvManager.encodeSettings(PREF_REMOVED, JsonUtil.toJson(v.distinct())) }

    /** Label as shipped (not the device locale), so a Persian UI doesn't make every app "Iranian". */
    private fun defaultLabel(pm: PackageManager, ai: ApplicationInfo): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        return try {
            val cfg = android.content.res.Configuration().apply { setLocale(Locale.ENGLISH) }
            val res = pm.getResourcesForApplication(ai, cfg)
            if (ai.labelRes != 0) res.getString(ai.labelRes) else ai.nonLocalizedLabel?.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun installed(context: Context): List<Pair<String, String?>> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName in CURATED }
                .map { it.packageName to defaultLabel(pm, it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).let { it.initiatingPackageName ?: it.installingPackageName }
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
    } catch (e: Exception) {
        null
    }

    fun installers(context: Context, pkgs: Collection<String>): Map<String, String?> {
        val pm = context.packageManager
        return pkgs.associateWith { installerOf(pm, it) }
    }

    fun current(context: Context): Set<String> {
        val inst = installed(context)
        return bypassSet(inst, added(), removed(), RoutingPolicy.values(RoutingPolicy.PREF_VPN_APPS), context.packageName,
            installers(context, inst.map { it.first }))
    }
}

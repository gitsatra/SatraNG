package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/** App groups used by the app picker and by category limits. Order = picker section order. */
enum class AppCategory { SOCIAL, MESSAGING, VIDEO, GAMES, BROWSERS, VPN, SHOPPING, PRODUCTIVITY, OTHER, SYSTEM }

/**
 * Sorts apps into [AppCategory]s: a curated package list first (it knows the popular apps,
 * including Iranian ones, and is right even for preinstalled copies), then Android's own
 * [ApplicationInfo.category] (set by the developer, Android 8+), with remaining preinstalled
 * apps filed as System. Pure where it can be, so the rules are unit tested.
 */
object AppCategories {

    /** Exact package names, or prefixes ending in '.'. */
    private val curated: Map<AppCategory, List<String>> = mapOf(
        AppCategory.SOCIAL to listOf(
            "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.facebook.lite",
            "com.twitter.android", "com.snapchat.android", "com.pinterest", "com.reddit.frontpage",
            "com.linkedin.android", "com.tumblr", "org.joinmastodon.android", "com.bereal.ft",
            "com.vkontakte.android", "ru.ok.android", "com.quora.android", "com.bluesky.app", "xyz.blueskyweb.app",
        ),
        AppCategory.MESSAGING to listOf(
            "org.telegram.", "org.thunderdog.challegram", "com.whatsapp", "com.whatsapp.w4b",
            "org.thoughtcrime.securesms", "com.viber.voip", "jp.naver.line.android", "com.facebook.orca",
            "com.discord", "com.skype.raider", "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "ir.eitaa.messenger", "mobi.mmdt.ottplus", "ir.nasim", "app.rbmain.a", "com.tencent.mm",
            "com.kakao.talk", "im.vector.app", "ch.threema.app", "com.imo.android.imoim", "ir.ilmili.telegraph",
        ),
        AppCategory.VIDEO to listOf(
            "com.google.android.youtube", "com.google.android.apps.youtube.", "app.revanced.android.youtube",
            "com.netflix.mediaclient", "com.zhiliaoapp.musically", "com.ss.android.ugc.", "tv.twitch.android.app",
            "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.spotify.music", "com.soundcloud.android",
            "org.videolan.vlc", "com.sabaidea.aparat", "com.sabaidea.filimo", "com.apple.android.music",
            "deezer.android.app", "com.hbo.hbonow", "com.wbd.stream", "com.google.android.apps.youtube.music",
            "ir.namava.", "com.telewebion", "ir.tamasha.",
        ),
        AppCategory.GAMES to listOf(
            "com.supercell.", "com.tencent.ig", "com.pubg.", "com.activision.callofduty.", "com.mojang.minecraftpe",
            "com.king.", "com.roblox.client", "com.miHoYo.", "com.HoYoverse.", "com.garena.", "com.ea.gp.",
            "com.gameloft.", "com.dts.freefireth", "com.epicgames.", "com.kiloo.subwaysurf", "com.ubisoft.",
        ),
        AppCategory.BROWSERS to listOf(
            "com.android.chrome", "com.chrome.", "org.mozilla.", "com.opera.", "com.brave.browser", "com.microsoft.emmx",
            "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
            "com.kiwibrowser.browser", "org.torproject.torbrowser", "com.mi.globalbrowser", "com.UCMobile.intl",
            "org.bromite.", "com.yandex.browser", "mark.via.gp", "com.android.browser",
        ),
        AppCategory.VPN to listOf(
            "com.v2ray.ang", "com.satra.ng", "com.github.kr328.clash", "com.github.metacubex.", "io.nekohasekai.",
            "moe.nb4a", "com.github.shadowsocks", "org.outline.android.client", "net.openvpn.openvpn",
            "de.blinkt.openvpn", "com.wireguard.android", "ch.protonvpn.android", "com.psiphon3",
            "org.torproject.android", "app.hiddify.com", "com.cloudflare.onedotonedotonedotone",
            "com.nordvpn.android", "com.expressvpn.vpn", "com.windscribe.vpn", "free.vpn.unblock.proxy.",
        ),
        AppCategory.SHOPPING to listOf(
            "com.amazon.mShop.android.shopping", "com.digikala", "com.ebay.mobile", "com.alibaba.aliexpresshd",
            "com.einnovation.temu", "com.zzkko", "ir.divar", "com.taobao.taobao", "com.walmart.android",
            "com.etsy.android", "ir.basalam.app", "ir.torob", "com.snappfood", "ir.snapp.",
        ),
        AppCategory.PRODUCTIVITY to listOf(
            "com.google.android.gm", "com.microsoft.office.", "com.google.android.apps.docs", "com.microsoft.teams",
            "us.zoom.videomeetings", "com.Slack", "notion.id", "com.dropbox.android", "com.google.android.apps.meetings",
            "com.todoist", "com.evernote", "com.google.android.keep", "com.google.android.calendar",
            "com.microsoft.skydrive", "com.google.android.apps.tachyon", "com.trello", "com.anydo",
        ),
    )

    /** Curated category for [packageName], or null when the list does not know it. */
    fun curatedCategory(packageName: String): AppCategory? {
        for ((cat, names) in curated) {
            for (n in names) {
                if (if (n.endsWith('.')) packageName.startsWith(n) else packageName == n) return cat
            }
        }
        return null
    }

    /**
     * @param androidCategory [ApplicationInfo.category] (or [ApplicationInfo.CATEGORY_UNDEFINED]).
     * @param isSystem preinstalled with the system image.
     */
    fun classify(packageName: String, androidCategory: Int, isSystem: Boolean): AppCategory {
        curatedCategory(packageName)?.let { return it }
        if (isSystem) return AppCategory.SYSTEM
        return when (androidCategory) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.SYSTEM
            else -> AppCategory.OTHER
        }
    }

    fun of(info: ApplicationInfo): AppCategory = classify(
        info.packageName,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) info.category else ApplicationInfo.CATEGORY_UNDEFINED,
        info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
    )

    /** Installed packages in [category] (our own app excluded), for category limits. */
    fun packagesIn(context: Context, category: AppCategory): List<String> {
        val pm = context.packageManager
        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }
        return apps.filter { it.packageName != context.packageName && of(it) == category }.map { it.packageName }
    }
}

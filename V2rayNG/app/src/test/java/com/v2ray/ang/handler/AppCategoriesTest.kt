package com.v2ray.ang.handler

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppCategoriesTest {
    private val none = ApplicationInfo.CATEGORY_UNDEFINED

    @Test
    fun curatedListWins_evenForPreinstalledApps() {
        assertEquals(AppCategory.VIDEO, AppCategories.classify("com.google.android.youtube", none, isSystem = true))
        assertEquals(AppCategory.BROWSERS, AppCategories.classify("com.android.chrome", none, isSystem = true))
        assertEquals(AppCategory.MESSAGING, AppCategories.classify("org.telegram.messenger", ApplicationInfo.CATEGORY_SOCIAL, false))
    }

    @Test
    fun prefixesMatchWholeFamilies() {
        assertEquals(AppCategory.GAMES, AppCategories.classify("com.supercell.clashofclans", none, false))
        assertEquals(AppCategory.MESSAGING, AppCategories.classify("org.telegram.plus", none, false))
        assertEquals(AppCategory.OTHER, AppCategories.classify("com.supercellar.notes", none, false))
    }

    @Test
    fun androidCategoryUsedForUnknownApps() {
        assertEquals(AppCategory.GAMES, AppCategories.classify("x.y.game", ApplicationInfo.CATEGORY_GAME, false))
        assertEquals(AppCategory.VIDEO, AppCategories.classify("x.y.music", ApplicationInfo.CATEGORY_AUDIO, false))
        assertEquals(AppCategory.SOCIAL, AppCategories.classify("x.y.social", ApplicationInfo.CATEGORY_SOCIAL, false))
        assertEquals(AppCategory.OTHER, AppCategories.classify("x.y.z", none, false))
    }

    @Test
    fun unknownPreinstalledAppsAreSystem() {
        assertEquals(AppCategory.SYSTEM, AppCategories.classify("com.vendor.settingsextra", ApplicationInfo.CATEGORY_PRODUCTIVITY, true))
    }
}

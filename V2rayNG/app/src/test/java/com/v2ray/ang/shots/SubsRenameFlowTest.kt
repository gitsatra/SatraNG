package com.v2ray.ang.shots

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.ui.app.AppNav
import com.v2ray.ang.ui.compose.satra.LocalInlineSheets
import com.v2ray.ang.ui.compose.satra.SatraTheme
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.settings.SettingsDestination
import com.v2ray.ang.ui.subs.SubRow
import com.v2ray.ang.ui.subs.SubsActions
import com.v2ray.ang.ui.subs.SubsContent
import com.v2ray.ang.ui.subs.SubsMessage
import com.v2ray.ang.ui.subs.SubsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression: renaming a subscription from its edit sheet (any text) must save and re-render without crashing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SubsRenameFlowTest {

    private object NoNav : AppNav {
        override fun action(action: MainAction) = Unit
        override fun navigate(destination: MainDestination) = Unit
        override fun openSettingsPage(destination: SettingsDestination) = Unit
        override fun editSubscription(subId: String?) = Unit
        override fun scanQr(onResult: (String) -> Unit) = Unit
        override fun openUsageAccess() = Unit
        override fun enableAppEnforcement() = Unit
        override fun openSystemVpnSettings() = Unit
        override fun share(text: String) = Unit
        override fun copy(text: String) = Unit
    }

    /** Behaves like SubsViewModel.save + reload: the renamed row replaces the old one. */
    private class FakeSubs(initial: SubsUiState) : SubsActions {
        val state = MutableStateFlow(initial)
        val saved = mutableListOf<String>()
        override fun reload() = Unit
        override fun updateAll() = Unit
        override fun updateOne(guid: String) = Unit
        override fun setEnabled(guid: String, enabled: Boolean) = Unit
        override fun delete(guid: String) = Unit
        override fun save(guid: String?, name: String, url: String, autoUpdate: Boolean, intervalHours: Int, enabled: Boolean): SubsMessage? {
            val clean = name.trim()
            if (clean.isEmpty()) return SubsMessage.InvalidName
            saved += clean
            state.update { s ->
                s.copy(
                    rows = s.rows.map { if (it.guid == guid) it.copy(item = it.item.copy(remarks = clean)) else it },
                    message = SubsMessage.Saved,
                )
            }
            return null
        }
        override fun showQr(row: SubRow) = Unit
        override fun dismissQr() = Unit
        override fun messageShown() = state.update { it.copy(message = null) }
    }

    @get:Rule(order = 0)
    val registerActivity = org.junit.rules.TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
                org.robolectric.Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(
                    android.content.ComponentName(app.packageName, androidx.activity.ComponentActivity::class.java.name),
                )
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private fun initial() = SubsUiState(
        rows = listOf(
            SubRow("s1", SubscriptionItem(remarks = "پرها ft", url = "https://sub.example.com/abc", autoUpdate = true, updateInterval = 720,
                profileTitle = "Provider title", announce = "base64:", total = 100L shl 30), 27),
            SubRow("s2", SubscriptionItem(remarks = "import sub", url = "https://two.example.com/x", updateInterval = 30), 10),
        ),
    )

    private fun renameTo(fake: FakeSubs, from: String, to: String) {
        compose.onAllNodes(hasText(from)).onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction() and hasText(from)).onFirst().performTextReplacement(to)
        compose.onAllNodes(hasText("Save")).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun renameManyTimesWithAnyText() {
        val fake = FakeSubs(initial())
        val scenario = androidx.test.core.app.ActivityScenario.launch(androidx.activity.ComponentActivity::class.java)
        scenario.onActivity { act ->
            act.setContent {
                val s by fake.state.collectAsState()
                CompositionLocalProvider(LocalInlineSheets provides true) {
                    SatraTheme(dark = true) { SubsContent(s, fake, NoNav) }
                }
            }
        }
        compose.waitForIdle()
        val names = listOf(
            "parham",
            "پرها Xet",
            "🚀 Fast ✨",
            "a".repeat(300),
            "  spaced  ",
            "same",
        )
        var current = "پرها ft"
        for (n in names) {
            renameTo(fake, current, n)
            current = n.trim()
        }
        // Two subscriptions with the same name are allowed.
        renameTo(fake, "import sub", "same")
        assertEquals(names.map { it.trim() } + "same", fake.saved)
        assertEquals(listOf("same", "same"), fake.state.value.rows.map { it.item.remarks })
        scenario.close()
    }
}

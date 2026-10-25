package com.v2ray.ang.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.TranslatorsParser
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.satra.GroupHeader
import com.v2ray.ang.ui.compose.satra.LazyGroupRow
import com.v2ray.ang.ui.compose.satra.Satra
import com.v2ray.ang.ui.compose.satra.SatraIcons
import com.v2ray.ang.ui.compose.satra.SatraLazyPage
import com.v2ray.ang.ui.compose.satra.SatraRow
import com.v2ray.ang.util.Utils

class TranslatorsActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        TranslatorsScreen(onBackClick = { finish() })
    }
}

@Composable
fun TranslatorsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val credits = remember {
        runCatching { TranslatorsParser.parse(Utils.readTextFromAssets(context, "translators.json")) }.getOrDefault(emptyList())
    }
    SatraLazyPage(title = stringResource(R.string.title_translators), onBack = onBackClick) {
        credits.forEach { credit ->
            item(key = "h_" + credit.language) { GroupHeader(credit.language) }
            val people = credit.contributors
            items(people.size, key = { "c_${credit.language}_$it" }) { i ->
                val who = people[i]
                LazyGroupRow(i, people.size) {
                    val url = who.url
                    SatraRow(
                        title = who.displayName ?: who.name,
                        icon = if (url != null) SatraIcons.Link else SatraIcons.Heart,
                        iconColor = if (url != null) Satra.colors.accent else Satra.colors.danger,
                        chevron = url != null,
                        onClick = url?.let { { Utils.openUri(context, it) } },
                    )
                }
            }
        }
    }
}

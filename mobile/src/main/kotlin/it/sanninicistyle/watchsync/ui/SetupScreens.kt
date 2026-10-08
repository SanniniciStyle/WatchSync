package it.sanninicistyle.watchsync.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.sanninicistyle.watchsync.R
import it.sanninicistyle.watchsync.ui.theme.Ws
import java.util.Locale

/** One requirement of the setup, with its live state and the action that grants it. */
data class SetupItem(
    val title: Int,
    val description: Int,
    val done: Boolean,
    /** Label of the button: "Allow" for a permission, "Start" for a guided step. */
    val action: Int = R.string.grant,
    /** Help shown under the "i": what the step is for and how to fix it when it fails. */
    val info: Int? = null,
    val onGrant: () -> Unit,
)

@Composable
fun SetupScreen(items: List<SetupItem>, onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.setup_title), onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ws.Surface).padding(vertical = 6.dp),
        ) {
            items.forEachIndexed { index, item -> SetupRow(index + 1, item) }
        }
    }
}

@Composable
private fun SetupRow(number: Int, item: SetupItem) {
    val badge by animateColorAsState(if (item.done) Ws.Mint else Ws.Amber, spring(stiffness = Spring.StiffnessLow), label = "badge")
    val pop by animateFloatAsState(
        if (item.done) 1f else 0.92f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow), label = "pop",
    )
    var showInfo by rememberSaveable(item.title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(30.dp).scale(pop).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            if (item.done) Icon(WsIcons.Check, null, tint = Ws.Ground, modifier = Modifier.size(15.dp))
            else Text("$number", style = MaterialTheme.typography.labelLarge, color = Ws.OnAmber)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(item.title), style = MaterialTheme.typography.labelLarge, color = Ws.Text)
            Text(stringResource(item.description), style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint)
        }
        if (!item.done) {
            PressableSurface(onClick = item.onGrant, color = Ws.Amber, shape = RoundedCornerShape(20.dp)) {
                Text(
                    stringResource(item.action),
                    style = MaterialTheme.typography.labelLarge,
                    color = Ws.OnAmber,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        item.info?.let { InfoButton(expanded = showInfo, onToggle = { showInfo = !showInfo }) }
    }
    item.info?.let { InfoPanel(visible = showInfo, text = it, modifier = Modifier.padding(start = 44.dp)) }
    }
}

/** Languages the app is translated into; "" follows the phone. */
val AppLanguages = listOf("", "it", "en", "es", "fr", "de")

fun currentAppLanguage(context: Context): String =
    context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        .substringBefore(',').substringBefore('-')

fun setAppLanguage(context: Context, tag: String) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

@Composable
fun SettingsScreen(setupItems: List<SetupItem>, version: String, onRelearnRiposo: () -> Unit, onLicenses: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(currentAppLanguage(context)) }
    ScreenScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        SectionTitle(stringResource(R.string.language))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ws.Surface).padding(vertical = 6.dp)) {
            AppLanguages.forEach { tag ->
                val label = if (tag.isEmpty()) stringResource(R.string.language_system)
                else Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }
                LanguageRow(label = label, selected = selected == tag) {
                    selected = tag
                    setAppLanguage(context, tag)
                }
            }
        }
        SectionTitle(stringResource(R.string.permissions))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ws.Surface).padding(vertical = 6.dp)) {
            setupItems.forEachIndexed { index, item -> SetupRow(index + 1, item) }
        }
        // The phone's Riposo is recognised by its rules: after changing them, learn it again
        PressableSurface(onClick = onRelearnRiposo, color = Ws.Surface, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(WsIcons.Moon, null, tint = Ws.Moon, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.relearn_riposo), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.relearn_riposo_desc), style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint)
                }
            }
        }
        SectionTitle(stringResource(R.string.about))
        PressableSurface(onClick = onLicenses, color = Ws.Surface, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.licenses), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            )
        }
        Text(
            stringResource(R.string.version, version), style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) Ws.Amber else Ws.Outline, label = "ring")
    val dot by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "dot",
    )
    PressableSurface(onClick = onClick, color = Ws.Surface, shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Ws.Text)
            Box(Modifier.size(22.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Ws.Surface), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(10.dp).scale(dot).clip(CircleShape).background(Ws.Amber))
                }
            }
        }
    }
}

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, info: Int? = null, content: @Composable () -> Unit) {
    var showInfo by rememberSaveable(title) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ws.Ground)
            // The code keyboard must not cover the Connect button
            .imePadding()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Ws.Surface, contentColor = Ws.TextMuted),
                modifier = Modifier.size(44.dp),
            ) { Icon(WsIcons.Back, contentDescription = stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            info?.let { InfoButton(expanded = showInfo, onToggle = { showInfo = !showInfo }) }
        }
        info?.let { InfoPanel(visible = showInfo, text = it) }
        content()
    }
}

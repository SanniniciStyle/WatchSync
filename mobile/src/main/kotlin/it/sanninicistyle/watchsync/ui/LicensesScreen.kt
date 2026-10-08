package it.sanninicistyle.watchsync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sanninicistyle.watchsync.R
import it.sanninicistyle.watchsync.ui.theme.Ws

/** A component WatchSync ships, with the licence it is used under. */
private data class Component(val name: String, val owner: String, val license: String, val text: Int)

private val COMPONENTS = listOf(
    Component("AndroidX, Jetpack Compose, Material 3", "The Android Open Source Project", "Apache License 2.0", R.raw.license_apache2),
    Component("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains s.r.o.", "Apache License 2.0", R.raw.license_apache2),
    Component("Google Play services (Wearable)", "Google LLC", "Android Software Development Kit License", 0),
    Component("libadb-android", "Muntashir Al-Islam", "Apache License 2.0", R.raw.license_apache2),
    Component("spake2-java", "Muntashir Al-Islam", "GNU LGPL 3.0", R.raw.license_lgpl3),
    Component("Conscrypt", "The Android Open Source Project", "Apache License 2.0", R.raw.license_apache2),
    Component("Bouncy Castle", "The Legion of the Bouncy Castle Inc.", "MIT", R.raw.license_bouncycastle),
    Component("Sora (font)", "The Sora Project Authors", "SIL Open Font License 1.1", R.raw.license_ofl_sora),
    Component("Figtree (font)", "The Figtree Project Authors", "SIL Open Font License 1.1", R.raw.license_ofl_figtree),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.licenses), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            COMPONENTS.forEach { LicenseRow(it) }
            // spake2-java is LGPL: its source and the GPL it builds on must be reachable too
            Text(
                stringResource(R.string.licenses_lgpl_note), style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            LicenseRow(Component("GNU GPL 3.0", "Free Software Foundation", "", R.raw.license_gpl3))
        }
    }
}

@Composable
private fun LicenseRow(component: Component) {
    val context = LocalContext.current
    var open by rememberSaveable(component.name) { mutableStateOf(false) }
    val text = remember(component.text) {
        if (component.text == 0) null
        else context.resources.openRawResource(component.text).bufferedReader().use { it.readText() }
    }
    PressableSurface(
        onClick = { if (text != null) open = !open },
        color = Ws.Surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(component.name, style = MaterialTheme.typography.labelLarge)
            Text(
                listOf(component.owner, component.license).filter(String::isNotEmpty).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint,
            )
            AnimatedVisibility(
                visible = open && text != null,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(120)),
            ) {
                Text(
                    text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = Ws.TextMuted,
                    modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Ws.SurfaceHigh).padding(12.dp),
                )
            }
        }
    }
}

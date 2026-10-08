package it.sanninicistyle.watchsync.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import it.sanninicistyle.watchsync.R

/** "Notte & Ambra": night ink grounds, amber for DND and alarms, moon lavender for Riposo. */
object Ws {
    val Ground = Color(0xFF0B0F14)
    val Surface = Color(0xFF141A23)
    val SurfaceHigh = Color(0xFF1B222D)
    val Outline = Color(0xFF2A3442)
    val Text = Color(0xFFEEF1F6)
    val TextMuted = Color(0xFFB9C3D1)
    val TextFaint = Color(0xFF8693A5)
    val Amber = Color(0xFFFFB547)
    val OnAmber = Color(0xFF1A1205)
    val Moon = Color(0xFFA9B4FF)
    val OnMoon = Color(0xFF0E1230)
    val Mint = Color(0xFF7FE3C0)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    res, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sora = FontFamily(
    variable(R.font.sora, 400), variable(R.font.sora, 600),
    variable(R.font.sora, 700), variable(R.font.sora, 800),
)

val Figtree = FontFamily(
    variable(R.font.figtree, 400), variable(R.font.figtree, 500),
    variable(R.font.figtree, 600), variable(R.font.figtree, 700),
)

private val baseTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 60.sp, letterSpacing = (-0.04).em, lineHeight = 60.sp),
    displayMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, letterSpacing = (-0.035).em, lineHeight = 52.sp),
    headlineMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.025).em, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.01).em, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.04.em, lineHeight = 16.sp),
)

private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private val typography = with(baseTypography) {
    copy(
        displayLarge = displayLarge.copy(lineHeightStyle = centred),
        displayMedium = displayMedium.copy(lineHeightStyle = centred),
        headlineMedium = headlineMedium.copy(lineHeightStyle = centred),
        titleLarge = titleLarge.copy(lineHeightStyle = centred),
        titleMedium = titleMedium.copy(lineHeightStyle = centred),
        titleSmall = titleSmall.copy(lineHeightStyle = centred),
        bodyLarge = bodyLarge.copy(lineHeightStyle = centred),
        bodyMedium = bodyMedium.copy(lineHeightStyle = centred),
        labelLarge = labelLarge.copy(lineHeightStyle = centred),
        labelMedium = labelMedium.copy(lineHeightStyle = centred),
    )
}

private val colors = darkColorScheme(
    primary = Ws.Amber, onPrimary = Ws.OnAmber,
    secondary = Ws.Moon, onSecondary = Ws.OnMoon,
    tertiary = Ws.Mint,
    background = Ws.Ground, onBackground = Ws.Text,
    surface = Ws.Ground, onSurface = Ws.Text,
    surfaceContainer = Ws.Surface, surfaceContainerHigh = Ws.SurfaceHigh,
    onSurfaceVariant = Ws.TextMuted, outline = Ws.Outline,
)

@Composable
fun WatchSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Text without an explicit color reads as light on the night grounds
        CompositionLocalProvider(LocalContentColor provides Ws.Text, content = content)
    }
}

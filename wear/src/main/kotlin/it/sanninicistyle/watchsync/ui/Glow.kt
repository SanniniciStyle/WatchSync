package it.sanninicistyle.watchsync.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/**
 * A living halo, in the spirit of an assistant's glow: a ring of soft colour that keeps flowing
 * around its centre and breathes, instead of waves expanding from it. The ring sits on the edge
 * of the box; its glow spills a little outside.
 */
@Composable
fun GlowHalo(colors: List<Color>, modifier: Modifier = Modifier, thickness: Dp = 4.dp, glow: Dp = 12.dp) {
    val t = rememberInfiniteTransition(label = "halo")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "turn")
    val drift by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9_500, easing = LinearEasing)), label = "drift")
    val breathe by t.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val brush = Brush.sweepGradient(colors + colors.first())
    Box(modifier) {
        // The glow: wide, blurred, turning one way
        Canvas(Modifier.matchParentSize().blur(glow, BlurredEdgeTreatment.Unbounded)) {
            ring(brush, turn, (thickness * 3).toPx(), alpha = 0.75f * breathe)
        }
        // The line: thin and crisp, drifting the other way, so the colours seem to flow
        Canvas(Modifier.matchParentSize()) {
            ring(brush, -drift, thickness.toPx(), alpha = 0.55f + 0.35f * breathe)
        }
    }
}

private fun DrawScope.ring(brush: Brush, angle: Float, width: Float, alpha: Float) = rotate(angle) {
    drawCircle(brush, radius = (size.minDimension - width) / 2f, alpha = alpha, style = Stroke(width))
}

/** The app's halo colours, led by [accent]. */
fun haloColors(accent: Color) = listOf(accent, Ws.Moon, accent.copy(alpha = 0.25f), Ws.Mint, accent)

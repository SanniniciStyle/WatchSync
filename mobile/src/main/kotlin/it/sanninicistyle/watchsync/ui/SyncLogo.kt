package it.sanninicistyle.watchsync.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import it.sanninicistyle.watchsync.ui.theme.Ws

// The app icon's geometry (108-unit viewport, centre 54)
private val amberArc = PathParser().parsePathString("M28,39 A30,30 0 0,1 80,39 M80,39 l-1.2,-7.6 M80,39 l-7.6,1.6").toPath()
private val moonArc = PathParser().parsePathString("M80,69 A30,30 0 0,1 28,69 M28,69 l1.2,7.6 M28,69 l7.6,-1.6").toPath()

/**
 * The WatchSync icon, alive as in the design: the sync arrows keep orbiting the watch while it is
 * reachable (6 s turns, ease in-out). When the watch is out of reach they settle and turn grey.
 */
@Composable
fun SyncLogo(connected: Boolean, modifier: Modifier = Modifier) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(connected) {
        if (connected) {
            while (true) {
                turn.animateTo(turn.value + 360f, tween(6_000, easing = CubicBezierEasing(0.6f, 0f, 0.4f, 1f)))
                turn.snapTo(turn.value % 360f)
            }
        } else {
            // Finish the current turn gently instead of freezing mid-way
            turn.animateTo(if (turn.value % 360f < 1f) turn.value else turn.value - turn.value % 360f + 360f, spring(stiffness = Spring.StiffnessVeryLow))
        }
    }

    val amber by animateColorAsState(if (connected) Ws.Amber else Ws.TextFaint, spring(stiffness = Spring.StiffnessLow), label = "amber")
    val moon by animateColorAsState(if (connected) Ws.Moon else Ws.TextFaint, spring(stiffness = Spring.StiffnessLow), label = "moon")
    Canvas(modifier) {
        val k = size.minDimension / 72f // crop the adaptive-icon safe zone: units 18..90
        scale(k, pivot = Offset.Zero) {
            translate(-18f, -18f) {
                val arrows = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                rotate(turn.value, pivot = Offset(54f, 54f)) {
                    drawPath(amberArc, amber, style = arrows)
                    drawPath(moonArc, moon, style = arrows)
                }
                drawCircle(Ws.Surface, 17f, Offset(54f, 54f))
                drawCircle(Ws.Text, 17f, Offset(54f, 54f), style = Stroke(3.5f))
                drawLine(Ws.Amber, Offset(54f, 54f), Offset(54f, 44.5f), 3.5f, StrokeCap.Round)
                drawCircle(Ws.Text, 2.8f, Offset(54f, 54f))
            }
        }
    }
}

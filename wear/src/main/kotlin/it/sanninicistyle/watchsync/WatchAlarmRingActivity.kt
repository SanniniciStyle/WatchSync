package it.sanninicistyle.watchsync

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import it.sanninicistyle.watchsync.shared.DiagLog
import it.sanninicistyle.watchsync.shared.MirroredAlarm
import it.sanninicistyle.watchsync.ui.BodyStyle
import it.sanninicistyle.watchsync.ui.GlowHalo
import it.sanninicistyle.watchsync.ui.LabelStyle
import it.sanninicistyle.watchsync.ui.haloColors
import it.sanninicistyle.watchsync.ui.TimeStyle
import it.sanninicistyle.watchsync.ui.WatchIcons
import it.sanninicistyle.watchsync.ui.Ws

/** Full-screen alarm shown while an alarm of the phone rings on the watch. */
class WatchAlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // One tap must be enough: no ambient mode while the alarm rings
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val alarm by MirroredAlarm.current.collectAsStateWithLifecycle()
            LaunchedEffect(alarm) { if (alarm == null) finish() }
            alarm?.let { event ->
                RingScreen(
                    time = event.text,
                    label = event.title,
                    canSnooze = event.canSnooze,
                    onStop = {
                        DiagLog.d("AlarmRing", "Stop tapped")
                        WatchAlarmRingService.userStop(this@WatchAlarmRingActivity); finish()
                    },
                    onSnooze = { WatchAlarmRingService.userSnooze(this@WatchAlarmRingActivity); finish() },
                )
            }
        }
    }
}

@Composable
private fun RingScreen(time: String, label: String, canSnooze: Boolean, onStop: () -> Unit, onSnooze: () -> Unit) {
    // Sized from the screen so the buttons stay inside the round display
    BoxWithConstraints(Modifier.fillMaxSize().background(Ws.Ground), contentAlignment = Alignment.Center) {
        val k = (minOf(maxWidth, maxHeight).value / 213f).coerceIn(0.8f, 1.3f)
        Rings()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((2 * k).dp)) {
            Text(stringResource(R.string.alarm_from_phone).uppercase(), style = LabelStyle.copy(fontSize = (11 * k).sp), color = Ws.Amber)
            Text(time.ifBlank { label }, style = TimeStyle.copy(fontSize = (46 * k).sp), color = Ws.Text, maxLines = 1)
            if (time.isNotBlank() && label.isNotBlank()) {
                Text(
                    label, style = BodyStyle.copy(fontSize = (12 * k).sp), color = Ws.TextMuted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = (36 * k).dp),
                )
            }
            Row(Modifier.padding(top = (8 * k).dp), horizontalArrangement = Arrangement.spacedBy((8 * k).dp)) {
                if (canSnooze) {
                    val snooze = stringResource(R.string.alarm_snooze)
                    Pill(color = Ws.Chip, width = 54 * k, height = 46 * k, onClick = onSnooze, modifier = Modifier.semantics { contentDescription = snooze }) {
                        Icon(WatchIcons.Snooze, contentDescription = null, tint = Ws.Text, modifier = Modifier.size((22 * k).dp))
                    }
                }
                Pill(color = Ws.Amber, width = 82 * k, height = 46 * k, onClick = onStop) {
                    Text(stringResource(R.string.alarm_stop), style = LabelStyle.copy(fontSize = (15 * k).sp), color = Ws.OnAmber)
                }
            }
        }
    }
}

@Composable
private fun Pill(color: Color, width: Float, height: Float, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    Box(
        modifier.width(width.dp).height(height.dp).scale(scale).clip(RoundedCornerShape((height / 2).dp)).background(color)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The glowing halo along the edge of the screen while the alarm rings. */
@Composable
private fun Rings() {
    GlowHalo(haloColors(Ws.Amber), Modifier.fillMaxSize().padding(10.dp), thickness = 4.dp, glow = 16.dp, line = false)
}

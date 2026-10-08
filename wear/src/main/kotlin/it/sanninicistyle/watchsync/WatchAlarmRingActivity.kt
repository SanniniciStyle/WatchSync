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
import it.sanninicistyle.watchsync.ui.LabelStyle
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
    Box(Modifier.fillMaxSize().background(Ws.Ground), contentAlignment = Alignment.Center) {
        Rings()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.alarm_from_phone).uppercase(), style = LabelStyle, color = Ws.Amber)
            Text(time.ifBlank { label }, style = TimeStyle.copy(fontSize = 60.sp), color = Ws.Text, maxLines = 1)
            if (time.isNotBlank() && label.isNotBlank()) Text(label, style = BodyStyle, color = Ws.TextMuted, maxLines = 1)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canSnooze) {
                    val snooze = stringResource(R.string.alarm_snooze)
                    Pill(color = Ws.Chip, width = 76, onClick = onSnooze, modifier = Modifier.semantics { contentDescription = snooze }) {
                        Icon(WatchIcons.Snooze, contentDescription = null, tint = Ws.Text, modifier = Modifier.size(24.dp))
                    }
                }
                Pill(color = Ws.Amber, width = 104, onClick = onStop) {
                    Text(stringResource(R.string.alarm_stop), style = LabelStyle.copy(fontSize = 17.sp), color = Ws.OnAmber)
                }
            }
        }
    }
}

@Composable
private fun Pill(color: Color, width: Int, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    Box(
        modifier.width(width.dp).height(56.dp).scale(scale).clip(RoundedCornerShape(28.dp)).background(color)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Two amber rings breathing out from the centre, half a cycle apart. */
@Composable
private fun Rings() {
    val t = rememberInfiniteTransition(label = "rings")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearOutSlowInEasing), RepeatMode.Restart), label = "a")
    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
        listOf(a, (a + 0.5f) % 1f).forEach { p ->
            drawCircle(
                color = Ws.Amber.copy(alpha = 0.6f * (1f - p)),
                radius = size.minDimension / 2f * (0.7f + 0.3f * p),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

package it.sanninicistyle.watchsync

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.sanninicistyle.watchsync.shared.MirroredAlarm
import it.sanninicistyle.watchsync.shared.PeerInfo
import it.sanninicistyle.watchsync.ui.GlowHalo
import it.sanninicistyle.watchsync.ui.PressableSurface
import it.sanninicistyle.watchsync.ui.haloColors
import it.sanninicistyle.watchsync.ui.WsIcons
import it.sanninicistyle.watchsync.ui.theme.WatchSyncTheme
import it.sanninicistyle.watchsync.ui.theme.Ws

/** Full-screen alarm shown while an alarm of the watch rings on the phone. */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // One tap must be enough: no dimming while the alarm rings
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            WatchSyncTheme {
                val alarm by MirroredAlarm.current.collectAsStateWithLifecycle()
                val watch by PeerInfo.peer(this).collectAsStateWithLifecycle(initialValue = null)
                LaunchedEffect(alarm) { if (alarm == null) finish() }
                alarm?.let { event ->
                    RingScreen(
                        time = event.text,
                        label = event.title,
                        watchName = watch?.name.orEmpty(),
                        canSnooze = event.canSnooze,
                        onStop = { AlarmRingService.userStop(this); finish() },
                        onSnooze = { AlarmRingService.userSnooze(this); finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingScreen(
    time: String,
    label: String,
    watchName: String,
    canSnooze: Boolean,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Ws.Ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.alarm_from_watch).uppercase(), style = MaterialTheme.typography.labelMedium, color = Ws.Amber)
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Rings()
            Column(
                Modifier.size(204.dp).clip(CircleShape).background(Ws.Surface).border(4.dp, Ws.Outline, CircleShape),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(time.ifBlank { label }, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
                if (time.isNotBlank() && label.isNotBlank()) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = Ws.TextMuted, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        if (watchName.isNotBlank()) Text(watchName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        PressableSurface(onClick = onStop, color = Ws.Amber, shape = RoundedCornerShape(38.dp), modifier = Modifier.fillMaxWidth().height(76.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(WsIcons.Stop, null, tint = Ws.OnAmber, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.alarm_stop), style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.1f), color = Ws.OnAmber)
            }
        }
        if (canSnooze) {
            Spacer(Modifier.height(14.dp))
            PressableSurface(onClick = onSnooze, color = Ws.SurfaceHigh, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(WsIcons.Snooze, null, tint = Ws.Text, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.alarm_snooze), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** The glowing halo around the watch while its alarm rings. */
@Composable
private fun Rings() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlowHalo(haloColors(Ws.Amber), Modifier.size(222.dp), thickness = 4.dp, glow = 16.dp)
    }
}

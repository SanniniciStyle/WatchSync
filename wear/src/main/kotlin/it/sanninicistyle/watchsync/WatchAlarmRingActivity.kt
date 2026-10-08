package it.sanninicistyle.watchsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import it.sanninicistyle.watchsync.shared.DiagLog
import it.sanninicistyle.watchsync.shared.MirroredAlarm

/** Full-screen alarm shown while an alarm of the phone rings on the watch. */
class WatchAlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // One tap must be enough: no dimming or ambient mode while the alarm rings
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                val alarm by MirroredAlarm.current.collectAsState()
                LaunchedEffect(alarm) { if (alarm == null) finish() }
                alarm?.let { event ->
                    AppScaffold {
                        ScreenScaffold {
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
        }
    }
}

@Composable
private fun RingScreen(
    time: String,
    label: String,
    canSnooze: Boolean,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.alarm_from_phone),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            time.ifBlank { label },
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        if (time.isNotBlank() && label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            if (canSnooze) {
                FilledTonalButton(onClick = onSnooze) { Text(stringResource(R.string.alarm_snooze)) }
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors()) {
                Text(stringResource(R.string.alarm_stop))
            }
        }
    }
}

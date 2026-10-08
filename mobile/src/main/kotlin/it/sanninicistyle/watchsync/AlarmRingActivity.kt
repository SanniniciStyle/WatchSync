package it.sanninicistyle.watchsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.sanninicistyle.watchsync.shared.MirroredAlarm

/** Full-screen alarm shown while an alarm of the watch rings on the phone. */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // One tap must be enough: no dimming or ambient mode while the alarm rings
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val alarm by MirroredAlarm.current.collectAsState()
                LaunchedEffect(alarm) { if (alarm == null) finish() }
                alarm?.let { event ->
                    RingScreen(
                        title = event.title.ifBlank { stringResource(R.string.alarm_from_watch) },
                        text = event.text,
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
    title: String,
    text: String,
    canSnooze: Boolean,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.alarm_from_watch),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(text.ifBlank { title }, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
            if (text.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(64.dp))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Text(stringResource(R.string.alarm_stop), style = MaterialTheme.typography.titleLarge)
            }
            if (canSnooze) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text(stringResource(R.string.alarm_snooze), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

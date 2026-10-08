package it.sanninicistyle.watchsync.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.sanninicistyle.watchsync.R
import it.sanninicistyle.watchsync.RiposoMode
import it.sanninicistyle.watchsync.SystemRiposo
import it.sanninicistyle.watchsync.ui.theme.Ws
import kotlinx.coroutines.delay

/** One-time: the user turns on the phone's own Riposo and WatchSync learns to recognise it. */
@Composable
fun LearnRiposoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var learnt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val system = SystemRiposo(context)
        val own = RiposoMode(context)
        // WatchSync's own Riposo must not be learnt as the phone's
        while (!learnt) {
            if (!own.isActive && system.learnCurrent()) learnt = true else delay(700)
        }
    }
    ScreenScaffold(title = stringResource(R.string.learn_riposo_title), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ws.Surface).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val pop by animateFloatAsState(
                    if (learnt) 1f else 0.9f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow), label = "pop",
                )
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    if (!learnt) Pulse(color = Ws.Moon, diameter = 52.dp)
                    Box(
                        Modifier.size(44.dp).scale(pop).clip(CircleShape).background(if (learnt) Ws.Mint else Ws.SurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (learnt) WsIcons.Check else WsIcons.Moon, null,
                            tint = if (learnt) Ws.Ground else Ws.Moon, modifier = Modifier.size(22.dp),
                        )
                    }
                }
                AnimatedContent(targetState = learnt, transitionSpec = { slideUp() }, label = "learn") { done ->
                    Column {
                        Text(
                            stringResource(if (done) R.string.learn_riposo_done else R.string.learn_riposo_step),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(if (done) R.string.learn_riposo_done_hint else R.string.learn_riposo_hint),
                            style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint,
                        )
                    }
                }
            }
            if (learnt) {
                PressableSurface(onClick = onBack, color = Ws.Amber, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium, color = Ws.OnAmber)
                }
            }
        }
    }
}

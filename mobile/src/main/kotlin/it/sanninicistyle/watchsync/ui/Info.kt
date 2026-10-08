package it.sanninicistyle.watchsync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import it.sanninicistyle.watchsync.R
import it.sanninicistyle.watchsync.ui.theme.Ws

/** The small "i" that opens the help of a step or a section. */
@Composable
fun InfoButton(expanded: Boolean, onToggle: () -> Unit) {
    val bg by animateColorAsState(if (expanded) Ws.Moon else Ws.SurfaceHigh, spring(stiffness = Spring.StiffnessMediumLow), label = "infoBg")
    val fg by animateColorAsState(if (expanded) Ws.OnMoon else Ws.TextMuted, spring(stiffness = Spring.StiffnessMediumLow), label = "infoFg")
    val label = stringResource(R.string.info)
    val state = stringResource(if (expanded) R.string.info_open else R.string.info_closed)
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, ripple(bounded = false), role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = label; stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        Icon(WsIcons.Info, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
    }
}

/**
 * The help itself: a first line, then one tip per line. Lines starting with "• " become bullets.
 */
@Composable
fun InfoPanel(visible: Boolean, text: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220)),
        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(20.dp)).background(Ws.SurfaceHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stringResource(text).split('\n').filter(String::isNotBlank).forEach { line ->
                if (line.startsWith("• ")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(Ws.Moon))
                        Text(line.removePrefix("• "), style = MaterialTheme.typography.bodyMedium, color = Ws.TextMuted)
                    }
                } else {
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = Ws.Text)
                }
            }
        }
    }
}

package it.sanninicistyle.watchsync.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sanninicistyle.watchsync.R
import it.sanninicistyle.watchsync.shared.NextAlarm
import it.sanninicistyle.watchsync.shared.Peer
import it.sanninicistyle.watchsync.ui.theme.Ws
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    watch: Peer?,
    modes: ModesUi,
    phoneAlarm: NextAlarm,
    watchAlarm: NextAlarm,
    permissions: Permissions,
    onToggle: (Mode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ws.Ground)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TopBar(mode = modes.mode, connected = watch?.connected == true && permissions.watchReady, onOpenSettings = onOpenSettings)
        if (!permissions.allGranted) SetupBanner(permissions, onOpenSetup)
        WatchCard(watch = watch, watchAlarm = watchAlarm, ready = permissions.watchReady)
        ModesSection(modes = modes, onToggle = onToggle)
        AlarmsSection(phoneAlarm = phoneAlarm, watchAlarm = watchAlarm)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopBar(mode: Mode, connected: Boolean, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SyncLogo(connected = connected, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        IconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Ws.Surface, contentColor = Ws.TextMuted),
            modifier = Modifier.size(44.dp),
        ) { Icon(WsIcons.Settings, contentDescription = stringResource(R.string.settings)) }
    }
}

@Composable
private fun SetupBanner(permissions: Permissions, onOpenSetup: () -> Unit) {
    val missing = permissions.missing
    PressableSurface(
        onClick = onOpenSetup,
        color = Ws.Amber,
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(R.string.setup_needed), style = MaterialTheme.typography.titleMedium, color = Ws.OnAmber)
                Text(stringResource(R.string.setup_missing, missing), style = MaterialTheme.typography.bodyMedium, color = Ws.OnAmber)
            }
            Icon(WsIcons.ArrowForward, contentDescription = null, tint = Ws.OnAmber)
        }
    }
}

/**
 * The watch as it really is: "connected" only when it is in reach AND set up; in reach but not
 * set up yet says so, since nothing would sync.
 */
@Composable
private fun WatchCard(watch: Peer?, watchAlarm: NextAlarm, ready: Boolean) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Ws.Surface).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            if (watch?.connected == true) Pulse(color = if (ready) Ws.Mint else Ws.Amber)
            Box(
                Modifier.size(92.dp).clip(CircleShape).background(Color(0xFF1E2632)).border(3.dp, Color(0xFF2C3644), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = if (watchAlarm.exists) formatTime(context, watchAlarm.triggerAt) else null,
                    transitionSpec = { slideUp() }, label = "watchTime",
                ) { time ->
                    if (time != null) {
                        Text(time, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
                    } else {
                        Icon(WsIcons.Watch, contentDescription = null, tint = Ws.TextMuted, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AnimatedContent(targetState = watch?.name, transitionSpec = { slideUp() }, label = "watchName") { name ->
                Text(name ?: stringResource(R.string.watch_none), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp))
            }
            val status = when {
                watch == null -> stringResource(R.string.watch_none_hint)
                !watch.connected -> stringResource(R.string.watch_disconnected)
                !ready -> stringResource(R.string.watch_needs_setup)
                else -> stringResource(R.string.watch_connected)
            }
            val statusColor by animateColorAsState(
                when { watch?.connected != true -> Ws.TextFaint; !ready -> Ws.Amber; else -> Ws.Mint }, label = "status",
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (watch != null) Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = Ws.TextMuted)
            }
        }
    }
}

@Composable
private fun ModesSection(modes: ModesUi, onToggle: (Mode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.modes))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeTile(
                title = stringResource(R.string.mode_dnd), icon = WsIcons.Dnd,
                on = modes.mode == Mode.DND, accent = Ws.Amber, onAccent = Ws.OnAmber,
                enabled = modes.canControl, onClick = { onToggle(Mode.DND) },
                modifier = Modifier.weight(1f),
            )
            ModeTile(
                title = stringResource(R.string.mode_riposo), icon = WsIcons.Moon,
                on = modes.mode == Mode.RIPOSO, accent = Ws.Moon, onAccent = Ws.OnMoon,
                enabled = modes.canControl, onClick = { onToggle(Mode.RIPOSO) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    on: Boolean,
    accent: Color,
    onAccent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bouncy = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val container by animateColorAsState(if (on) accent else Ws.Surface, bouncy, label = "tile")
    val content by animateColorAsState(if (on) onAccent else Ws.Text, bouncy, label = "tileText")
    val track by animateColorAsState(if (on) Color(0x47070A0D) else Ws.Outline, bouncy, label = "track")
    val knob by animateColorAsState(if (on) Ws.Ground else Ws.TextFaint, bouncy, label = "knob")
    val knobX by animateDpAsState(
        if (on) 18.dp else 0.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "knobX",
    )
    val iconTurn by animateFloatAsState(
        if (on) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "icon",
    )
    val stateText = stringResource(if (on) R.string.mode_on else R.string.mode_off)
    PressableSurface(
        onClick = onClick,
        enabled = enabled,
        color = container,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
            .height(176.dp)
            .semantics { role = Role.Switch; stateDescription = stateText },
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(
                    icon, contentDescription = null, tint = content,
                    modifier = Modifier.size(30.dp).graphicsLayer {
                        val s = 0.92f + 0.12f * iconTurn
                        scaleX = s; scaleY = s
                    },
                )
                Box(Modifier.width(44.dp).height(26.dp).clip(RoundedCornerShape(13.dp)).background(track).padding(3.dp)) {
                    Box(Modifier.offset(x = knobX).size(20.dp).clip(CircleShape).background(knob))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = content)
                AnimatedContent(targetState = stateText, transitionSpec = { slideUp() }, label = "state") {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun AlarmsSection(phoneAlarm: NextAlarm, watchAlarm: NextAlarm) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.alarms))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Ws.Surface).padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            AnimatedContent(targetState = phoneAlarm, transitionSpec = { slideUp() }, label = "phoneAlarm") { alarm ->
                if (alarm.exists) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                stringResource(R.string.next_alarm, relativeDay(context, alarm.triggerAt)).uppercase(),
                                style = MaterialTheme.typography.labelMedium, color = Ws.Amber,
                            )
                            Text(formatTime(context, alarm.triggerAt), style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp))
                        }
                        if (alarm.appLabel.isNotBlank()) {
                            Text(alarm.appLabel, style = MaterialTheme.typography.bodyMedium, color = Ws.TextFaint, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                } else {
                    Text(stringResource(R.string.no_alarm), style = MaterialTheme.typography.titleMedium, color = Ws.TextMuted)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.5.dp, Ws.Outline, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.watch_alarms), style = MaterialTheme.typography.bodyMedium, color = Ws.TextMuted)
            AnimatedContent(targetState = watchAlarm, transitionSpec = { slideUp() }, label = "watchAlarm") { alarm ->
                Text(
                    if (alarm.exists) "${relativeDay(context, alarm.triggerAt)} · ${formatTime(context, alarm.triggerAt)}"
                    else stringResource(R.string.no_alarm),
                    style = MaterialTheme.typography.labelLarge, color = Ws.Text,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Color(0xFFC9D0DB), modifier = Modifier.padding(start = 4.dp))
}

/** A clickable surface that squashes a little while pressed, with a springy release. */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(color)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun Pulse(color: Color, diameter: androidx.compose.ui.unit.Dp = 92.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "p")
    Canvas(Modifier.size(diameter)) {
        drawCircle(color.copy(alpha = 0.5f * (1f - p)), radius = size.minDimension / 2f * (0.45f + 0.75f * p))
    }
}

internal fun slideUp() =
    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(220))) togetherWith
        (slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut(tween(160)))

fun formatTime(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

fun relativeDay(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    return when (day.toEpochDay() - today.toEpochDay()) {
        0L -> context.getString(R.string.today)
        1L -> context.getString(R.string.tomorrow)
        in 2L..6L -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

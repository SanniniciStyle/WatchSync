package it.sanninicistyle.watchsync.ui

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import it.sanninicistyle.watchsync.R
import it.sanninicistyle.watchsync.WatchModes
import it.sanninicistyle.watchsync.WatchSetup
import it.sanninicistyle.watchsync.shared.NextAlarm
import it.sanninicistyle.watchsync.shared.Peer
import it.sanninicistyle.watchsync.shared.PeerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayStyle
import java.util.Date

object Ws {
    val Ground = Color(0xFF000000)
    val Chip = Color(0xFF1B222D)
    val Text = Color(0xFFEEF1F6)
    val TextMuted = Color(0xFFB9C3D1)
    val TextFaint = Color(0xFF9AA6B6)
    val Amber = Color(0xFFFFB547)
    val OnAmber = Color(0xFF1A1205)
    val Moon = Color(0xFFA9B4FF)
    val OnMoon = Color(0xFF0E1230)
    val Mint = Color(0xFF7FE3C0)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Sora = FontFamily(variable(R.font.sora, 600), variable(R.font.sora, 700), variable(R.font.sora, 800))
val Figtree = FontFamily(variable(R.font.figtree, 400), variable(R.font.figtree, 600))

val TimeStyle = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 50.sp, letterSpacing = (-0.04).em)
val BodyStyle = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 14.sp)
val LabelStyle = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

enum class Mode { NONE, DND, RIPOSO }

class WatchHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val modes = WatchModes(app)
    private val _mode = MutableStateFlow(Mode.NONE)
    val mode: StateFlow<Mode> = _mode.asStateFlow()
    private val _nextAlarm = MutableStateFlow(NextAlarm())
    val nextAlarm: StateFlow<NextAlarm> = _nextAlarm.asStateFlow()
    private val _ready = MutableStateFlow(WatchSetup.read(app).ready)

    /** Every grant the watch needs is in place (set up from the phone app). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    val phone: StateFlow<Peer?> = PeerInfo.peer(app).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }
    private val bedtimeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        app.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        app.contentResolver.registerContentObserver(Settings.Global.getUriFor(WatchModes.BEDTIME_SETTING), false, bedtimeObserver)
        refresh()
        // Grants arrive from the phone with no broadcast: look again until the setup is complete
        viewModelScope.launch {
            while (!_ready.value) {
                delay(2_000)
                _ready.value = WatchSetup.read(getApplication()).ready
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val bedtime = modes.readBedtime()
            _mode.value = when { bedtime -> Mode.RIPOSO; modes.readAnyDnd() -> Mode.DND; else -> Mode.NONE }
            _nextAlarm.value = NextAlarm.read(getApplication())
            _ready.value = WatchSetup.read(getApplication()).ready
        }
    }

    /** DND and Riposo are exclusive: turning one on turns the other off. */
    fun toggle(target: Mode) {
        val next = if (_mode.value == target) Mode.NONE else target
        _mode.value = next
        viewModelScope.launch(Dispatchers.Default) {
            modes.apply(dnd = next == Mode.DND, bedtime = next == Mode.RIPOSO)
            refresh()
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(receiver)
        getApplication<Application>().contentResolver.unregisterContentObserver(bedtimeObserver)
    }
}

@Composable
fun WatchHome(phone: Peer?, ready: Boolean, mode: Mode, nextAlarm: NextAlarm, onToggle: (Mode) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Ws.Ground).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val dot by animateColorAsState(
            when { phone?.connected != true -> Ws.TextFaint; !ready -> Ws.Amber; else -> Ws.Mint }, label = "dot",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (phone != null) Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            AnimatedContent(targetState = phone?.name, transitionSpec = { slideUp() }, label = "phone") { name ->
                Text(name ?: stringResource(R.string.phone_none), style = BodyStyle, color = Ws.TextMuted, maxLines = 1)
            }
        }
        AnimatedContent(targetState = ready, transitionSpec = { slideUp() }, label = "ready") { isReady ->
            if (!isReady) Text(stringResource(R.string.needs_setup), style = LabelStyle, color = Ws.Amber, maxLines = 2, textAlign = TextAlign.Center)
        }
        AnimatedContent(targetState = nextAlarm, transitionSpec = { slideUp() }, label = "alarm") { alarm ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (alarm.exists) {
                    Text(DateFormat.getTimeFormat(context).format(Date(alarm.triggerAt)), style = TimeStyle, color = Ws.Text)
                    Text(relativeDay(context, alarm.triggerAt), style = BodyStyle, color = Ws.TextFaint)
                } else {
                    Text(stringResource(R.string.no_alarm), style = BodyStyle, color = Ws.TextFaint)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            ModeChip(stringResource(R.string.mode_dnd_short), WatchIcons.Dnd, mode == Mode.DND, Ws.Amber, Ws.OnAmber) { onToggle(Mode.DND) }
            ModeChip(stringResource(R.string.mode_riposo), WatchIcons.Moon, mode == Mode.RIPOSO, Ws.Moon, Ws.OnMoon) { onToggle(Mode.RIPOSO) }
        }
    }
}

@Composable
private fun ModeChip(label: String, icon: ImageVector, on: Boolean, accent: Color, onAccent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val bg by animateColorAsState(if (on) accent else Ws.Chip, spring(stiffness = Spring.StiffnessMediumLow), label = "bg")
    val fg by animateColorAsState(if (on) onAccent else Ws.Text, spring(stiffness = Spring.StiffnessMediumLow), label = "fg")
    val iconScale by animateFloatAsState(if (on) 1.1f else 1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow), label = "icon")
    val state = stringResource(if (on) R.string.mode_on else R.string.mode_off)
    Column(
        Modifier
            .size(88.dp)
            .scale(scale)
            .clip(RoundedCornerShape(32.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { role = Role.Switch; stateDescription = state },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp).scale(iconScale))
        Text(label, style = LabelStyle, color = fg, maxLines = 1)
    }
}

object WatchIcons {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

    val Dnd = icon("Dnd", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M7.5 12h9")
    val Moon = icon("Moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Snooze = icon("Snooze", "M20 13a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 9v4l2.5 2.5M9 2h6")
}

fun slideUp() =
    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(220))) togetherWith
        (slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 2 } + fadeOut(tween(160)))

fun relativeDay(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val locale = context.resources.configuration.locales[0]
    return when (day.toEpochDay() - LocalDate.now(zone).toEpochDay()) {
        0L -> context.getString(R.string.today)
        1L -> context.getString(R.string.tomorrow)
        else -> day.dayOfWeek.getDisplayName(DayStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    }
}

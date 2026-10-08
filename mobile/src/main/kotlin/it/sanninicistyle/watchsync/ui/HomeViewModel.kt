package it.sanninicistyle.watchsync.ui

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.sanninicistyle.watchsync.PhoneSyncComponents
import it.sanninicistyle.watchsync.shared.InfoPaths
import it.sanninicistyle.watchsync.shared.NextAlarm
import it.sanninicistyle.watchsync.shared.Peer
import it.sanninicistyle.watchsync.shared.PeerInfo
import it.sanninicistyle.watchsync.shared.PeerMessenger
import it.sanninicistyle.watchsync.shared.StatusPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which of the two exclusive modes is on. */
enum class Mode { NONE, DND, RIPOSO }

data class ModesUi(val mode: Mode = Mode.NONE, val canControl: Boolean = false)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val modes = PhoneSyncComponents.modes(app)

    private val _modes = MutableStateFlow(ModesUi())
    val modesUi: StateFlow<ModesUi> = _modes.asStateFlow()

    private val _phoneAlarm = MutableStateFlow(NextAlarm())
    val phoneAlarm: StateFlow<NextAlarm> = _phoneAlarm.asStateFlow()

    val watchAlarm: StateFlow<NextAlarm> = PeerInfo.nextAlarm

    val watch: StateFlow<Peer?> = PeerInfo.peer(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _permissions = MutableStateFlow(Permissions.read(app))
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        PeerInfo.load(app)
        val filter = IntentFilter().apply {
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        refresh()
        // Ask the watch for its next alarm so the screen never shows stale data
        viewModelScope.launch(Dispatchers.IO) {
            PeerMessenger(app).send(InfoPaths.REQUEST)
            PeerMessenger(app).send(StatusPaths.REQUEST)
        }
        // The watch's setup state arrives over the Data Layer: re-read when it changes
        viewModelScope.launch { PeerInfo.watchStatus.collect { refresh() } }
    }

    /** Re-reads everything from the system (also called when the screen resumes). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val riposo = modes.readBedtime()
            val dnd = !riposo && modes.readAnyDnd()
            val perms = Permissions.read(ctx)
            _permissions.value = perms
            _modes.value = ModesUi(
                mode = when { riposo -> Mode.RIPOSO; dnd -> Mode.DND; else -> Mode.NONE },
                canControl = perms.dndAccess,
            )
            _phoneAlarm.value = NextAlarm.read(ctx)
        }
    }

    /** DND and Riposo are exclusive: turning one on turns the other off. */
    fun toggle(target: Mode) {
        val current = _modes.value.mode
        val next = if (current == target) Mode.NONE else target
        _modes.value = _modes.value.copy(mode = next) // instant feedback, confirmed by refresh()
        viewModelScope.launch(Dispatchers.Default) {
            modes.apply(dnd = next == Mode.DND, bedtime = next == Mode.RIPOSO)
            refresh()
        }
    }

    override fun onCleared() {
        ctx.unregisterReceiver(receiver)
    }
}

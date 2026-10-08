package it.sanninicistyle.watchsync.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The peer's alarm currently ringing on this device, observed by the ringing screen. */
object MirroredAlarm {
    private val _current = MutableStateFlow<AlarmEvent?>(null)
    val current: StateFlow<AlarmEvent?> = _current.asStateFlow()

    fun set(event: AlarmEvent?) {
        _current.value = event
    }
}

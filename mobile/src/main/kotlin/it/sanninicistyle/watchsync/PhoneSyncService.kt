package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.sanninicistyle.watchsync.shared.AlarmCommand
import it.sanninicistyle.watchsync.shared.AlarmEvent
import it.sanninicistyle.watchsync.shared.AlarmPaths
import it.sanninicistyle.watchsync.shared.ModeState
import it.sanninicistyle.watchsync.shared.SyncPaths
import kotlinx.coroutines.runBlocking

/** Receives the watch's modes and alarms. onMessageReceived already runs on a worker thread. */
class PhoneSyncService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        DiagLog.d(TAG, "from watch: ${event.path} (${event.data.size} bytes)")
        when (event.path) {
            SyncPaths.WATCH_STATE -> runBlocking {
                val state = runCatching { ModeState.decode(event.data) }.getOrNull()
                if (state == null) DiagLog.w(TAG, "bad state payload")
                else PhoneSyncComponents.modeSync(this@PhoneSyncService).onRemoteState(state)
            }
            SyncPaths.STATE_REQUEST -> runBlocking {
                PhoneSyncComponents.modeSync(this@PhoneSyncService).pushCurrent()
            }
            // An alarm of the watch started or stopped ringing
            AlarmPaths.EVENT -> runCatching { AlarmEvent.decode(event.data) }.getOrNull()?.let {
                if (it.ringing) AlarmRingService.ring(this, it) else AlarmRingService.remoteStopped(this)
            }
            // The user stopped or snoozed this phone's alarm from the watch
            AlarmPaths.COMMAND -> runCatching { AlarmCommand.decode(event.data) }.getOrNull()?.let {
                PhoneSyncComponents.alarms.execute(it)
            }
            else -> super.onMessageReceived(event)
        }
    }

    private companion object {
        const val TAG = "PhoneSyncService"
    }
}

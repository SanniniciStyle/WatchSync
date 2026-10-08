package it.sanninicistyle.watchsync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.sanninicistyle.watchsync.shared.ModeState
import it.sanninicistyle.watchsync.shared.SyncPaths
import kotlinx.coroutines.runBlocking

/** Receives the watch's state. onMessageReceived already runs on a worker thread. */
class PhoneSyncService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val sync = PhoneSyncComponents.modeSync(this)
        when (event.path) {
            SyncPaths.WATCH_STATE -> runBlocking {
                val state = runCatching { ModeState.decode(event.data) }.getOrNull()
                if (state == null) Log.w(TAG, "bad state payload") else sync.onRemoteState(state)
            }
            SyncPaths.STATE_REQUEST -> runBlocking { sync.pushCurrent() }
            else -> super.onMessageReceived(event)
        }
    }

    private companion object {
        const val TAG = "PhoneSyncService"
    }
}

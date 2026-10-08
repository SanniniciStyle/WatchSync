package it.sanninicistyle.watchsync

import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Long-lived listener that notices DND and Riposo changes on the phone and reports them.
 * It does not read notifications: the binding just keeps it alive and delivers DND changes.
 */
class PhoneModeListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    override fun onListenerConnected() {
        Log.d(TAG, "connected")
        scope.launch { PhoneSyncComponents.modes(this@PhoneModeListenerService).riposo.ensure() }
        scheduleReport()
    }

    // Turning Riposo on or off always changes the interruption filter too
    override fun onInterruptionFilterChanged(interruptionFilter: Int) = scheduleReport()

    private fun scheduleReport() {
        pending?.cancel()
        pending = scope.launch {
            delay(SETTLE_MS)
            PhoneSyncComponents.modeSync(this@PhoneModeListenerService).onLocalChanged()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PhoneModeListener"
        const val SETTLE_MS = 1_000L
    }
}

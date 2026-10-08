package it.sanninicistyle.watchsync

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
 * Long-lived listener that notices DND and Bedtime changes on the watch and reports them.
 * The notification-listener binding keeps it running; it does not read any notification.
 */
class WatchModeListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    private val bedtimeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReport()
    }

    override fun onListenerConnected() {
        Log.d(TAG, "connected")
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(WatchModes.BEDTIME_SETTING), false, bedtimeObserver
        )
        scheduleReport()
    }

    override fun onListenerDisconnected() {
        contentResolver.unregisterContentObserver(bedtimeObserver)
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) = scheduleReport()

    /** Bedtime and DND change a few hundred ms apart: report once both have settled. */
    private fun scheduleReport() {
        pending?.cancel()
        pending = scope.launch {
            delay(SETTLE_MS)
            WatchSyncComponents.modeSync(this@WatchModeListenerService).onLocalChanged()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "WatchModeListener"
        const val SETTLE_MS = 1_200L
    }
}

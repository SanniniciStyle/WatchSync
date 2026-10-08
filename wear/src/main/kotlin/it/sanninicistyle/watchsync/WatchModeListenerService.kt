package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import it.sanninicistyle.watchsync.shared.AlarmEvent
import it.sanninicistyle.watchsync.shared.AlarmPaths
import it.sanninicistyle.watchsync.shared.PeerMessenger
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
        DiagLog.d(TAG, "connected")
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(WatchModes.BEDTIME_SETTING), false, bedtimeObserver
        )
        scheduleReport()
        WatchClockAlarms.scheduleNextCheck(this)
    }

    override fun onListenerDisconnected() {
        contentResolver.unregisterContentObserver(bedtimeObserver)
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) = scheduleReport()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        WatchSyncComponents.alarms.onPosted(sbn)?.let(::sendAlarm)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        WatchSyncComponents.alarms.onRemoved(sbn)?.let(::sendAlarm)
    }

    private fun sendAlarm(event: AlarmEvent) {
        scope.launch {
            PeerMessenger(this@WatchModeListenerService).send(AlarmPaths.EVENT, event.encode())
        }
    }

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

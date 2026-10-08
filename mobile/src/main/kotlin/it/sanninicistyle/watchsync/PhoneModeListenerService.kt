package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
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
 * Long-lived listener that notices DND and Riposo changes on the phone and reports them, and
 * spots alarms of clock apps while they ring so the watch can ring too. Other notifications are
 * ignored and never leave the phone.
 */
class PhoneModeListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    override fun onListenerConnected() {
        DiagLog.d(TAG, "connected")
        scope.launch { PhoneSyncComponents.modes(this@PhoneModeListenerService).riposo.cleanUp() }
        scheduleReport()
    }

    // Turning Riposo on or off always changes the interruption filter too
    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        DiagLog.d(TAG, "filter=$interruptionFilter policy=${getSystemService(android.app.NotificationManager::class.java).consolidatedNotificationPolicy}")
        scheduleReport()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != packageName && (sbn.isOngoing || sbn.packageName in SYSTEM_PKGS)) {
            DiagLog.d(TAG, "posted ${sbn.packageName} tag=${sbn.tag} id=${sbn.id} ch=${sbn.notification.channelId} " +
                "cat=${sbn.notification.category} title=${sbn.notification.extras.getCharSequence("android.title")}")
        }
        PhoneSyncComponents.alarms.onPosted(sbn)?.let(::sendAlarm)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        PhoneSyncComponents.alarms.onRemoved(sbn)?.let(::sendAlarm)
    }

    private fun sendAlarm(event: AlarmEvent) {
        scope.launch {
            PeerMessenger(this@PhoneModeListenerService).send(AlarmPaths.EVENT, event.encode())
        }
    }

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
        // Long enough for a mode switch to settle (e.g. Riposo applying its rules), short enough to feel instant
        const val SETTLE_MS = 400L
        val SYSTEM_PKGS = setOf("android", "com.android.systemui", "com.google.android.apps.wellbeing")
    }
}

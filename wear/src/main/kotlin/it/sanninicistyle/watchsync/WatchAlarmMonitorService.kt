package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import it.sanninicistyle.watchsync.shared.AlarmAction
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
import java.text.DateFormat
import java.util.Date

/**
 * Follows an alarm of the watch's clock app while it rings: tells the phone when it starts and
 * when it stops, and stops/snoozes it when asked from the phone. Runs only during the alarm.
 */
class WatchAlarmMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, quietNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        when (intent?.action) {
            ACTION_COMMAND -> {
                val action = runCatching { AlarmAction.valueOf(intent.getStringExtra(EXTRA_ACTION)!!) }.getOrNull()
                val clock = ringing?.first
                if (action != null && clock != null) WatchClockAlarms.execute(this, clock, action)
            }
            else -> if (job?.isActive != true) job = scope.launch { follow() }
        }
        return START_NOT_STICKY
    }

    private suspend fun follow() {
        val messenger = PeerMessenger(this)
        // The ringing screen may take a moment to come up
        var found: Pair<String, String>? = null
        for (attempt in 1..10) {
            found = WatchClockAlarms.ringingScreen(this)
            if (found != null) break
            delay(1_000)
        }
        if (found == null) {
            DiagLog.d(TAG, "no ringing clock screen found")
            stop()
            return
        }
        ringing = found
        val key = "watchclock:${System.currentTimeMillis()}"
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        messenger.send(AlarmPaths.EVENT, AlarmEvent(key = key, ringing = true, text = time, canSnooze = true).encode())
        DiagLog.d(TAG, "watch alarm ringing in ${found.first}")

        // Follow it until the ringing screen goes away (stopped here, from the phone or timed out)
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < MAX_FOLLOW_MS) {
            delay(POLL_MS)
            if (WatchClockAlarms.ringingScreen(this, lookBackMs = System.currentTimeMillis() - startedAt + 10_000) == null) break
        }
        messenger.send(AlarmPaths.EVENT, AlarmEvent(key = key, ringing = false).encode())
        DiagLog.d(TAG, "watch alarm stopped")
        ringing = null
        WatchClockAlarms.scheduleNextCheck(this)
        stop()
    }

    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun quietNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.monitor_running))
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WatchAlarmMonitor"
        private const val CHANNEL_ID = "alarm_monitor"
        private const val NOTIFICATION_ID = 1002
        private const val POLL_MS = 1_000L
        private const val MAX_FOLLOW_MS = 30 * 60 * 1000L
        private const val ACTION_COMMAND = "command"
        private const val EXTRA_ACTION = "action"

        /** Clock package and screen of the alarm being followed, if any. */
        @Volatile
        private var ringing: Pair<String, String>? = null

        fun start(context: Context) = context.startForegroundService(
            Intent(context, WatchAlarmMonitorService::class.java)
        )

        /** Stop/snooze asked from the phone. */
        fun command(context: Context, action: AlarmAction) {
            if (ringing == null) return
            context.startForegroundService(
                Intent(context, WatchAlarmMonitorService::class.java)
                    .setAction(ACTION_COMMAND).putExtra(EXTRA_ACTION, action.name)
            )
        }
    }
}

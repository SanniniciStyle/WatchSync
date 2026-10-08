package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import it.sanninicistyle.watchsync.shared.AlarmAction
import it.sanninicistyle.watchsync.shared.InfoPaths
import it.sanninicistyle.watchsync.shared.InfoSync

/**
 * Alarms of the watch's own clock app. Wear clock apps ring with a full-screen activity and no
 * notification, so ringing is detected by scheduling a check at the system's next alarm time and
 * looking (through usage events) for the clock's ringing screen.
 */
object WatchClockAlarms {
    private const val TAG = "WatchClockAlarms"
    private const val CHECK_DELAY_MS = 1_500L

    /** Activity names clock apps use for their ringing screen. */
    private val RINGING_SCREENS = listOf("FiringAlarmActivity", "AlarmActivity", "AlarmAlertFullScreen")

    /** Schedules a check right after the next system alarm fires. Call when it changes. */
    fun scheduleNextCheck(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val next = am.nextAlarmClock
        if (next == null || next.showIntent?.creatorPackage == context.packageName) {
            // Don't cancel a pending check: the "next alarm" changes the instant an alarm fires,
            // right before its check runs. A check with nothing ringing just ends by itself.
            DiagLog.d(TAG, "no next alarm to watch")
            return
        }
        val at = next.triggerTime + CHECK_DELAY_MS
        // One check per alarm time, so a following alarm can't replace a check still pending
        // Exact when allowed (granted during setup); otherwise within a few seconds, still in doze
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkIntent(context, next.triggerTime))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, checkIntent(context, next.triggerTime))
        }
        DiagLog.d(TAG, "check scheduled at $at for ${next.showIntent?.creatorPackage}")
    }

    private fun checkIntent(context: Context, triggerTime: Long) = PendingIntent.getBroadcast(
        context, (triggerTime / 1000 % Int.MAX_VALUE).toInt(),
        Intent(context, AlarmCheckReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Returns the clock app package and ringing screen if one is in the foreground right now,
     * reading the last [lookBackMs] of usage events.
     */
    fun ringingScreen(context: Context, lookBackMs: Long = 120_000L): Pair<String, String>? {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - lookBackMs, now)
        val event = UsageEvents.Event()
        var resumed: Pair<String, String>? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val cls = event.className ?: continue
            if (RINGING_SCREENS.none { cls.endsWith(it) }) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumed = event.packageName to cls
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> if (resumed?.second == cls) resumed = null
            }
        }
        return resumed
    }

    /** Stops or snoozes the alarm ringing in the watch's clock app (standard clock intents). */
    fun execute(context: Context, clockPackage: String, action: AlarmAction) {
        val intent = when (action) {
            AlarmAction.STOP -> Intent(AlarmClock.ACTION_DISMISS_ALARM)
                .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
            AlarmAction.SNOOZE -> Intent(AlarmClock.ACTION_SNOOZE_ALARM)
        }.setPackage(clockPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onSuccess { DiagLog.d(TAG, "$action sent to $clockPackage") }
            .onFailure { DiagLog.w(TAG, "$action failed on $clockPackage", it) }
    }
}

/** Fires right after the system alarm time: starts following the clock's ringing screen. */
class AlarmCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DiagLog.d("WatchClockAlarms", "alarm time reached, looking for the ringing screen")
        WatchAlarmMonitorService.start(context)
    }
}

/** Keeps the alarm check in step with the system's next alarm. */
class NextAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WatchClockAlarms.scheduleNextCheck(context)
        InfoSync.sendNextAlarm(context, InfoPaths.WATCH_NEXT_ALARM, goAsync())
    }
}

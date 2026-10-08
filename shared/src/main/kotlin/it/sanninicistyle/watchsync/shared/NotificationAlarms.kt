package it.sanninicistyle.watchsync.shared

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks alarms of clock apps that ring through a notification (category "alarm" with its own
 * Stop/Snooze buttons, as most phone clock apps do) and replays Stop/Snooze on them.
 * Must be fed by a NotificationListenerService.
 */
class NotificationAlarms(private val ownPackage: String) {
    private val ringing = ConcurrentHashMap<String, StatusBarNotification>()

    /** Returns the event to send if [sbn] is a newly ringing alarm. */
    fun onPosted(sbn: StatusBarNotification): AlarmEvent? {
        if (!isRingingAlarm(sbn)) return null
        val isNew = ringing.put(sbn.key, sbn) == null
        if (!isNew) return null
        val extras = sbn.notification.extras
        return AlarmEvent(
            key = sbn.key,
            ringing = true,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            canSnooze = findAction(sbn, AlarmAction.SNOOZE) != null,
        ).also { DiagLog.d(TAG, "alarm ringing: ${sbn.packageName} ${it.title} ${it.text}") }
    }

    /** Returns the event to send if [sbn] was a ringing alarm that just stopped. */
    fun onRemoved(sbn: StatusBarNotification): AlarmEvent? {
        val removed = ringing.remove(sbn.key) ?: return null
        DiagLog.d(TAG, "alarm stopped: ${removed.packageName}")
        return AlarmEvent(key = sbn.key, ringing = false)
    }

    /** Presses Stop or Snooze on the original alarm. */
    fun execute(command: AlarmCommand): Boolean {
        val sbn = ringing[command.key] ?: return false.also { DiagLog.d(TAG, "alarm ${command.key} not ringing") }
        val action = findAction(sbn, command.action)
            ?: return false.also { DiagLog.w(TAG, "no ${command.action} button on ${sbn.packageName}") }
        return try {
            action.actionIntent.send()
            DiagLog.d(TAG, "pressed '${action.title}' on ${sbn.packageName}")
            true
        } catch (e: PendingIntent.CanceledException) {
            DiagLog.w(TAG, "alarm action no longer valid", e)
            false
        }
    }

    private fun isRingingAlarm(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == ownPackage) return false
        val n = sbn.notification
        // Upcoming-alarm reminders share the category but are not ongoing and have no Stop
        return n.category == Notification.CATEGORY_ALARM &&
            (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 &&
            !n.actions.isNullOrEmpty()
    }

    private fun findAction(sbn: StatusBarNotification, wanted: AlarmAction): Notification.Action? {
        val actions = sbn.notification.actions ?: return null
        // Prefer the semantic tag when the clock app sets it, then the button label
        val semantic = when (wanted) {
            AlarmAction.STOP -> Notification.Action.SEMANTIC_ACTION_DELETE
            AlarmAction.SNOOZE -> Notification.Action.SEMANTIC_ACTION_MUTE
        }
        actions.firstOrNull { it.semanticAction == semantic }?.let { return it }
        val words = if (wanted == AlarmAction.STOP) STOP_WORDS else SNOOZE_WORDS
        actions.firstOrNull { a -> words.any { a.title?.toString()?.contains(it, ignoreCase = true) == true } }
            ?.let { return it }
        // Clock apps put Stop/Dismiss first and Snooze second when labels are unknown
        return when (wanted) {
            AlarmAction.STOP -> actions.firstOrNull()
            AlarmAction.SNOOZE -> actions.getOrNull(1)
        }
    }

    private companion object {
        const val TAG = "NotificationAlarms"
        val STOP_WORDS = listOf("stop", "ignora", "interrompi", "disattiva", "chiudi", "dismiss", "off")
        val SNOOZE_WORDS = listOf("posponi", "rimanda", "ritarda", "snooze", "dopo")
    }
}

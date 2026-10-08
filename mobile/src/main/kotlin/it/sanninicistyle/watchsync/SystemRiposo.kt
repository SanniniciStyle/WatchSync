package it.sanninicistyle.watchsync

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import it.sanninicistyle.watchsync.shared.DiagLog

/**
 * Recognises the phone's own Riposo/Bedtime mode (e.g. Digital Wellbeing's, toggled from the quick
 * settings). Android doesn't tell apps which mode is on, only the rules in force: Riposo is
 * recognised by its rules (who may call, whether messages get through, which effects it hides),
 * learnt once from the user and different from plain DND's.
 */
class SystemRiposo(context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("system_riposo", Context.MODE_PRIVATE)

    val isLearnt: Boolean get() = prefs.contains(KEY_SIGNATURE)

    /** Rules in force right now, or null when nothing silences the phone. */
    private fun current(): String? {
        val filter = nm.currentInterruptionFilter
        if (filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return null
        return "$filter|" + signature(nm.consolidatedNotificationPolicy)
    }

    /** Plain DND's rules: a mode with these can't be told apart from DND, so it's never Riposo. */
    private fun plainDnd() = "${NotificationManager.INTERRUPTION_FILTER_PRIORITY}|" + signature(nm.notificationPolicy)

    val isActive: Boolean
        get() {
            val learnt = prefs.getString(KEY_SIGNATURE, null) ?: return false
            return current() == learnt
        }

    /**
     * Learns the rules in force as the phone's Riposo. False when they can't be told apart from
     * plain DND (nothing to learn from) or nothing is on.
     */
    fun learnCurrent(): Boolean {
        val now = current() ?: return false
        if (now == plainDnd()) return false
        prefs.edit { putString(KEY_SIGNATURE, now) }
        DiagLog.d(TAG, "learnt Riposo: $now")
        return true
    }

    private fun signature(p: NotificationManager.Policy) = listOf(
        p.priorityCategories, p.priorityCallSenders, p.priorityMessageSenders,
        p.priorityConversationSenders, p.suppressedVisualEffects,
    ).joinToString("|")

    private companion object {
        const val TAG = "SystemRiposo"
        const val KEY_SIGNATURE = "signature"
    }
}

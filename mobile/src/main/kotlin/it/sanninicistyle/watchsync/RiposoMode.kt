package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import android.service.notification.ZenPolicy
import android.util.Log
import androidx.core.content.edit

/**
 * The phone's "Riposo" mode. It belongs to WatchSync, so unlike the Digital Wellbeing one it can
 * be switched on and off by the app, which is what makes a 1:1 Bedtime sync possible.
 * Its rules mirror the user's existing Riposo: starred contacts and repeat callers ring, alarms
 * and media play, everything else is silent and the screen turns grey.
 */
class RiposoMode(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("riposo", Context.MODE_PRIVATE)

    /** Returns the rule id, creating the rule on first use. Null without DND access. */
    fun ensure(): String? {
        if (!nm.isNotificationPolicyAccessGranted) return null
        prefs.getString(KEY_ID, null)?.let { id ->
            val rule = nm.getAutomaticZenRule(id)
            if (rule != null) {
                // Keep the mode's name in the app's current language, and its moon icon
                val name = context.getString(R.string.riposo_name)
                if (rule.name != name || rule.iconResId != R.drawable.ic_mode_riposo) {
                    runCatching {
                        nm.updateAutomaticZenRule(id, AutomaticZenRule.Builder(rule).setName(name).setIconResId(R.drawable.ic_mode_riposo).build())
                    }
                }
                return id
            }
        }
        val id = try {
            nm.addAutomaticZenRule(buildRule(withGrayscale = true))
        } catch (e: IllegalArgumentException) {
            // Some device effects may be reserved to system apps: fall back without them
            DiagLog.w(TAG, "rule with grayscale rejected, retrying without", e)
            nm.addAutomaticZenRule(buildRule(withGrayscale = false))
        }
        prefs.edit { putString(KEY_ID, id) }
        DiagLog.d(TAG, "created Riposo rule $id")
        return id
    }

    val isActive: Boolean
        get() {
            val id = ensure() ?: return false
            val state = nm.getAutomaticZenRuleState(id)
            DiagLog.d(TAG, "rule $id state=$state icon=${nm.getAutomaticZenRule(id)?.iconResId}")
            return state == Condition.STATE_TRUE
        }

    fun setActive(active: Boolean) {
        val id = ensure() ?: return
        val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        nm.setAutomaticZenRuleState(
            id, Condition(CONDITION_ID, context.getString(R.string.riposo_name), state, Condition.SOURCE_CONTEXT)
        )
        DiagLog.d(TAG, "Riposo -> $active")
    }

    private fun buildRule(withGrayscale: Boolean): AutomaticZenRule {
        val policy = ZenPolicy.Builder()
            .allowAlarms(true)
            .allowMedia(true)
            .allowSystem(false)
            .allowReminders(false)
            .allowEvents(false)
            .allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
            .allowRepeatCallers(true)
            .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
            .allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
            .hideAllVisualEffects()
            .build()
        return AutomaticZenRule.Builder(context.getString(R.string.riposo_name), CONDITION_ID)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setIconResId(R.drawable.ic_mode_riposo)
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setZenPolicy(policy)
            .setManualInvocationAllowed(true)
            .setTriggerDescription(context.getString(R.string.riposo_trigger))
            .apply {
                if (withGrayscale) {
                    setDeviceEffects(ZenDeviceEffects.Builder().setShouldDisplayGrayscale(true).build())
                }
            }
            .build()
    }

    private companion object {
        const val TAG = "RiposoMode"
        const val KEY_ID = "rule_id"
        val CONDITION_ID: Uri = Uri.parse("condition://it.sanninicistyle.watchsync/riposo")
    }
}

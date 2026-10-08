package it.sanninicistyle.watchsync

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import android.service.notification.ZenPolicy
import androidx.core.content.edit
import it.sanninicistyle.watchsync.shared.DiagLog

/**
 * Riposo on the phone when it starts from the watch. Android lets no app switch on the phone's own
 * Riposo (Digital Wellbeing), so WatchSync brings its own, and only for as long as it's needed:
 * the mode is created when the watch goes to Bedtime and removed when it wakes up, so the phone's
 * list of modes holds only the native ones. Its rules are the native Riposo's, as learnt by
 * [SystemRiposo], so the night behaves the same whichever device started it.
 */
class RiposoMode(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("riposo", Context.MODE_PRIVATE)

    /** The rule while it exists (i.e. while Riposo from the watch is on). */
    private fun existingId(): String? {
        if (!nm.isNotificationPolicyAccessGranted) return null
        val id = prefs.getString(KEY_ID, null) ?: return null
        return id.takeIf { nm.getAutomaticZenRule(it) != null }
    }

    val isActive: Boolean
        get() = existingId()?.let { nm.getAutomaticZenRuleState(it) == Condition.STATE_TRUE } == true

    fun setActive(active: Boolean) {
        if (!nm.isNotificationPolicyAccessGranted) return
        if (active) {
            val id = existingId() ?: create()
            nm.setAutomaticZenRuleState(id, condition(Condition.STATE_TRUE))
        } else {
            existingId()?.let { id ->
                nm.setAutomaticZenRuleState(id, condition(Condition.STATE_FALSE))
                nm.removeAutomaticZenRule(id)
            }
            prefs.edit { remove(KEY_ID) }
        }
        DiagLog.d(TAG, "Riposo -> $active")
    }

    /**
     * Leaves no WatchSync mode behind: the Riposo rule when it's off (e.g. the app was stopped while
     * it was on) and the "Do not disturb (WatchSync)" mode Android creates for apps that set DND
     * before being the watch's companion.
     */
    fun cleanUp() {
        if (!nm.isNotificationPolicyAccessGranted) return
        nm.automaticZenRules.forEach { (id, _) ->
            val ours = id == prefs.getString(KEY_ID, null)
            if (ours && nm.getAutomaticZenRuleState(id) == Condition.STATE_TRUE) return@forEach
            runCatching { nm.removeAutomaticZenRule(id) }
                .onSuccess { DiagLog.d(TAG, "removed leftover mode $id") }
        }
        if (existingId() == null) prefs.edit { remove(KEY_ID) }
    }

    private fun create(): String {
        val id = try {
            nm.addAutomaticZenRule(buildRule(withEffects = true))
        } catch (e: IllegalArgumentException) {
            // Some device effects may be reserved to system apps: fall back without them
            DiagLog.w(TAG, "rule with effects rejected, retrying without", e)
            nm.addAutomaticZenRule(buildRule(withEffects = false))
        }
        prefs.edit { putString(KEY_ID, id) }
        DiagLog.d(TAG, "created Riposo rule $id")
        return id
    }

    private fun condition(state: Int) =
        Condition(CONDITION_ID, context.getString(R.string.riposo_name), state, Condition.SOURCE_CONTEXT)

    private fun buildRule(withEffects: Boolean): AutomaticZenRule =
        AutomaticZenRule.Builder(context.getString(R.string.riposo_name), CONDITION_ID)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setIconResId(R.drawable.ic_mode_riposo)
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setZenPolicy(SystemRiposo(context).learntPolicy()?.toZenPolicy() ?: DEFAULT_POLICY)
            .setManualInvocationAllowed(false)
            .setTriggerDescription(context.getString(R.string.riposo_trigger))
            .apply {
                // The native Riposo's night look: grey screen, dimmed wallpaper
                if (withEffects) {
                    setDeviceEffects(ZenDeviceEffects.Builder().setShouldDisplayGrayscale(true).setShouldDimWallpaper(true).build())
                }
            }
            .build()

    private fun Policy.toZenPolicy(): ZenPolicy {
        fun has(category: Int) = priorityCategories and category != 0
        fun people(senders: Int) = when (senders) {
            Policy.PRIORITY_SENDERS_ANY -> ZenPolicy.PEOPLE_TYPE_ANYONE
            Policy.PRIORITY_SENDERS_CONTACTS -> ZenPolicy.PEOPLE_TYPE_CONTACTS
            else -> ZenPolicy.PEOPLE_TYPE_STARRED
        }
        fun shown(effect: Int) = suppressedVisualEffects and effect == 0
        return ZenPolicy.Builder()
            .allowAlarms(has(Policy.PRIORITY_CATEGORY_ALARMS))
            .allowMedia(has(Policy.PRIORITY_CATEGORY_MEDIA))
            .allowSystem(has(Policy.PRIORITY_CATEGORY_SYSTEM))
            .allowReminders(has(Policy.PRIORITY_CATEGORY_REMINDERS))
            .allowEvents(has(Policy.PRIORITY_CATEGORY_EVENTS))
            .allowRepeatCallers(has(Policy.PRIORITY_CATEGORY_REPEAT_CALLERS))
            .allowCalls(if (has(Policy.PRIORITY_CATEGORY_CALLS)) people(priorityCallSenders) else ZenPolicy.PEOPLE_TYPE_NONE)
            .allowMessages(if (has(Policy.PRIORITY_CATEGORY_MESSAGES)) people(priorityMessageSenders) else ZenPolicy.PEOPLE_TYPE_NONE)
            .allowConversations(
                if (has(Policy.PRIORITY_CATEGORY_CONVERSATIONS)) priorityConversationSenders else ZenPolicy.CONVERSATION_SENDERS_NONE
            )
            .showFullScreenIntent(shown(Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT))
            .showLights(shown(Policy.SUPPRESSED_EFFECT_LIGHTS))
            .showPeeking(shown(Policy.SUPPRESSED_EFFECT_PEEK))
            .showStatusBarIcons(shown(Policy.SUPPRESSED_EFFECT_STATUS_BAR))
            .showBadges(shown(Policy.SUPPRESSED_EFFECT_BADGE))
            .showInAmbientDisplay(shown(Policy.SUPPRESSED_EFFECT_AMBIENT))
            .showInNotificationList(shown(Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST))
            .build()
    }

    private companion object {
        const val TAG = "RiposoMode"
        const val KEY_ID = "rule_id"
        val CONDITION_ID: Uri = Uri.parse("condition://it.sanninicistyle.watchsync/riposo")

        /** Until the native Riposo is learnt: starred and repeat callers, alarms and media only. */
        val DEFAULT_POLICY: ZenPolicy = ZenPolicy.Builder()
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
    }
}

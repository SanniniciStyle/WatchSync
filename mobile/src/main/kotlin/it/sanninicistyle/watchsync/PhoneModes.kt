package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import it.sanninicistyle.watchsync.shared.LocalModes
import kotlinx.coroutines.delay

/**
 * Reads and changes the phone's DND and Riposo.
 * DND changes reach the global DND (not an app mode) because WatchSync is the companion app of
 * the watch (CompanionDeviceManager, watch profile).
 */
class PhoneModes(context: Context) : LocalModes {
    private val nm = context.getSystemService(NotificationManager::class.java)
    val riposo = RiposoMode(context)
    val systemRiposo = SystemRiposo(context)

    /** WatchSync's own Riposo (set from the watch or the app) or the phone's native one. */
    override fun readBedtime(): Boolean = riposo.isActive || systemRiposo.isActive

    override fun readAnyDnd(): Boolean =
        nm.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }

    override suspend fun apply(dnd: Boolean, bedtime: Boolean) {
        if (bedtime) {
            if (!readBedtime()) {
                // DND and Rest are never on together: a manual DND stays below Rest otherwise
                if (readAnyDnd()) setDnd(false)
                riposo.setActive(true)
            }
            return
        }
        if (riposo.isActive) {
            riposo.setActive(false)
            delay(MODE_EXIT_MS)
        }
        if (readAnyDnd() != dnd) setDnd(dnd)
    }

    fun setDnd(on: Boolean) {
        if (!nm.isNotificationPolicyAccessGranted) {
            DiagLog.w(TAG, "DND access not granted")
            return
        }
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        DiagLog.d(TAG, "setDnd($on)")
    }

    private companion object {
        const val TAG = "PhoneModes"
        const val MODE_EXIT_MS = 800L
    }
}

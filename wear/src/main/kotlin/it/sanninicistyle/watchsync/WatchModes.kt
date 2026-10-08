package it.sanninicistyle.watchsync

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import it.sanninicistyle.watchsync.shared.LocalModes
import kotlinx.coroutines.delay

/**
 * Reads and changes the watch's DND and Bedtime modes.
 *
 * DND goes through NotificationManager: thanks to the companion (watch profile) association it
 * drives the global DND, not an app-specific mode. Bedtime is driven by writing the "bedtime_mode"
 * setting only, which makes the system run its full transition (DND, night light, watch face).
 */
class WatchModes(private val context: Context) : LocalModes {
    private val nm = context.getSystemService(NotificationManager::class.java)

    val hasPolicyAccess: Boolean
        get() = nm.isNotificationPolicyAccessGranted

    override fun readBedtime(): Boolean =
        Settings.Global.getInt(context.contentResolver, BEDTIME_SETTING, 0) == 1

    override fun readAnyDnd(): Boolean =
        nm.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }

    override suspend fun apply(dnd: Boolean, bedtime: Boolean) {
        if (bedtime) {
            if (!readBedtime()) setBedtime(true)
            return
        }
        if (readBedtime()) {
            setBedtime(false)
            // Leaving Bedtime also clears the DND it turned on: let that finish first
            delay(BEDTIME_EXIT_MS)
        }
        if (readAnyDnd() != dnd) setDnd(dnd)
    }

    fun setDnd(on: Boolean) {
        if (!hasPolicyAccess) {
            Log.w(TAG, "DND access not granted")
            return
        }
        val filter = if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(filter)
        Log.d(TAG, "setDnd($on)")
    }

    /** Needs WRITE_SECURE_SETTINGS, granted once over adb during setup. */
    fun setBedtime(on: Boolean): Boolean = try {
        Settings.Global.putInt(context.contentResolver, BEDTIME_SETTING, if (on) 1 else 0)
            .also { Log.d(TAG, "setBedtime($on) -> $it") }
    } catch (e: SecurityException) {
        Log.w(TAG, "WRITE_SECURE_SETTINGS not granted", e)
        false
    }

    companion object {
        private const val TAG = "WatchModes"
        const val BEDTIME_SETTING = "bedtime_mode"
        private const val BEDTIME_EXIT_MS = 1_500L
    }
}

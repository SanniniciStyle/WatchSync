package it.sanninicistyle.watchsync.shared

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService

/**
 * The notification listener is what notices mode changes and ringing alarms. Android may leave it
 * unbound after an app update or when the system kills the process (some phones do this often):
 * ask for a rebind whenever it should be running and isn't.
 *
 * Android may also drop the binding without telling the listener (e.g. while the OEM's power
 * manager has the process frozen), so [connected] can't be trusted alone: the rebind is also asked
 * periodically. When the listener is bound, the request does nothing.
 */
object ListenerBinding {
    @Volatile
    var connected = false

    @Volatile
    private var lastRequest = 0L

    /** When this process started: the system binds the listener shortly after, give it time. */
    private val processStart = SystemClock.elapsedRealtime()
    private val main = Handler(Looper.getMainLooper())

    /** The listener is running, or may still be about to (just after the process started). */
    val healthy: Boolean
        get() = connected || SystemClock.elapsedRealtime() - processStart < HEALTHY_GRACE_MS

    fun ensure(context: Context, service: Class<out NotificationListenerService>) {
        val now = SystemClock.elapsedRealtime()
        if (!connected && now - processStart < START_GRACE_MS) {
            val app = context.applicationContext
            main.postDelayed({ ensure(app, service) }, START_GRACE_MS - (now - processStart) + 500)
            return
        }
        if (connected && now - lastRequest < RECHECK_MS) return
        val component = ComponentName(context, service)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationListenerAccessGranted(component)) return
        lastRequest = now
        if (connected) {
            // Probably bound: a rebind request does nothing if so
            NotificationListenerService.requestRebind(component)
            return
        }
        // Not bound. requestRebind() alone is ignored on some phones (seen on HONOR / Android 17):
        // switching the component off and on makes the system drop and rebind it for sure
        DiagLog.d("ListenerBinding", "listener not connected: re-enabling it")
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        NotificationListenerService.requestRebind(component)
    }

    private const val RECHECK_MS = 60_000L
    private const val START_GRACE_MS = 10_000L
    private const val HEALTHY_GRACE_MS = 15_000L
}

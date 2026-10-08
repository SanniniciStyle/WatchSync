package it.sanninicistyle.watchsync.shared

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService

/**
 * The notification listener is what notices mode changes and ringing alarms. Android may leave it
 * unbound after an app update or when the system kills the process (some phones do this often):
 * ask for a rebind whenever it should be running and isn't.
 */
object ListenerBinding {
    @Volatile
    var connected = false

    fun ensure(context: Context, service: Class<out NotificationListenerService>) {
        if (connected) return
        val component = ComponentName(context, service)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationListenerAccessGranted(component)) return
        DiagLog.d("ListenerBinding", "listener not connected: asking for a rebind")
        NotificationListenerService.requestRebind(component)
    }
}

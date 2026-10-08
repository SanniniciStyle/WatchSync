package it.sanninicistyle.watchsync

import android.content.Context
import it.sanninicistyle.watchsync.shared.ModeSync
import it.sanninicistyle.watchsync.shared.NotificationAlarms
import it.sanninicistyle.watchsync.shared.SyncPaths

/** Single instances shared by the watch services. */
object WatchSyncComponents {
    /** Alarms of watch apps that ring through a notification (not the built-in clock). */
    val alarms = NotificationAlarms("it.sanninicistyle.watchsync")

    @Volatile
    private var modeSync: ModeSync? = null

    fun modeSync(context: Context): ModeSync =
        modeSync ?: synchronized(this) {
            modeSync ?: ModeSync(
                context.applicationContext,
                WatchModes(context.applicationContext),
                SyncPaths.WATCH_STATE,
            ).also { modeSync = it }
        }
}

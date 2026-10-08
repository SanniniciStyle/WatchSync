package it.sanninicistyle.watchsync

import android.content.Context
import it.sanninicistyle.watchsync.shared.ModeSync
import it.sanninicistyle.watchsync.shared.NotificationAlarms
import it.sanninicistyle.watchsync.shared.SyncPaths

/** Package name, used to ignore WatchSync's own mirrored alarms. */
object BuildConfigPackage {
    const val NAME = "it.sanninicistyle.watchsync"
}

/** Single instances shared by the phone services and UI. */
object PhoneSyncComponents {
    @Volatile
    private var modes: PhoneModes? = null

    @Volatile
    private var modeSync: ModeSync? = null

    /** Alarms of the phone's clock apps currently ringing. */
    val alarms = NotificationAlarms(BuildConfigPackage.NAME)

    fun modes(context: Context): PhoneModes =
        modes ?: synchronized(this) {
            modes ?: PhoneModes(context.applicationContext).also { modes = it }
        }

    fun modeSync(context: Context): ModeSync =
        modeSync ?: synchronized(this) {
            modeSync ?: ModeSync(
                context.applicationContext,
                modes(context),
                SyncPaths.PHONE_STATE,
            ).also { modeSync = it }
        }
}

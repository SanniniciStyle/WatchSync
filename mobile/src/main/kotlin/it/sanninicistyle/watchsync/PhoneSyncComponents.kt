package it.sanninicistyle.watchsync

import android.content.Context
import it.sanninicistyle.watchsync.shared.ModeSync
import it.sanninicistyle.watchsync.shared.SyncPaths

/** Single instances shared by the phone services and UI. */
object PhoneSyncComponents {
    @Volatile
    private var modes: PhoneModes? = null

    @Volatile
    private var modeSync: ModeSync? = null

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

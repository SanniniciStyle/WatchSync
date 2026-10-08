package it.sanninicistyle.watchsync

import android.app.Application
import it.sanninicistyle.watchsync.shared.DiagLog

class WatchSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
    }
}

package it.sanninicistyle.watchsync

import android.app.Application
import it.sanninicistyle.watchsync.shared.DiagLog

class WatchSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        // The phone learns by itself whether this watch still needs setting up
        WatchSetup.report(this)
    }
}

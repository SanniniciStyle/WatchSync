package it.sanninicistyle.watchsync

import android.app.Application
import it.sanninicistyle.watchsync.shared.DiagLog
import it.sanninicistyle.watchsync.shared.ListenerBinding

class WatchSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        ListenerBinding.ensure(this, WatchModeListenerService::class.java)
        // The phone learns by itself whether this watch still needs setting up
        WatchSetup.report(this)
    }
}

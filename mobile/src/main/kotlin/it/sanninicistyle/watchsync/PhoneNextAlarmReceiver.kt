package it.sanninicistyle.watchsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.sanninicistyle.watchsync.shared.InfoPaths
import it.sanninicistyle.watchsync.shared.InfoSync

/** Tells the watch whenever the phone's next alarm changes. */
class PhoneNextAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        InfoSync.sendNextAlarm(context, InfoPaths.PHONE_NEXT_ALARM, goAsync())
    }
}

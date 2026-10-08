package it.sanninicistyle.watchsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import it.sanninicistyle.watchsync.shared.AlarmEvent

/**
 * Prototype only. Usage:
 * adb shell am broadcast -n it.sanninicistyle.watchsync/.DebugReceiver --es dnd on|off
 * adb shell am broadcast -n it.sanninicistyle.watchsync/.DebugReceiver --es bedtime on|off
 * adb shell am broadcast -n it.sanninicistyle.watchsync/.DebugReceiver --es ring on|off   (ringing screen, no real alarm)
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val modes = WatchModes(context)
        intent.getStringExtra("dnd")?.let { modes.setDnd(it == "on") }
        intent.getStringExtra("bedtime")?.let { modes.setBedtime(it == "on") }
        when (intent.getStringExtra("ring")) {
            "on" -> WatchAlarmRingService.ring(
                context, AlarmEvent(key = "debug", ringing = true, title = "Sveglia", text = "08:20", canSnooze = true),
            )
            "off" -> WatchAlarmRingService.remoteStopped(context)
        }
        Log.d("DebugReceiver", "dnd=${modes.readAnyDnd()} bedtime=${modes.readBedtime()} access=${modes.hasPolicyAccess}")
    }
}

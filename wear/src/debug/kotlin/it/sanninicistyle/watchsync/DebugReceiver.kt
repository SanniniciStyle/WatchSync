package it.sanninicistyle.watchsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Prototype only. Usage:
 * adb shell am broadcast -n it.sanninicistyle.watchsync/.DebugReceiver --es dnd on|off
 * adb shell am broadcast -n it.sanninicistyle.watchsync/.DebugReceiver --es bedtime on|off
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val modes = WatchModes(context)
        intent.getStringExtra("dnd")?.let { modes.setDnd(it == "on") }
        intent.getStringExtra("bedtime")?.let { modes.setBedtime(it == "on") }
        Log.d("DebugReceiver", "dnd=${modes.readAnyDnd()} bedtime=${modes.readBedtime()} access=${modes.hasPolicyAccess}")
    }
}

package it.sanninicistyle.watchsync

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.runBlocking

/**
 * Debug builds only (Honor blocks shell broadcasts to third-party apps). Usage:
 * adb shell am start -n it.sanninicistyle.watchsync/.DebugActivity --es dnd on|off
 * adb shell am start -n it.sanninicistyle.watchsync/.DebugActivity --es riposo on|off
 * Each call appends the resulting state to files/debug.txt (read it with run-as).
 */
class DebugActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = runCatching {
            val modes = PhoneSyncComponents.modes(this)
            intent.getStringExtra("dnd")?.let { modes.setDnd(it == "on") }
            intent.getStringExtra("riposo")?.let { modes.riposo.setActive(it == "on") }
            if (intent.hasExtra("push")) runBlocking { PhoneSyncComponents.modeSync(this@DebugActivity).pushCurrent() }
            "dnd=${modes.readAnyDnd()} riposo=${modes.readBedtime()}"
        }.getOrElse { it.stackTraceToString() }
        java.io.File(filesDir, "debug.txt")
            .appendText("${System.currentTimeMillis()} ${intent.extras?.keySet()} -> $result\n")
        finish()
    }
}

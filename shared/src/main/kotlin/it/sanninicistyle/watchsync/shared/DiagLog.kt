package it.sanninicistyle.watchsync.shared

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logcat plus, in debuggable builds only, a small rolling file (files/diag.log) that can be read
 * with `run-as` on devices that hide or encrypt app logs.
 */
object DiagLog {
    @Volatile
    private var file: File? = null
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    fun init(context: Context) {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        file = if (debuggable) File(context.filesDir, "diag.log") else null
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("D", tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
        write("W", tag, message + (error?.let { " | $it" } ?: ""))
    }

    @Synchronized
    private fun write(level: String, tag: String, message: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) f.writeText("")
            f.appendText("${time.format(Date())} $level/$tag: $message\n")
        }
    }

    private const val MAX_BYTES = 256 * 1024
}

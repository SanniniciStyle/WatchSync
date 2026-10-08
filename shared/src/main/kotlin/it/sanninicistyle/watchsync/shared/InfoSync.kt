package it.sanninicistyle.watchsync.shared

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.launch

/** Sends this device's live info (its next alarm) to the peer. */
object InfoSync {
    fun sendNextAlarm(context: Context, path: String, pending: BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        AppScope.launch {
            try {
                PeerMessenger(app).send(path, NextAlarm.read(app).encode())
            } finally {
                pending?.finish()
            }
        }
    }
}

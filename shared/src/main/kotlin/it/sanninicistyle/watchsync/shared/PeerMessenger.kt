package it.sanninicistyle.watchsync.shared

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Sends messages to the paired device running the other half of WatchSync. */
class PeerMessenger(context: Context) {
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val messageClient = Wearable.getMessageClient(context)

    /** Returns true if at least one peer received the message. */
    suspend fun send(path: String, data: ByteArray = ByteArray(0)): Boolean {
        val nodes = try {
            capabilityClient
                .getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        } catch (e: Exception) {
            DiagLog.w(TAG, "peer lookup failed", e)
            return false
        }
        if (nodes.isEmpty()) {
            DiagLog.d(TAG, "no reachable peer for $path")
            return false
        }
        var delivered = false
        for (node in nodes) {
            try {
                messageClient.sendMessage(node.id, path, data).await()
                DiagLog.d(TAG, "sent $path to ${node.displayName}")
                delivered = true
            } catch (e: Exception) {
                DiagLog.w(TAG, "send $path to ${node.displayName} failed", e)
            }
        }
        return delivered
    }

    suspend fun isPeerReachable(): Boolean = try {
        capabilityClient
            .getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val TAG = "PeerMessenger"
    }
}

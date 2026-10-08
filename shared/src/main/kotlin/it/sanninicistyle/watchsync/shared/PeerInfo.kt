package it.sanninicistyle.watchsync.shared

import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The paired device as seen right now: its real name and whether it is reachable. */
data class Peer(val name: String, val connected: Boolean)

/** Live data about the other device, kept fresh by the sync services. */
object PeerInfo {
    private val _nextAlarm = MutableStateFlow(NextAlarm())

    /** Next alarm of the other device, as it last reported it. */
    val nextAlarm: StateFlow<NextAlarm> = _nextAlarm.asStateFlow()

    private val _watchStatus = MutableStateFlow<WatchStatus?>(null)

    /** Setup state of the watch app as it last reported it (phone side only), null if unknown. */
    val watchStatus: StateFlow<WatchStatus?> = _watchStatus.asStateFlow()

    fun load(context: Context) {
        val p = prefs(context)
        _nextAlarm.value = NextAlarm(p.getLong(KEY_AT, 0L), p.getString(KEY_APP, "").orEmpty())
        _watchStatus.value = p.getString(KEY_STATUS, null)
            ?.let { runCatching { WatchStatus.decode(android.util.Base64.decode(it, 0)) }.getOrNull() }
    }

    fun setWatchStatus(context: Context, status: WatchStatus) {
        _watchStatus.value = status
        prefs(context).edit { putString(KEY_STATUS, android.util.Base64.encodeToString(status.encode(), 0)) }
    }

    /** Forgets the last report, so the next one is known to be fresh. */
    fun clearWatchStatus(context: Context) {
        _watchStatus.value = null
        prefs(context).edit { remove(KEY_STATUS) }
    }

    fun setNextAlarm(context: Context, alarm: NextAlarm) {
        _nextAlarm.value = alarm
        prefs(context).edit {
            putLong(KEY_AT, alarm.triggerAt)
            putString(KEY_APP, alarm.appLabel)
        }
    }

    /**
     * The other device running WatchSync, updated as it connects and disconnects. The name is the
     * one the device itself reports (e.g. "Pixel Watch 5", or the phone's Bluetooth name).
     */
    fun peer(context: Context): Flow<Peer?> = callbackFlow {
        val client = Wearable.getCapabilityClient(context)
        fun emit(info: CapabilityInfo) {
            val node = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
            trySend(node?.let { Peer(it.displayName, connected = true) } ?: lastKnown(context))
        }
        val listener = CapabilityClient.OnCapabilityChangedListener { info ->
            info.nodes.firstOrNull()?.let { remember(context, it.displayName) }
            emit(info)
        }
        client.addListener(listener, SyncPaths.CAPABILITY)
        runCatching {
            client.getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
        }.onSuccess { info ->
            info.nodes.firstOrNull()?.let { remember(context, it.displayName) }
            emit(info)
        }.onFailure { trySend(lastKnown(context)) }
        awaitClose { client.removeListener(listener) }
    }

    private fun lastKnown(context: Context): Peer? =
        prefs(context).getString(KEY_NAME, null)?.let { Peer(it, connected = false) }

    private fun remember(context: Context, name: String) =
        prefs(context).edit { putString(KEY_NAME, name) }

    private fun prefs(context: Context) =
        context.getSharedPreferences("peer_info", Context.MODE_PRIVATE)

    private const val KEY_AT = "next_alarm_at"
    private const val KEY_APP = "next_alarm_app"
    private const val KEY_NAME = "peer_name"
    private const val KEY_STATUS = "watch_status"
}

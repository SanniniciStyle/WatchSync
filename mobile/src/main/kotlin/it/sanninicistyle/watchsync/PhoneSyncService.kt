package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import it.sanninicistyle.watchsync.shared.AlarmCommand
import it.sanninicistyle.watchsync.shared.AlarmEvent
import it.sanninicistyle.watchsync.shared.AlarmPaths
import it.sanninicistyle.watchsync.adb.WatchIdentity
import it.sanninicistyle.watchsync.shared.InfoPaths
import it.sanninicistyle.watchsync.shared.InfoSync
import it.sanninicistyle.watchsync.shared.ModeState
import it.sanninicistyle.watchsync.shared.PeerMessenger
import it.sanninicistyle.watchsync.shared.StatusPaths
import it.sanninicistyle.watchsync.shared.WatchStatus
import it.sanninicistyle.watchsync.shared.NextAlarm
import it.sanninicistyle.watchsync.shared.PeerInfo
import it.sanninicistyle.watchsync.shared.SyncPaths
import kotlinx.coroutines.runBlocking

/** Receives the watch's modes and alarms. onMessageReceived already runs on a worker thread. */
class PhoneSyncService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        DiagLog.d(TAG, "from watch: ${event.path} (${event.data.size} bytes)")
        when (event.path) {
            SyncPaths.PING -> Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, SyncPaths.PONG, ByteArray(0))
            SyncPaths.PONG -> PeerInfo.onPong()
            SyncPaths.WATCH_STATE -> runBlocking {
                val state = runCatching { ModeState.decode(event.data) }.getOrNull()
                if (state == null) DiagLog.w(TAG, "bad state payload")
                else PhoneSyncComponents.modeSync(this@PhoneSyncService).onRemoteState(state)
            }
            SyncPaths.STATE_REQUEST -> runBlocking {
                PhoneSyncComponents.modeSync(this@PhoneSyncService).pushCurrent()
            }
            // An alarm of the watch started or stopped ringing
            AlarmPaths.EVENT -> runCatching { AlarmEvent.decode(event.data) }.getOrNull()?.let {
                if (it.ringing) AlarmRingService.ring(this, it) else AlarmRingService.remoteStopped(this)
            }
            // The user stopped or snoozed this phone's alarm from the watch
            AlarmPaths.COMMAND -> runCatching { AlarmCommand.decode(event.data) }.getOrNull()?.let {
                PhoneSyncComponents.alarms.execute(it)
            }
            // The watch's next alarm changed
            InfoPaths.WATCH_NEXT_ALARM -> runCatching { NextAlarm.decode(event.data) }.getOrNull()?.let {
                PeerInfo.setNextAlarm(this, it)
            }
            InfoPaths.REQUEST -> InfoSync.sendNextAlarm(this, InfoPaths.PHONE_NEXT_ALARM)
            StatusPaths.WATCH -> runCatching { WatchStatus.decode(event.data) }.getOrNull()?.let {
                PeerInfo.setWatchStatus(this, it)
                WatchSetupAlert.update(this, it)
                if (it.btAddress.isNotEmpty()) WatchIdentity.setAddress(this, it.btAddress)
            }
            else -> super.onMessageReceived(event)
        }
    }

    /** The watch came back in reach: realign modes and info right away. */
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.nodes.isEmpty()) return
        DiagLog.d(TAG, "watch reachable again: resync")
        runBlocking {
            PhoneSyncComponents.modeSync(this@PhoneSyncService).pushCurrent()
            PeerMessenger(this@PhoneSyncService).send(StatusPaths.REQUEST)
        }
        InfoSync.sendNextAlarm(this, InfoPaths.PHONE_NEXT_ALARM)
    }

    private companion object {
        const val TAG = "PhoneSyncService"
    }
}

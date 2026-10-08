package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.sanninicistyle.watchsync.shared.AlarmCommand
import it.sanninicistyle.watchsync.shared.AlarmEvent
import it.sanninicistyle.watchsync.shared.AlarmPaths
import it.sanninicistyle.watchsync.shared.InfoPaths
import it.sanninicistyle.watchsync.shared.InfoSync
import it.sanninicistyle.watchsync.shared.ModeState
import it.sanninicistyle.watchsync.shared.StatusPaths
import it.sanninicistyle.watchsync.shared.NextAlarm
import it.sanninicistyle.watchsync.shared.PeerInfo
import it.sanninicistyle.watchsync.shared.SyncPaths
import kotlinx.coroutines.runBlocking

/** Receives the phone's modes and alarms. onMessageReceived already runs on a worker thread. */
class WatchSyncService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        DiagLog.d(TAG, "from phone: ${event.path} (${event.data.size} bytes)")
        when (event.path) {
            SyncPaths.PHONE_STATE -> runBlocking {
                val state = runCatching { ModeState.decode(event.data) }.getOrNull()
                if (state == null) DiagLog.w(TAG, "bad state payload")
                else WatchSyncComponents.modeSync(this@WatchSyncService).onRemoteState(state)
            }
            SyncPaths.STATE_REQUEST -> runBlocking {
                WatchSyncComponents.modeSync(this@WatchSyncService).pushCurrent()
            }
            // An alarm of the phone started or stopped ringing
            AlarmPaths.EVENT -> runCatching { AlarmEvent.decode(event.data) }.getOrNull()?.let {
                if (it.ringing) WatchAlarmRingService.ring(this, it) else WatchAlarmRingService.remoteStopped(this)
            }
            // The user stopped or snoozed this watch's alarm from the phone
            AlarmPaths.COMMAND -> runCatching { AlarmCommand.decode(event.data) }.getOrNull()?.let {
                if (!WatchSyncComponents.alarms.execute(it)) WatchAlarmMonitorService.command(this, it.action)
            }
            // The phone's next alarm changed
            InfoPaths.PHONE_NEXT_ALARM -> runCatching { NextAlarm.decode(event.data) }.getOrNull()?.let {
                PeerInfo.setNextAlarm(this, it)
            }
            InfoPaths.REQUEST -> InfoSync.sendNextAlarm(this, InfoPaths.WATCH_NEXT_ALARM)
            StatusPaths.REQUEST -> WatchSetup.report(this)
            else -> super.onMessageReceived(event)
        }
    }

    /** The phone came back in reach: realign modes and info right away. */
    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.nodes.isEmpty()) return
        DiagLog.d(TAG, "phone reachable again: resync")
        runBlocking { WatchSyncComponents.modeSync(this@WatchSyncService).pushCurrent() }
        InfoSync.sendNextAlarm(this, InfoPaths.WATCH_NEXT_ALARM)
        WatchSetup.report(this)
    }

    private companion object {
        const val TAG = "WatchSyncService"
    }
}

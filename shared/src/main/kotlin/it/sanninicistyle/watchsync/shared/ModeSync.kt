package it.sanninicistyle.watchsync.shared

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.delay

/** The device-specific half of the sync: how this device reads and changes its own modes. */
interface LocalModes {
    /** Bedtime state of this device. */
    fun readBedtime(): Boolean

    /** True if DND is on for any reason (user, Bedtime, other modes). */
    fun readAnyDnd(): Boolean

    /** Applies the given modes to this device. Called off the main thread. */
    suspend fun apply(dnd: Boolean, bedtime: Boolean)
}

/**
 * State-based, loop-free synchronisation of DND and Bedtime between the two devices.
 *
 * Every local change is reported as a full [ModeState]. A state received from the peer is
 * applied only if it differs from the local one, and is remembered as "already in sync", so the
 * local events it triggers are not echoed back. If both sides change at the same moment, the
 * newest change wins.
 */
class ModeSync(
    context: Context,
    private val local: LocalModes,
    private val outPath: String,
    private val messenger: PeerMessenger = PeerMessenger(context),
) {
    private val prefs = context.getSharedPreferences("mode_sync", Context.MODE_PRIVATE)

    /** Current state of this device as the peer should see it. */
    fun currentState(now: Long = System.currentTimeMillis()): ModeState {
        val bedtime = local.readBedtime()
        // DND and Bedtime are exclusive: Bedtime turns DND on by itself, so it never counts as DND
        val dnd = !bedtime && local.readAnyDnd()
        return ModeState(dnd = dnd, bedtime = bedtime, changedAt = now)
    }

    /** Call after the local modes changed (debounced by the caller). */
    suspend fun onLocalChanged() {
        // While a remote state is being applied the device passes through intermediate states
        // (e.g. Bedtime off but its DND not yet cleared): wait for it to settle first.
        val settleAt = prefs.getLong(KEY_APPLYING_UNTIL, 0L)
        val wait = settleAt - System.currentTimeMillis()
        if (wait > 0) delay(wait)

        val state = currentState()
        if (state.sameModesAs(lastSynced())) {
            DiagLog.d(TAG, "local change already in sync: $state")
            return
        }
        DiagLog.d(TAG, "local change -> peer: $state")
        remember(state)
        messenger.send(outPath, state.encode())
    }

    /** Call when the peer sent its state. */
    suspend fun onRemoteState(remote: ModeState) {
        val mine = currentState()
        if (remote.sameModesAs(mine)) {
            DiagLog.d(TAG, "remote already matches: $remote")
            remember(remote)
            return
        }
        val last = lastSynced()
        if (remote.changedAt < last.changedAt && !mine.sameModesAs(last)) {
            // We changed after the peer did: our state wins, send it back
            DiagLog.d(TAG, "local newer than remote, resending $mine")
            remember(mine)
            messenger.send(outPath, mine.encode())
            return
        }
        DiagLog.d(TAG, "applying remote $remote (local was $mine)")
        remember(remote)
        prefs.edit { putLong(KEY_APPLYING_UNTIL, System.currentTimeMillis() + SETTLE_MS) }
        local.apply(dnd = remote.dnd, bedtime = remote.bedtime)
    }

    /** Sends the current state no matter what, e.g. when the peer asks for it. */
    suspend fun pushCurrent() {
        val state = currentState()
        remember(state)
        messenger.send(outPath, state.encode())
    }

    private fun lastSynced() = ModeState(
        dnd = prefs.getBoolean(KEY_DND, false),
        bedtime = prefs.getBoolean(KEY_BEDTIME, false),
        changedAt = prefs.getLong(KEY_AT, 0L),
    )

    private fun remember(state: ModeState) = prefs.edit {
        putBoolean(KEY_DND, state.dnd)
        putBoolean(KEY_BEDTIME, state.bedtime)
        putLong(KEY_AT, state.changedAt)
    }

    private companion object {
        const val TAG = "ModeSync"
        const val KEY_DND = "dnd"
        const val KEY_BEDTIME = "bedtime"
        const val KEY_AT = "changed_at"
        const val KEY_APPLYING_UNTIL = "applying_until"
        const val SETTLE_MS = 4_000L
    }
}

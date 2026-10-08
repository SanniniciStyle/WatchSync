package it.sanninicistyle.watchsync.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Full mode state of one device. Devices exchange whole states (never toggles), so a lost or
 * duplicated message can't leave the two sides out of step, and an echo of a state that was just
 * applied changes nothing.
 *
 * [dnd] and [bedtime] are mutually exclusive: Bedtime silences the device by itself, so a state
 * with [bedtime] set never has [dnd] set.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ModeState(
    @ProtoNumber(1) val dnd: Boolean,
    @ProtoNumber(2) val bedtime: Boolean,
    /** Wall-clock time of the change, used to resolve simultaneous changes on both devices. */
    @ProtoNumber(3) val changedAt: Long,
) {
    fun encode(): ByteArray = ProtoBuf.encodeToByteArray(this)

    fun sameModesAs(other: ModeState) = dnd == other.dnd && bedtime == other.bedtime

    companion object {
        fun decode(bytes: ByteArray): ModeState = ProtoBuf.decodeFromByteArray(bytes)
    }
}

object SyncPaths {
    /** Phone -> watch: the phone's current mode state. */
    const val PHONE_STATE = "/watchsync/state/phone"

    /** Watch -> phone: the watch's current mode state. */
    const val WATCH_STATE = "/watchsync/state/watch"

    /** Either side asks the peer to send its current state (e.g. after a reconnect). */
    const val STATE_REQUEST = "/watchsync/state/request"

    /** Capability both apps declare in res/values/wear.xml, used to find the peer node. */
    const val CAPABILITY = "watchsync"

    /** Liveness check, answered with [PONG] straight to the sender. */
    const val PING = "/watchsync/ping"
    const val PONG = "/watchsync/pong"
}

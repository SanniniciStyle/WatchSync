package it.sanninicistyle.watchsync.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/** An alarm of a clock app that is ringing (or stopped ringing) on one device. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AlarmEvent(
    /** Notification key of the ringing alarm on the source device. */
    @ProtoNumber(1) val key: String,
    @ProtoNumber(2) val ringing: Boolean,
    /** Text shown by the clock app (time and/or label), if any. */
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val text: String = "",
    /** Whether the source alarm offers snooze, so the mirror can show the button. */
    @ProtoNumber(5) val canSnooze: Boolean = false,
) {
    fun encode(): ByteArray = ProtoBuf.encodeToByteArray(this)

    companion object {
        fun decode(bytes: ByteArray): AlarmEvent = ProtoBuf.decodeFromByteArray(bytes)
    }
}

/** What the user did on the mirrored alarm, to be replayed on the original one. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AlarmCommand(
    @ProtoNumber(1) val key: String,
    @ProtoNumber(2) val action: AlarmAction,
) {
    fun encode(): ByteArray = ProtoBuf.encodeToByteArray(this)

    companion object {
        fun decode(bytes: ByteArray): AlarmCommand = ProtoBuf.decodeFromByteArray(bytes)
    }
}

@Serializable
enum class AlarmAction { STOP, SNOOZE }

object AlarmPaths {
    /** The peer's original alarm started or stopped ringing. */
    const val EVENT = "/watchsync/alarm/event"

    /** The user stopped or snoozed the mirrored alarm. */
    const val COMMAND = "/watchsync/alarm/command"
}

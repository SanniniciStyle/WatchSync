package it.sanninicistyle.watchsync.shared

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/** What the watch app has been granted, reported to the phone so setup shows the real state. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WatchStatus(
    /** Bedtime control (granted over adb). */
    @ProtoNumber(1) val secureSettings: Boolean = false,
    /** Detecting the clock's ringing screen (granted over adb). */
    @ProtoNumber(2) val usageStats: Boolean = false,
    /** Noticing DND/Bedtime changes and alarm notifications. */
    @ProtoNumber(3) val listener: Boolean = false,
    /** Changing DND. */
    @ProtoNumber(4) val dndAccess: Boolean = false,
    /** Companion association (watch profile): global DND instead of an app mode. */
    @ProtoNumber(5) val associated: Boolean = false,
    @ProtoNumber(6) val notifications: Boolean = false,
    @ProtoNumber(7) val appVersion: String = "",
) {
    val ready: Boolean
        get() = secureSettings && usageStats && listener && dndAccess && associated && notifications

    fun encode(): ByteArray = ProtoBuf.encodeToByteArray(this)

    companion object {
        fun decode(bytes: ByteArray): WatchStatus = ProtoBuf.decodeFromByteArray(bytes)
    }
}

object StatusPaths {
    /** Phone -> watch: report your setup state. */
    const val REQUEST = "/watchsync/status/request"

    /** Watch -> phone: the setup state. */
    const val WATCH = "/watchsync/status/watch"
}

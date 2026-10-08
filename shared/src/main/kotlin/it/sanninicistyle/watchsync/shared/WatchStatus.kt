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
    /** The watch's Bluetooth address, as the phone read it during setup ("" if unknown). */
    @ProtoNumber(8) val btAddress: String = "",
    /** Checking the clock at the exact second an alarm is due. */
    @ProtoNumber(9) val exactAlarms: Boolean = false,
    /** What this watch app knows how to report (1: before exactAlarms). */
    @ProtoNumber(10) val protocol: Int = 1,
) {
    /** Grants still missing, by name (empty when ready). Older watch apps don't report exactAlarms. */
    val missing: List<String>
        get() = buildList {
            if (!secureSettings) add("secure settings")
            if (!usageStats) add("usage access")
            if (!listener) add("notification access")
            if (!dndAccess) add("Do Not Disturb access")
            if (!associated) add("companion association")
            if (!notifications) add("notifications")
            if (protocol >= 2 && !exactAlarms) add("exact alarms")
        }

    val ready: Boolean get() = missing.isEmpty()

    /** The watch app is older than this phone app. */
    val outdated: Boolean get() = protocol < PROTOCOL

    fun encode(): ByteArray = ProtoBuf.encodeToByteArray(this)

    companion object {
        const val PROTOCOL = 2

        fun decode(bytes: ByteArray): WatchStatus = ProtoBuf.decodeFromByteArray(bytes)
    }
}

object StatusPaths {
    /** Phone -> watch: report your setup state. */
    const val REQUEST = "/watchsync/status/request"

    /** Watch -> phone: the setup state. */
    const val WATCH = "/watchsync/status/watch"

    /** Phone -> watch: the watch's Bluetooth address, for the watch to keep. */
    const val ADDRESS = "/watchsync/status/address"
}

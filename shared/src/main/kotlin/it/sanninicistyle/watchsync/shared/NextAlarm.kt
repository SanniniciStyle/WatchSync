package it.sanninicistyle.watchsync.shared

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/** The next alarm the system knows about on one device, as read from AlarmManager. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NextAlarm(
    /** Epoch millis of the alarm, 0 if none. */
    @ProtoNumber(1) val triggerAt: Long = 0L,
    /** Name of the app that set it, as shown by the launcher. */
    @ProtoNumber(2) val appLabel: String = "",
) {
    val exists: Boolean get() = triggerAt > 0L

    fun encode(): ByteArray = ProtoBuf.encodeToByteArray(this)

    companion object {
        fun decode(bytes: ByteArray): NextAlarm = ProtoBuf.decodeFromByteArray(bytes)

        /** Reads the system's next alarm clock and the label of the app that owns it. */
        fun read(context: Context): NextAlarm {
            val info = context.getSystemService(AlarmManager::class.java).nextAlarmClock
                ?: return NextAlarm()
            val pm = context.packageManager
            // The app that set the alarm; some clock apps leave no show intent, then the
            // phone's default clock app is the one that owns it
            val pkg = info.showIntent?.creatorPackage
                ?: pm.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.ResolveInfoFlags.of(0))
                    ?.activityInfo?.packageName
            val label = pkg?.let {
                runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(it, PackageManager.ApplicationInfoFlags.of(0))).toString()
                }.getOrNull()
            }.orEmpty()
            DiagLog.d("NextAlarm", "next alarm ${info.triggerTime} from $pkg ($label)")
            return NextAlarm(info.triggerTime, label)
        }
    }
}

object InfoPaths {
    /** Watch -> phone: the watch's next alarm (sent when it changes or when asked). */
    const val WATCH_NEXT_ALARM = "/watchsync/info/watch-next-alarm"

    /** Phone -> watch: the phone's next alarm. */
    const val PHONE_NEXT_ALARM = "/watchsync/info/phone-next-alarm"

    /** Either side asks the peer to send its info. */
    const val REQUEST = "/watchsync/info/request"
}

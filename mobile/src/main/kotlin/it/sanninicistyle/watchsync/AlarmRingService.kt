package it.sanninicistyle.watchsync

import it.sanninicistyle.watchsync.shared.DiagLog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import it.sanninicistyle.watchsync.shared.AlarmAction
import it.sanninicistyle.watchsync.shared.AppScope
import it.sanninicistyle.watchsync.shared.AlarmCommand
import it.sanninicistyle.watchsync.shared.AlarmEvent
import it.sanninicistyle.watchsync.shared.AlarmPaths
import it.sanninicistyle.watchsync.shared.MirroredAlarm
import it.sanninicistyle.watchsync.shared.PeerMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rings on the phone while an alarm of the watch is ringing: alarm sound, vibration and a
 * full-screen alarm screen. Stop/Snooze are sent back to the watch's original alarm.
 */
class AlarmRingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: MediaPlayer? = null
    private var current: AlarmEvent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DiagLog.d("AlarmRing", "command ${intent?.action}, current=${current?.key}")
        when (intent?.action) {
            ACTION_RING -> intent.getByteArrayExtra(EXTRA_EVENT)
                ?.let { runCatching { AlarmEvent.decode(it) }.getOrNull() }
                ?.let { ring(it) }
            ACTION_STOP -> userAction(AlarmAction.STOP)
            ACTION_SNOOZE -> userAction(AlarmAction.SNOOZE)
            ACTION_REMOTE_STOPPED -> finish()
        }
        return START_NOT_STICKY
    }

    private fun ring(event: AlarmEvent) {
        current = event
        MirroredAlarm.set(event)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(event),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        startSound()
        startVibration()
        // Never ring forever if the stop message gets lost
        // Bring the alarm screen up even when the screen is off (companion apps may start
        // activities from the background); the full-screen intent is the fallback
        runCatching {
            startActivity(Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        scope.launch {
            delay(MAX_RING_MS)
            finish()
        }
    }

    private fun userAction(action: AlarmAction) {
        current?.let { event ->
            // AppScope: the send must survive this service stopping right below
            val messenger = PeerMessenger(applicationContext)
            AppScope.launch { messenger.send(AlarmPaths.COMMAND, AlarmCommand(event.key, action).encode()) }
        }
        finish()
    }

    private fun finish() {
        player?.runCatching { stop(); release() }
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        MirroredAlarm.set(null)
        current = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { DiagLog.w(TAG, "cannot play alarm sound", it) }.getOrNull()
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 600, 400, 600, 1200)
        getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
            VibrationEffect.createWaveform(pattern, 0),
            android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun buildNotification(event: AlarmEvent): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the service plays the sound itself
                enableVibration(false)
            }
        )
        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(event.title.ifBlank { getString(R.string.alarm_from_watch) })
            .setContentText(event.text.ifBlank { getString(R.string.alarm_from_watch) })
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(Notification.Action.Builder(null, getString(R.string.alarm_stop), servicePending(ACTION_STOP)).build())
        if (event.canSnooze) {
            builder.addAction(Notification.Action.Builder(null, getString(R.string.alarm_snooze), servicePending(ACTION_SNOOZE)).build())
        }
        return builder.build()
    }

    private fun servicePending(action: String) = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, AlarmRingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        private const val CHANNEL_ID = "mirrored_alarm"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_RING_MS = 10 * 60 * 1000L
        private const val ACTION_RING = "ring"
        private const val ACTION_STOP = "stop"
        private const val ACTION_SNOOZE = "snooze"
        private const val ACTION_REMOTE_STOPPED = "remote_stopped"
        private const val EXTRA_EVENT = "event"

        fun ring(context: Context, event: AlarmEvent) = context.startForegroundService(
            Intent(context, AlarmRingService::class.java).setAction(ACTION_RING)
                .putExtra(EXTRA_EVENT, event.encode())
        )

        fun remoteStopped(context: Context) {
            if (MirroredAlarm.current.value == null) return
            context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_REMOTE_STOPPED))
        }

        fun userStop(context: Context) =
            context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP))

        fun userSnooze(context: Context) =
            context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_SNOOZE))
    }
}

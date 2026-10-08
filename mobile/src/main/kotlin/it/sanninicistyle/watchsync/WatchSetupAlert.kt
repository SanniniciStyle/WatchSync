package it.sanninicistyle.watchsync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import it.sanninicistyle.watchsync.shared.WatchStatus

/**
 * Tells the user when the watch needs to be set up (again): a new watch, a reset, a reinstalled
 * watch app. The watch reports its grants by itself, so this needs no action to be detected.
 */
object WatchSetupAlert {
    private const val CHANNEL_ID = "watch_setup"
    private const val NOTIFICATION_ID = 7

    fun update(context: Context, status: WatchStatus) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (status.ready) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_setup), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SETUP, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(context.getString(R.string.watch_needs_setup_title))
                .setContentText(context.getString(R.string.watch_needs_setup_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }
}

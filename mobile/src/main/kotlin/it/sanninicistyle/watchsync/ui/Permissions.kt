package it.sanninicistyle.watchsync.ui

import android.Manifest
import android.app.NotificationManager
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import it.sanninicistyle.watchsync.PhoneModeListenerService
import it.sanninicistyle.watchsync.SystemRiposo
import it.sanninicistyle.watchsync.shared.PeerInfo

/** Everything the phone app needs from the user, read live from the system. */
data class Permissions(
    val notifications: Boolean,
    val listener: Boolean,
    val dndAccess: Boolean,
    val watchAssociated: Boolean,
    val fullScreen: Boolean,
    /** The watch app reported every grant it needs (set up over the watch's wireless debugging). */
    val watchReady: Boolean,
    /** WatchSync recognises the phone's own Riposo (learnt once). */
    val riposoLearnt: Boolean,
) {
    val allGranted get() = missing == 0

    val missing get() = listOf(notifications, listener, dndAccess, watchReady && watchAssociated, riposoLearnt, fullScreen).count { !it }

    companion object {
        fun read(context: Context): Permissions {
            val nm = context.getSystemService(NotificationManager::class.java)
            val cdm = context.getSystemService(CompanionDeviceManager::class.java)
            return Permissions(
                notifications = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
                listener = nm.isNotificationListenerAccessGranted(
                    ComponentName(context, PhoneModeListenerService::class.java)
                ),
                dndAccess = nm.isNotificationPolicyAccessGranted,
                watchAssociated = cdm.myAssociations.isNotEmpty(),
                fullScreen = nm.canUseFullScreenIntent(),
                watchReady = PeerInfo.watchStatus.value?.ready == true,
                riposoLearnt = SystemRiposo(context).isLearnt,
            )
        }

        fun listenerSettings(context: Context) =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, PhoneModeListenerService::class.java).flattenToString(),
            )

        fun dndSettings() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

        fun fullScreenSettings(context: Context) =
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    }
}

package it.sanninicistyle.watchsync

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import it.sanninicistyle.watchsync.shared.AppScope
import it.sanninicistyle.watchsync.shared.ListenerBinding
import it.sanninicistyle.watchsync.shared.PeerMessenger
import it.sanninicistyle.watchsync.shared.StatusPaths
import it.sanninicistyle.watchsync.shared.WatchStatus
import kotlinx.coroutines.launch

/** Reads what the watch app has been granted and reports it to the phone. */
object WatchSetup {
    fun read(context: Context): WatchStatus {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ops = context.getSystemService(AppOpsManager::class.java)
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return WatchStatus(
            secureSettings = granted(Manifest.permission.WRITE_SECURE_SETTINGS),
            usageStats = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
                AppOpsManager.MODE_ALLOWED,
            // Granted is not enough: it must be bound too (setting the watch up again rebinds it)
            listener = nm.isNotificationListenerAccessGranted(ComponentName(context, WatchModeListenerService::class.java)) &&
                ListenerBinding.healthy,
            dndAccess = nm.isNotificationPolicyAccessGranted,
            associated = cdm.myAssociations.isNotEmpty(),
            notifications = granted(Manifest.permission.POST_NOTIFICATIONS),
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
            btAddress = prefs(context).getString(KEY_ADDRESS, "").orEmpty(),
            exactAlarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            protocol = WatchStatus.PROTOCOL,
        )
    }

    /** Kept so the phone can find this watch again even after its app's data is gone. */
    fun setAddress(context: Context, address: String) {
        prefs(context).edit().putString(KEY_ADDRESS, address).apply()
        report(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    private const val KEY_ADDRESS = "bt_address"

    fun report(context: Context) {
        val app = context.applicationContext
        AppScope.launch { PeerMessenger(app).send(StatusPaths.WATCH, read(app).encode()) }
    }
}

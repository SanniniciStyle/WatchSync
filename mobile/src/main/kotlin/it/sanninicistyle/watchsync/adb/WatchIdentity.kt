package it.sanninicistyle.watchsync.adb

import android.content.Context
import androidx.core.content.edit

/** The watch's Bluetooth address, read from the watch itself during setup. */
object WatchIdentity {
    private const val PREFS = "watch_identity"
    private const val KEY_ADDRESS = "bt_address"

    fun address(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDRESS, null)

    fun setAddress(context: Context, address: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_ADDRESS, address) }
}

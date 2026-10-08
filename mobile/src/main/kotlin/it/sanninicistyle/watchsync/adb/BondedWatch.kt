package it.sanninicistyle.watchsync.adb

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Finds the watch among the phone's paired Bluetooth devices (needs "Nearby devices"): used when
 * the watch was set up before its address was recorded.
 */
object BondedWatch {
    const val PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

    fun canRead(context: Context) = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The address of the paired watch named [name] (the Wear OS node's name), if exactly one fits. */
    fun address(context: Context, name: String?): String? {
        if (!canRead(context)) return null
        val bonded = context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
        val wearables = bonded.filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE }
        val byName = name?.let { n -> bonded.filter { it.name?.startsWith(n) == true || it.alias?.startsWith(n) == true } }.orEmpty()
        return (byName.singleOrNull() ?: wearables.singleOrNull())?.address
    }
}

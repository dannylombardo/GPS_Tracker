package com.dannylombardo.gpstracker.tracking

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Starts a drive the moment the phone connects to a car's Bluetooth (car stereo or
 * hands-free kit), and treats it disconnecting as leaving the car. GPS still confirms
 * the car is actually moving before a drive is saved, so sitting in the driveway with
 * the radio on doesn't count. Needs "Nearby devices" access on Android 12 and later.
 */
class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!TrackingPrefs.isAutoTrackEnabled(context)) return
        if (!Permissions.hasBluetooth(context)) return
        val device = intent.device() ?: return
        if (!isCarAudio(device)) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                DriveTrackingService.vehicleEntered(context, DriveTrigger.BLUETOOTH)
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                if (DriveState.isServiceRunning) DriveTrackingService.vehicleExited(context)
        }
    }

    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun isCarAudio(device: BluetoothDevice): Boolean =
        try {
            isCarDeviceClass(device.bluetoothClass?.deviceClass)
        } catch (e: SecurityException) {
            false
        }

    companion object {
        fun isCarDeviceClass(deviceClass: Int?): Boolean =
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
    }
}

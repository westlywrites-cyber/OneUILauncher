package com.westly.oneuilauncher.qs

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Everything the Quick Settings tiles read and change.
 *
 * Android does not let normal apps switch Wi-Fi, Bluetooth, mobile data or airplane mode,
 * so those tiles open the matching system screen. The rest are real switches.
 */
object QuickControls {

    // ---- Opening system screens ----

    private fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // The screen does not exist on this phone.
        }
    }

    fun openInternet(context: Context) {
        val action = if (Build.VERSION.SDK_INT >= 29) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        open(context, Intent(action))
    }

    fun openNetworkSettings(context: Context) = open(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
    fun openBluetooth(context: Context) = open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun openAirplane(context: Context) = open(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun openLocation(context: Context) = open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun openBatterySaver(context: Context) = open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    fun openSystemSettings(context: Context) = open(context, Intent(Settings.ACTION_SETTINGS))

    fun openWriteSettingsPermission(context: Context) = open(
        context,
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    private fun openDndPermission(context: Context) =
        open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    // ---- Reading state ----

    /** Pair of (connected through Wi-Fi, connected through mobile data). */
    fun network(context: Context): Pair<Boolean, Boolean> {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            Pair(
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            )
        } catch (_: Exception) {
            Pair(false, false)
        }
    }

    fun isAirplane(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun isLocationOn(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 28) {
            context.getSystemService(LocationManager::class.java).isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
        }
    } catch (_: Exception) {
        false
    }

    fun isBatterySaver(context: Context): Boolean = try {
        context.getSystemService(PowerManager::class.java).isPowerSaveMode
    } catch (_: Exception) {
        false
    }

    // ---- Auto-rotate (needs the "Modify system settings" permission) ----

    fun isAutoRotate(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /** Returns false when the permission is missing (the caller then asks for it). */
    fun setAutoRotate(context: Context, on: Boolean): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (on) 1 else 0
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---- Do not disturb (needs "Do Not Disturb access") ----

    fun isDoNotDisturb(context: Context): Boolean = try {
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    } catch (_: Exception) {
        false
    }

    /** Switches Do Not Disturb, or opens the permission screen first. */
    fun toggleDoNotDisturb(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) {
            openDndPermission(context)
            return false
        }
        return try {
            manager.setInterruptionFilter(
                if (isDoNotDisturb(context)) {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                } else {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---- Sound mode ----

    fun ringerMode(context: Context): Int =
        context.getSystemService(AudioManager::class.java).ringerMode

    /** Sound -> Vibrate -> (Mute, only when allowed) -> Sound. */
    fun cycleRingerMode(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        try {
            when (audio.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    audio.ringerMode = if (manager.isNotificationPolicyAccessGranted) {
                        AudioManager.RINGER_MODE_SILENT
                    } else {
                        AudioManager.RINGER_MODE_NORMAL
                    }
                }
                else -> audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (_: Exception) {
            // Android refused this sound mode.
        }
    }

    // ---- Flashlight ----

    fun torchCameraId(context: Context): String? = try {
        val manager = context.getSystemService(CameraManager::class.java)
        manager.cameraIdList.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (_: Exception) {
        null
    }

    fun setTorch(context: Context, cameraId: String, on: Boolean) {
        try {
            context.getSystemService(CameraManager::class.java).setTorchMode(cameraId, on)
        } catch (_: CameraAccessException) {
            // The camera is busy.
        } catch (_: Exception) {
            // Ignore.
        }
    }

    // ---- Brightness (needs "Modify system settings") ----

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun brightness(context: Context): Int = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Exception) {
        128
    }

    fun setBrightness(context: Context, value: Int) {
        if (!Settings.System.canWrite(context)) return
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(1, 255)
            )
        } catch (_: Exception) {
            // Ignore.
        }
    }

    // ---- Media volume ----

    fun mediaVolume(context: Context): Int =
        context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC)

    fun mediaVolumeMax(context: Context): Int =
        context.getSystemService(AudioManager::class.java).getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun setMediaVolume(context: Context, value: Int) {
        try {
            context.getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        } catch (_: Exception) {
            // Ignore.
        }
    }
}

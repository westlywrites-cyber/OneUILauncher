package com.westly.oneuilauncher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the custom status bar on screen over every app.
 * Runs as a foreground service so Android does not stop it in the background.
 */
class StatusBarOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var barView: StatusBarView? = null
    private var connectivity: ConnectivityManager? = null

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            barView?.timeText = currentTime()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applyBattery(intent)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> StatusBarView.Network.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> StatusBarView.Network.CELLULAR
                else -> StatusBarView.Network.NONE
            }
            barView?.post { barView?.network = kind }
        }

        override fun onLost(network: Network) {
            barView?.post { barView?.network = StatusBarView.Network.NONE }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (barView == null && !showBar()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeBar()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Status bar overlay",
                NotificationManager.IMPORTANCE_MIN
            )
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Status bar overlay is on")
            .setContentText("Turn it off in Launcher settings")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Adds the bar to the screen. Returns false if Android refused. */
    private fun showBar(): Boolean {
        if (!Settings.canDrawOverlays(this)) return false

        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = StatusBarView(this).apply { timeText = currentTime() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            manager.addView(view, params)
        } catch (_: Exception) {
            return false
        }
        windowManager = manager
        barView = view

        // Time changes.
        val timeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, tickReceiver, timeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Battery (the first value arrives straight away).
        val sticky = ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (sticky != null) applyBattery(sticky)

        // Network type.
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(networkCallback)
            connectivity = cm
        } catch (_: Exception) {
            // The network icon simply stays hidden.
        }
        return true
    }

    private fun removeBar() {
        try { unregisterReceiver(tickReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        try { connectivity?.unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
        connectivity = null
        barView?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) { }
        }
        barView = null
    }

    private fun applyBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level >= 0 && scale > 0) {
            barView?.batteryLevel = level * 100 / scale
        }
        barView?.charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun currentTime(): String {
        val pattern = if (DateFormat.is24HourFormat(this)) "H:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            resources.getDimensionPixelSize(id)
        } else {
            (24 * resources.displayMetrics.density).toInt()
        }
    }

    private companion object {
        const val CHANNEL_ID = "status_bar_overlay"
        const val NOTIFICATION_ID = 6001
    }
}

package com.westly.oneuilauncher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.westly.oneuilauncher.data.SettingsStore
import com.westly.oneuilauncher.lock.LockScreenActivity
import com.westly.oneuilauncher.notifications.NotificationRepository
import com.westly.oneuilauncher.qs.QuickSettingsActivity
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
    private var touchZone: View? = null
    private var zoneParams: WindowManager.LayoutParams? = null
    private val iconCache = HashMap<String, Bitmap>()
    private val notificationListener: () -> Unit = { refreshNotificationUi() }
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
        val settings = SettingsStore.load(this)

        // Status bar overlay.
        if (settings.statusBarOverlay) {
            if (barView == null && !showBar() && !settings.lockScreen) {
                stopSelf()
                return START_NOT_STICKY
            }
        } else if (barView != null) {
            removeBar()
        }

        // Lock screen trigger.
        if (settings.lockScreen) registerScreenReceiver() else unregisterScreenReceiver()

        if (!settings.statusBarOverlay && !settings.lockScreen) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        removeBar()
        super.onDestroy()
    }

    // ---- Lock screen: show it whenever the display turns on while Android is locked ----

    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showLockScreen()
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
            // Already gone.
        }
        screenReceiverRegistered = false
    }

    private fun showLockScreen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) return

        // Leave incoming calls and calls in progress alone.
        val audio = getSystemService(AudioManager::class.java)
        if (audio.mode != AudioManager.MODE_NORMAL) return

        try {
            startActivity(
                Intent(this, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (_: Exception) {
            // Android refused to open it from the background.
        }
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Launcher services",
                NotificationManager.IMPORTANCE_MIN
            )
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Launcher services are on")
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

        addTouchZone(manager)

        // Notification icons in the bar, and the swipe zone width, follow the notifications.
        NotificationRepository.addListener(notificationListener)
        refreshNotificationUi()

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
        NotificationRepository.removeListener(notificationListener)
        try { unregisterReceiver(tickReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        try { connectivity?.unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
        connectivity = null
        barView?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) { }
        }
        barView = null
        touchZone?.let { zone ->
            try { windowManager?.removeView(zone) } catch (_: Exception) { }
        }
        touchZone = null
        zoneParams = null
    }

    /**
     * An invisible window over the right half of the bar. Swiping down on it opens the
     * Quick Settings panel. The left half is left alone, so Android's own notification
     * shade still opens from there.
     */
    private fun addTouchZone(manager: WindowManager) {
        val zone = View(this)
        val slop = ViewConfiguration.get(this).scaledTouchSlop * 1.5f

        zone.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            private var opened = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        opened = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!opened && event.rawY - startY > slop) {
                            opened = true
                            openQuickSettings()
                        }
                    }
                }
                return true
            }
        })

        val params = WindowManager.LayoutParams(
            zoneWidth(),
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            manager.addView(zone, params)
            touchZone = zone
            zoneParams = params
        } catch (_: Exception) {
            // Without the touch zone the bar still works; only the swipe is missing.
        }
    }

    /**
     * Without notification access only the right half of the bar opens the launcher panel
     * (the left half keeps opening Android's own shade). With access, the whole bar does.
     */
    private fun zoneWidth(): Int =
        if (NotificationRepository.connected) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            resources.displayMetrics.widthPixels / 2
        }

    private fun refreshNotificationUi() {
        // Icons of the apps that have notifications.
        val packages = NotificationRepository.items.map { it.packageName }.distinct()
        val shown = packages.take(MAX_ICONS)
        val sizePx = (16 * resources.displayMetrics.density).toInt()
        val icons = shown.mapNotNull { pkg ->
            iconCache[pkg] ?: try {
                packageManager.getApplicationIcon(pkg)
                    .toBitmap(width = sizePx, height = sizePx)
                    .also { iconCache[pkg] = it }
            } catch (_: Exception) {
                null
            }
        }
        barView?.notificationIcons = icons
        barView?.extraNotifications = packages.size - shown.size

        // Swipe zone width.
        val zone = touchZone
        val params = zoneParams
        val width = zoneWidth()
        if (zone != null && params != null && params.width != width) {
            params.width = width
            try {
                windowManager?.updateViewLayout(zone, params)
            } catch (_: Exception) {
                // The window is already gone.
            }
        }
    }

    private fun openQuickSettings() {
        try {
            startActivity(
                Intent(this, QuickSettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Android refused to open the panel from the background.
        }
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
        const val MAX_ICONS = 4
    }
}

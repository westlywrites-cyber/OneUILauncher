package com.westly.oneuilauncher.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/** One notification, reduced to what the launcher shows. */
data class NotifItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
    val contentIntent: PendingIntent?,
    val clearable: Boolean
)

/**
 * Holds the phone's current notifications. [NotificationListener] fills it; the Quick Settings
 * panel and the status bar overlay read it.
 */
object NotificationRepository {

    var items by mutableStateOf<List<NotifItem>>(emptyList())
        private set

    /** True while Android has the launcher connected as a notification listener. */
    var connected by mutableStateOf(false)
        private set

    private var service: NotificationListener? = null
    private val listeners = mutableSetOf<() -> Unit>()

    /** For code outside Compose (the overlay service) that wants to know about changes. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    internal fun onConnected(listenerService: NotificationListener) {
        service = listenerService
        connected = true
        notifyChanged()
    }

    internal fun onDisconnected() {
        service = null
        connected = false
        items = emptyList()
        notifyChanged()
    }

    internal fun update(list: List<NotifItem>) {
        items = list
        notifyChanged()
    }

    fun dismiss(key: String) {
        try {
            service?.cancelNotification(key)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    fun clearAll() {
        try {
            service?.cancelAllNotifications()
        } catch (_: Exception) {
            // Nothing to clear.
        }
    }

    /** Opens whatever the notification opens when tapped. Returns false if it could not. */
    fun open(context: Context, item: NotifItem): Boolean {
        val pendingIntent = item.contentIntent ?: return false
        return try {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } else {
                null
            }
            pendingIntent.send(context, 0, null, null, null, null, options)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }
}

/** Permission helpers for "Notification access". */
object NotificationAccess {

    fun has(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the Android screen where the user turns notification access on. */
    fun openSettings(context: Context) {
        val component = ComponentName(context, NotificationListener::class.java)
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component.flattenToString()
                )
        } else {
            null
        }
        val attempts = listOfNotNull(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // Try the next screen.
            }
        }
    }

    /** If access is on but Android has not connected the listener yet, ask it to. */
    fun ensureConnected(context: Context) {
        if (has(context) && !NotificationRepository.connected) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NotificationListener::class.java)
                )
            } catch (_: Exception) {
                // Android will connect it by itself shortly.
            }
        }
    }
}

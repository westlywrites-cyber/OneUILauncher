package com.westly.oneuilauncher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Receives every notification on the phone (once the user allows Notification access). */
class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationRepository.onConnected(this)
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationRepository.onDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refresh()
    }

    private fun refresh() {
        NotificationRepository.update(buildItems())
    }

    private fun buildItems(): List<NotifItem> {
        val active: Array<StatusBarNotification> = try {
            activeNotifications ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        val pm = packageManager

        return active
            .filter { sbn ->
                sbn.packageName != packageName &&
                    (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            .map { sbn ->
                val extras = sbn.notification.extras
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (_: Exception) {
                    sbn.packageName
                }
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                        ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                    )?.toString().orEmpty()

                NotifItem(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = label,
                    title = title,
                    text = text,
                    time = sbn.postTime,
                    contentIntent = sbn.notification.contentIntent,
                    clearable = sbn.isClearable
                )
            }
            .sortedByDescending { it.time }
    }
}

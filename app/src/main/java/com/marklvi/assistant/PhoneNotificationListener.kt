package com.marklvi.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

data class PhoneNotice(val app: String, val title: String, val text: String)

class PhoneNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isNotBlank() || text.isNotBlank()) {
            latest.removeAll { it.app == sbn.packageName && it.title == title }
            latest.add(0, PhoneNotice(sbn.packageName, title, text))
            while (latest.size > 30) latest.removeAt(latest.lastIndex)
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    companion object {
        val latest = CopyOnWriteArrayList<PhoneNotice>()
    }
}

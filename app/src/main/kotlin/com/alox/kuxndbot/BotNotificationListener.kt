package com.alox.kuxndbot

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class BotNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AloxSilentTest"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        Log.d(TAG, "================================")
        Log.d(TAG, "✅ Notification Listener CONNECTED")
        Log.d(TAG, "================================")
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        // نهتم بـ Messenger فقط
        if (sbn.packageName != "com.facebook.orca") {
            return
        }

        val notification = sbn.notification
        val extras = notification.extras

        Log.d(TAG, "================================")
        Log.d(TAG, "📩 Messenger notification received")
        Log.d(TAG, "Key: ${sbn.key}")
        Log.d(TAG, "Package: ${sbn.packageName}")
        Log.d(TAG, "ID: ${sbn.id}")
        Log.d(TAG, "Tag: ${sbn.tag}")
        Log.d(TAG, "Post time: ${sbn.postTime}")

        // حالة الإشعار
        Log.d(
            TAG,
            "isOngoing: ${sbn.isOngoing}"
        )

        Log.d(
            TAG,
            "isClearable: ${sbn.isClearable}"
        )

        Log.d(
            TAG,
            "Notification flags: ${notification.flags}"
        )

        // هل هو Silent حسب Android؟
        val isSilent =
            (notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0

        Log.d(
            TAG,
            "FLAG_ONLY_ALERT_ONCE: $isSilent"
        )

        // =========================
        // النصوص
        // =========================

        logExtra(
            extras,
            Notification.EXTRA_TITLE,
            "EXTRA_TITLE"
        )

        logExtra(
            extras,
            Notification.EXTRA_TEXT,
            "EXTRA_TEXT"
        )

        logExtra(
            extras,
            Notification.EXTRA_BIG_TEXT,
            "EXTRA_BIG_TEXT"
        )

        logExtra(
            extras,
            Notification.EXTRA_SUB_TEXT,
            "EXTRA_SUB_TEXT"
        )

        // =========================
        // جميع Extras
        // =========================

        Log.d(TAG, "---- ALL EXTRAS ----")

        for (key in extras.keySet()) {

            try {

                val value = extras.get(key)

                Log.d(
                    TAG,
                    "$key = $value"
                )

            } catch (e: Exception) {

                Log.d(
                    TAG,
                    "$key = <error>"
                )
            }
        }

        Log.d(TAG, "---- END EXTRAS ----")
        Log.d(TAG, "================================")
    }

    private fun logExtra(
        extras: Bundle,
        key: String,
        label: String
    ) {

        val value =
            extras.getCharSequence(key)

        Log.d(
            TAG,
            "$label: ${value ?: "<null>"}"
        )
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap
    ) {

        if (sbn.packageName != "com.facebook.orca") {
            return
        }

        Log.d(TAG, "🗑 Messenger notification REMOVED")
        Log.d(TAG, "Key: ${sbn.key}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        Log.d(
            TAG,
            "❌ Notification Listener DISCONNECTED"
        )
    }
}

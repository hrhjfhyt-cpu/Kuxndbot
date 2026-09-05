package com.alox.kuxndbot

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class BotNotificationListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        val prefs = getSharedPreferences(
            "bot_settings",
            MODE_PRIVATE
        )

        // البوت متوقف
        if (!prefs.getBoolean("enabled", false)) {
            return
        }

        // التطبيق المستهدف
        val targetPackage = prefs.getString(
            "package",
            ""
        ) ?: ""

        // تجاهل أي تطبيق آخر
        if (
            targetPackage.isNotEmpty() &&
            sbn.packageName != targetPackage
        ) {
            return
        }

        val delay = prefs.getInt(
            "delay",
            10
        ).coerceAtLeast(0)

        val reply = prefs.getString(
            "reply",
            ""
        ) ?: ""

        if (reply.isBlank()) {
            return
        }

        // الانتظار حسب عدد الثواني المحدد
        handler.postDelayed({

            sendReply(
                sbn,
                reply
            )

        }, delay * 1000L)
    }

    private fun sendReply(
        sbn: StatusBarNotification,
        text: String
    ) {

        val notification =
            sbn.notification

        val actions =
            notification.actions
                ?: return

        for (action in actions) {

            val remoteInputs =
                action.remoteInputs
                    ?: continue

            if (remoteInputs.isEmpty()) {
                continue
            }

            val intent = Intent()

            val results = Bundle()

            for (input in remoteInputs) {

                results.putCharSequence(
                    input.resultKey,
                    text
                )
            }

            RemoteInput.addResultsToIntent(
                remoteInputs,
                intent,
                results
            )

            try {

                action.actionIntent.send(
                    this,
                    0,
                    intent
                )

            } catch (_: Exception) {
                // التطبيق قد لا يسمح بالرد من الإشعار
            }

            return
        }
    }
}

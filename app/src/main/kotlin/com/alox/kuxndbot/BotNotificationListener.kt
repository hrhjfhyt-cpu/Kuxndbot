package com.alox.kuxndbot

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class BotNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AloxBot"
        private const val MESSENGER_PACKAGE = "com.facebook.orca"
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()

        Log.d(TAG, "================================")
        Log.d(TAG, "✅ BOT CONNECTED")
        Log.d(TAG, "================================")

        // فحص الإشعارات الموجودة حاليًا
        try {
            activeNotifications
                ?.filter {
                    it.packageName == MESSENGER_PACKAGE
                }
                ?.forEach {
                    Log.d(TAG, "📩 Existing Messenger notification: ${it.key}")
                }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        // Messenger فقط
        if (sbn.packageName != MESSENGER_PACKAGE) {
            return
        }

        val prefs = getSharedPreferences(
            "bot_settings",
            MODE_PRIVATE
        )

        // البوت متوقف
        if (!prefs.getBoolean("enabled", false)) {
            Log.d(TAG, "⏹ Bot disabled")
            return
        }

        val reply = prefs.getString(
            "reply",
            ""
        ) ?: ""

        if (reply.isBlank()) {
            return
        }

        /*
         * نأخذ الإشعار حتى لو كان:
         * - Silent
         * - بدون صوت
         * - بدون اهتزاز
         * - مخفي بصريًا
         */

        val notification = sbn.notification

        Log.d(TAG, "================================")
        Log.d(TAG, "📩 MESSENGER NOTIFICATION")
        Log.d(TAG, "Key: ${sbn.key}")
        Log.d(TAG, "ID: ${sbn.id}")
        Log.d(TAG, "Tag: ${sbn.tag}")
        Log.d(TAG, "Flags: ${notification.flags}")

        val extras = notification.extras

        val title =
            extras.getCharSequence(Notification.EXTRA_TITLE)

        val text =
            extras.getCharSequence(Notification.EXTRA_TEXT)

        val bigText =
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)

        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Text: $text")
        Log.d(TAG, "BigText: $bigText")

        /*
         * نطبع كل Extras لمعرفة كيف يتعامل
         * Messenger مع الرسائل الصامتة.
         */
        for (key in extras.keySet()) {
            try {
                Log.d(
                    TAG,
                    "EXTRA [$key] = ${extras.get(key)}"
                )
            } catch (_: Exception) {
            }
        }

        Log.d(TAG, "================================")

        val delay = prefs.getInt(
            "delay",
            10
        ).coerceAtLeast(0)

        /*
         * نحفظ نسخة من الإشعار والرد.
         * لا نعتمد على أن sbn سيبقى صالحًا بعد عدة ثوانٍ.
         */
        handler.postDelayed({

            try {
                sendReply(
                    sbn,
                    reply
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "❌ Reply failed",
                    e
                )
            }

        }, delay * 1000L)
    }

    private fun sendReply(
        sbn: StatusBarNotification,
        text: String
    ) {

        if (sbn.packageName != MESSENGER_PACKAGE) {
            return
        }

        val notification =
            sbn.notification

        val actions =
            notification.actions

        if (actions == null || actions.isEmpty()) {

            Log.d(
                TAG,
                "❌ Messenger notification has no actions"
            )

            return
        }

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

                Log.d(
                    TAG,
                    "✅ Reply sent"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "❌ actionIntent failed",
                    e
                )
            }

            return
        }

        Log.d(
            TAG,
            "❌ No RemoteInput found"
        )
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap
    ) {

        if (sbn.packageName != MESSENGER_PACKAGE) {
            return
        }

        Log.d(
            TAG,
            "🗑 Messenger notification removed: ${sbn.key}"
        )
    }

    override fun onListenerDisconnected() {

        super.onListenerDisconnected()

        Log.d(
            TAG,
            "⚠️ BOT DISCONNECTED"
        )
    }
}

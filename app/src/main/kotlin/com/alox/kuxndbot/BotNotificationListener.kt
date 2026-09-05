package com.alox.kuxndbot

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class BotNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AloxBot"
        private const val MESSENGER_PACKAGE = "com.facebook.orca"
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "BOT CONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != MESSENGER_PACKAGE) return

        val prefs = getSharedPreferences("bot_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val reply = prefs.getString("reply", "") ?: ""
        if (reply.isBlank()) return

        val notification = sbn.notification
        val delay = prefs.getInt("delay", 10).coerceAtLeast(0)
        val notificationKey = sbn.key

        handler.postDelayed({
            try {
                var current = activeNotifications?.firstOrNull { it.key == notificationKey }
                if (current == null) {
                    current = sbn
                }

                val currentPrefs = getSharedPreferences("bot_settings", MODE_PRIVATE)
                if (!currentPrefs.getBoolean("enabled", false)) return@postDelayed

                val currentReply = currentPrefs.getString("reply", "") ?: ""
                if (currentReply.isBlank()) return@postDelayed

                sendReply(current, currentReply)

            } catch (e: Exception) {
                Log.e(TAG, "Reply failed", e)
            }
        }, delay * 1000L)
    }

    private fun sendReply(sbn: StatusBarNotification, text: String) {
        val notification = sbn.notification

        // -------------------------------------------------------------
        // التقنية 1: الأزرار المباشرة الأساسية (Standard Actions)
        // -------------------------------------------------------------
        val actions = notification.actions
        if (actions != null && actions.isNotEmpty()) {
            for ((index, action) in actions.withIndex()) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    if (sendUsingRemoteInput(action.actionIntent, remoteInputs.toList(), text, "Standard-Action-$index")) {
                        return
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // التقنية 2: الساعات الذكية (WearableExtender)
        // -------------------------------------------------------------
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        for ((wIndex, wAction) in wearableExtender.actions.withIndex()) {
            val compatInputs = wAction.remoteInputs
            if (!compatInputs.isNullOrEmpty()) {
                val nativeInputs = compatInputs.map { compatInput ->
                    RemoteInput.Builder(compatInput.resultKey)
                        .setLabel(compatInput.label)
                        .setChoices(compatInput.choices)
                        .build()
                }
                val pendingIntent = wAction.actionIntent
                if (pendingIntent != null && sendUsingRemoteInput(pendingIntent, nativeInputs, text, "Wearable-$wIndex")) {
                    return
                }
            }
        }

        // -------------------------------------------------------------
        // التقنية 3: إشعارات السيارات (CarExtender / Android Auto)
        // -------------------------------------------------------------
        val carExtender = NotificationCompat.CarExtender(notification)
        val unreadConversation = carExtender.unreadConversation
        if (unreadConversation != null) {
            val carRemoteInput = unreadConversation.remoteInput
            val carPendingIntent = unreadConversation.replyPendingIntent
            if (carRemoteInput != null && carPendingIntent != null) {
                val nativeInput = RemoteInput.Builder(carRemoteInput.resultKey)
                    .setLabel(carRemoteInput.label)
                    .setChoices(carRemoteInput.choices)
                    .build()

                if (sendUsingRemoteInput(carPendingIntent, listOf(nativeInput), text, "CarExtender")) {
                    return
                }
            }
        }

        // -------------------------------------------------------------
        // التقنية 4: الاستخراج الهيكلي المتقدم (NotificationCompat Actions Search)
        // -------------------------------------------------------------
        val compatActionsCount = NotificationCompat.getActionCount(notification)
        for (i in 0 until compatActionsCount) {
            val compatAction = NotificationCompat.getAction(notification, i)
            if (compatAction != null && compatAction.remoteInputs != null && compatAction.remoteInputs!!.isNotEmpty()) {
                val nativeInputs = compatAction.remoteInputs!!.map {
                    RemoteInput.Builder(it.resultKey)
                        .setLabel(it.label)
                        .setChoices(it.choices)
                        .build()
                }
                val pendingIntent = compatAction.actionIntent
                if (pendingIntent != null && sendUsingRemoteInput(pendingIntent, nativeInputs, text, "CompatAction-$i")) {
                    return
                }
            }
        }

        Log.d(TAG, "All reply techniques failed for key: ${sbn.key}")
    }

    private fun sendUsingRemoteInput(
        pendingIntent: PendingIntent,
        remoteInputs: List<RemoteInput>,
        text: String,
        sourceTag: String
    ): Boolean {
        if (remoteInputs.isEmpty()) return false

        try {
            val intent = Intent()
            val results = Bundle()

            for (input in remoteInputs) {
                results.putCharSequence(input.resultKey, text)
            }

            RemoteInput.addResultsToIntent(remoteInputs.toTypedArray(), intent, results)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
                } catch (e: Exception) {
                    Log.d(TAG, "Could not set RemoteInput source: ${e.message}")
                }
            }

            pendingIntent.send(this, 0, intent)
            Log.d(TAG, "SUCCESSFULLY REPLIED using method: $sourceTag")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed sending via $sourceTag", e)
        }
        return false
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "BOT DISCONNECTED")
    }
}


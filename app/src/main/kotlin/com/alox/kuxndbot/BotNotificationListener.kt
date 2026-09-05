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

        val delay = prefs.getInt("delay", 10).coerceAtLeast(0)
        val notificationKey = sbn.key

        handler.postDelayed({
            try {
                var current =
                    activeNotifications?.firstOrNull {
                        it.key == notificationKey
                    }

                if (current == null) {
                    current = sbn
                }

                val currentPrefs =
                    getSharedPreferences("bot_settings", MODE_PRIVATE)

                if (!currentPrefs.getBoolean("enabled", false)) {
                    return@postDelayed
                }

                val currentReply =
                    currentPrefs.getString("reply", "") ?: ""

                if (currentReply.isBlank()) {
                    return@postDelayed
                }

                sendReply(current, currentReply)

            } catch (e: Exception) {
                Log.e(TAG, "Reply failed", e)
            }
        }, delay * 1000L)
    }

    private fun sendReply(
        sbn: StatusBarNotification,
        text: String
    ) {
        val notification = sbn.notification

        // الطريقة المعتادة
        val actions = notification.actions

        if (actions != null && actions.isNotEmpty()) {
            for ((index, action) in actions.withIndex()) {

                val remoteInputs = action.remoteInputs

                if (
                    remoteInputs != null &&
                    remoteInputs.isNotEmpty()
                ) {
                    if (
                        sendUsingRemoteInput(
                            action.actionIntent,
                            remoteInputs.toList(),
                            text,
                            "Standard-Action-$index"
                        )
                    ) {
                        return
                    }
                }
            }
        }

        // الطريقة الإضافية: CarExtender / Android Auto
        try {
            val carExtender =
                NotificationCompat.CarExtender(notification)

            val unreadConversation =
                carExtender.unreadConversation

            if (unreadConversation != null) {

                val carRemoteInput =
                    unreadConversation.remoteInput

                val carPendingIntent =
                    unreadConversation.replyPendingIntent

                if (
                    carRemoteInput != null &&
                    carPendingIntent != null
                ) {

                    val nativeInput =
                        RemoteInput.Builder(
                            carRemoteInput.resultKey
                        )
                            .setLabel(carRemoteInput.label)
                            .setChoices(carRemoteInput.choices)
                            .build()

                    if (
                        sendUsingRemoteInput(
                            carPendingIntent,
                            listOf(nativeInput),
                            text,
                            "CarExtender"
                        )
                    ) {
                        return
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "CarExtender reply method failed",
                e
            )
        }

        Log.d(
            TAG,
            "All reply methods failed for key: ${sbn.key}"
        )
    }

    private fun sendUsingRemoteInput(
        pendingIntent: PendingIntent,
        remoteInputs: List<RemoteInput>,
        text: String,
        sourceTag: String
    ): Boolean {

        if (remoteInputs.isEmpty()) {
            return false
        }

        try {

            val intent = Intent()
            val results = Bundle()

            for (input in remoteInputs) {
                results.putCharSequence(
                    input.resultKey,
                    text
                )
            }

            RemoteInput.addResultsToIntent(
                remoteInputs.toTypedArray(),
                intent,
                results
            )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {
                try {
                    RemoteInput.setResultsSource(
                        intent,
                        RemoteInput.SOURCE_FREE_FORM_INPUT
                    )
                } catch (e: Exception) {
                    Log.d(
                        TAG,
                        "Could not set RemoteInput source: ${e.message}"
                    )
                }
            }

            pendingIntent.send(
                this,
                0,
                intent
            )

            Log.d(
                TAG,
                "SUCCESSFULLY REPLIED using method: $sourceTag"
            )

            return true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed sending via $sourceTag",
                e
            )
        }

        return false
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "BOT DISCONNECTED")
    }
}

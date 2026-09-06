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
import java.util.ArrayDeque

class BotNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AloxBot"
        private const val MESSENGER_PACKAGE = "com.facebook.orca"

        private const val MAX_QUEUE_PER_CHAT = 5
    }

    private data class ReplyItem(
        val notificationKey: String,
        val notification: StatusBarNotification
    )

    private val handler = Handler(Looper.getMainLooper())

    private val queueLock = Any()

    private val chatQueues =
        mutableMapOf<String, ArrayDeque<ReplyItem>>()

    private val processingChats =
        mutableSetOf<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()

        Log.d(
            TAG,
            "BOT CONNECTED"
        )
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        if (
            sbn.packageName !=
            MESSENGER_PACKAGE
        ) {
            return
        }

        if (!isBotEnabled()) {
            return
        }

        val prefs =
            getSharedPreferences(
                "bot_settings",
                MODE_PRIVATE
            )

        val reply =
            prefs.getString(
                "reply",
                ""
            ) ?: ""

        if (reply.isBlank()) {
            return
        }

        val chatKey =
            getChatKey(sbn)

        synchronized(queueLock) {

            val queue =
                chatQueues.getOrPut(chatKey) {
                    ArrayDeque()
                }

            if (
                queue.size >=
                MAX_QUEUE_PER_CHAT
            ) {

                Log.d(
                    TAG,
                    "Queue full for chat=$chatKey, notification ignored"
                )

                return
            }

            queue.addLast(
                ReplyItem(
                    notificationKey = sbn.key,
                    notification = sbn
                )
            )

            Log.d(
                TAG,
                "Queued message for chat=$chatKey, size=${queue.size}"
            )

            if (
                !processingChats.contains(
                    chatKey
                )
            ) {

                processingChats.add(
                    chatKey
                )

                handler.post {
                    processChatQueue(
                        chatKey
                    )
                }
            }
        }
    }

    private fun isBotEnabled(): Boolean {

        return getSharedPreferences(
            "bot_settings",
            MODE_PRIVATE
        ).getBoolean(
            "enabled",
            false
        )
    }

    private fun getChatKey(
        sbn: StatusBarNotification
    ): String {

        val notification =
            sbn.notification

        val group =
            notification.group

        if (
            !group.isNullOrBlank()
        ) {
            return "group:$group"
        }

        val title =
            notification.extras
                ?.getCharSequence(
                    Notification.EXTRA_TITLE
                )
                ?.toString()

        if (
            !title.isNullOrBlank()
        ) {
            return "title:$title"
        }

        return "notification:${sbn.id}:${sbn.tag ?: ""}"
    }

    private fun processChatQueue(
        chatKey: String
    ) {

        val item =
            synchronized(queueLock) {

                val queue =
                    chatQueues[chatKey]

                if (
                    queue == null ||
                    queue.isEmpty()
                ) {

                    chatQueues.remove(
                        chatKey
                    )

                    processingChats.remove(
                        chatKey
                    )

                    return
                }

                queue.peekFirst()
            }

        val prefs =
            getSharedPreferences(
                "bot_settings",
                MODE_PRIVATE
            )

        if (
            !prefs.getBoolean(
                "enabled",
                false
            )
        ) {

            clearChatQueue(
                chatKey
            )

            return
        }

        val text =
            prefs.getString(
                "reply",
                ""
            ) ?: ""

        if (text.isBlank()) {

            clearChatQueue(
                chatKey
            )

            return
        }

        val delaySeconds =
            prefs.getInt(
                "delay",
                10
            ).coerceAtLeast(0)

        val current =
            activeNotifications
                ?.firstOrNull {
                    it.key ==
                    item.notificationKey
                }
                ?: item.notification

        try {

            sendReply(
                current,
                text
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Reply failed",
                e
            )
        }

        synchronized(queueLock) {

            val queue =
                chatQueues[chatKey]

            if (
                queue != null &&
                queue.isNotEmpty()
            ) {

                queue.removeFirst()
            }
        }

        handler.postDelayed(
            {
                processChatQueue(
                    chatKey
                )
            },
            delaySeconds * 1000L
        )
    }

    private fun clearChatQueue(
        chatKey: String
    ) {

        synchronized(queueLock) {

            chatQueues.remove(
                chatKey
            )

            processingChats.remove(
                chatKey
            )
        }
    }

    private fun sendReply(
        sbn: StatusBarNotification,
        text: String
    ) {

        val notification =
            sbn.notification

        /*
         * METHOD 1
         * Standard Actions + RemoteInput
         */
        val actions =
            notification.actions

        if (
            actions != null &&
            actions.isNotEmpty()
        ) {

            for (
                (index, action)
                in actions.withIndex()
            ) {

                val remoteInputs =
                    action.remoteInputs

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

        /*
         * METHOD 2
         * CarExtender / Android Auto
         *
         * هذه الطريقة نجحت مع Messenger
         */
        try {

            val carExtender =
                NotificationCompat.CarExtender(
                    notification
                )

            val unreadConversation =
                carExtender.unreadConversation

            if (
                unreadConversation != null
            ) {

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
                            .setLabel(
                                carRemoteInput.label
                            )
                            .setChoices(
                                carRemoteInput.choices
                            )
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

        if (
            remoteInputs.isEmpty()
        ) {
            return false
        }

        return try {

            val intent =
                Intent()

            val results =
                Bundle()

            for (
                input in remoteInputs
            ) {

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

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed sending via $sourceTag",
                e
            )

            false
        }
    }

    override fun onListenerDisconnected() {

        super.onListenerDisconnected()

        Log.d(
            TAG,
            "BOT DISCONNECTED"
        )
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        synchronized(queueLock) {

            chatQueues.clear()
            processingChats.clear()
        }

        super.onDestroy()

        Log.d(
            TAG,
            "BOT DESTROYED"
        )
    }
}

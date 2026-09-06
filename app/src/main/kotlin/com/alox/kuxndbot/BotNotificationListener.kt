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

    private val handler = Handler(Looper.getMainLooper())

    private data class ReplyItem(
        val notification: StatusBarNotification,
        val text: String,
        val readyAt: Long
    )

    private val chatQueues =
        mutableMapOf<String, ArrayDeque<ReplyItem>>()

    private val processingChats =
        mutableSetOf<String>()

    private val queueLock = Any()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "BOT CONNECTED")
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        if (sbn.packageName != MESSENGER_PACKAGE) {
            return
        }

        val prefs =
            getSharedPreferences(
                "bot_settings",
                MODE_PRIVATE
            )

        if (!prefs.getBoolean("enabled", false)) {
            return
        }

        val reply =
            prefs.getString(
                "reply",
                ""
            ) ?: ""

        if (reply.isBlank()) {
            return
        }

        val delay =
            prefs.getInt(
                "delay",
                10
            ).coerceAtLeast(0)

        val chatKey =
            getChatKey(sbn)

        val item =
            ReplyItem(
                notification = sbn,
                text = reply,
                readyAt = System.currentTimeMillis() +
                    delay * 1000L
            )

        synchronized(queueLock) {

            val queue =
                chatQueues.getOrPut(chatKey) {
                    ArrayDeque()
                }

            if (queue.size >= MAX_QUEUE_PER_CHAT) {
                queue.removeFirst()

                Log.d(
                    TAG,
                    "Queue limit reached for chat: $chatKey"
                )
            }

            queue.addLast(item)

            Log.d(
                TAG,
                "Queued reply for chat=$chatKey " +
                    "queueSize=${queue.size}"
            )

            if (chatKey !in processingChats) {
                processingChats.add(chatKey)

                handler.post {
                    processChatQueue(chatKey)
                }
            }
        }
    }

    private fun processChatQueue(
        chatKey: String
    ) {
        val item: ReplyItem?

        synchronized(queueLock) {

            val queue =
                chatQueues[chatKey]

            if (
                queue == null ||
                queue.isEmpty()
            ) {
                chatQueues.remove(chatKey)
                processingChats.remove(chatKey)
                return
            }

            item =
                queue.peekFirst()
        }

        val now =
            System.currentTimeMillis()

        val wait =
            (item.readyAt - now).coerceAtLeast(0L)

        if (wait > 0L) {

            handler.postDelayed(
                {
                    processChatQueue(chatKey)
                },
                wait
            )

            return
        }

        synchronized(queueLock) {

            val queue =
                chatQueues[chatKey]

            if (
                queue == null ||
                queue.isEmpty()
            ) {
                processingChats.remove(chatKey)
                return
            }

            val current =
                queue.removeFirst()

            try {

                val prefs =
                    getSharedPreferences(
                        "bot_settings",
                        MODE_PRIVATE
                    )

                if (
                    prefs.getBoolean(
                        "enabled",
                        false
                    )
                ) {

                    val currentReply =
                        prefs.getString(
                            "reply",
                            ""
                        ) ?: ""

                    if (currentReply.isNotBlank()) {

                        sendReply(
                            current.notification,
                            currentReply
                        )
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Queued reply failed",
                    e
                )
            }

            if (queue.isEmpty()) {

                chatQueues.remove(chatKey)
                processingChats.remove(chatKey)

                return
            }
        }

        handler.post {
            processChatQueue(chatKey)
        }
    }

    private fun getChatKey(
        sbn: StatusBarNotification
    ): String {

        val extras =
            sbn.notification.extras

        val conversationTitle =
            try {
                extras.getCharSequence(
                    Notification.EXTRA_TITLE
                )?.toString()
            } catch (e: Exception) {
                null
            }

        val groupKey =
            try {
                sbn.notification.group
            } catch (e: Exception) {
                null
            }

        return when {
            !groupKey.isNullOrBlank() ->
                "group:$groupKey"

            !conversationTitle.isNullOrBlank() ->
                "title:$conversationTitle"

            else ->
                "fallback:${sbn.id}:${sbn.tag ?: ""}"
        }
    }

    private fun sendReply(
        sbn: StatusBarNotification,
        text: String
    ) {
        val notification =
            sbn.notification

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

        try {

            val carExtender =
                NotificationCompat.CarExtender(
                    notification
                )

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

        if (remoteInputs.isEmpty()) {
            return false
        }

        try {

            val intent =
                Intent()

            val results =
                Bundle()

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

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap
    ) {
        if (sbn.packageName != MESSENGER_PACKAGE) {
            return
        }

        Log.d(
            TAG,
            "Messenger notification removed: ${sbn.key}"
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        Log.d(
            TAG,
            "BOT DISCONNECTED"
        )

        synchronized(queueLock) {
            chatQueues.clear()
            processingChats.clear()
        }
    }
}

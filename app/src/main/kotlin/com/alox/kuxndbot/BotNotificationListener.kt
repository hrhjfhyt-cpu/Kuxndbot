package com.alox.kuxndbot

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
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
import kotlin.math.min

class BotNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AloxBot"
        private const val MESSENGER_PACKAGE = "com.facebook.orca"

        // أقصى عدد رسائل تنتظر في كل محادثة
        private const val MAX_QUEUE_PER_CHAT = 5

        // Smart Wake:
        // نبدأ بعد فترة خمول طويلة، ثم نزيد الفاصل تدريجيًا
        private const val INITIAL_IDLE_CHECK = 5 * 60 * 1000L
        private const val FIRST_WAKE_DELAY = 5 * 60 * 1000L
        private const val MAX_WAKE_DELAY = 30 * 60 * 1000L

        private const val MESSENGER_FBNS_ACTION =
            "com.facebook.orca.fbns.ACTION_RECEIVE"

        private const val MESSENGER_FBNS_RECEIVER =
            "com.facebook.push.fbns.FbnsCallbackReceiver"

        private const val MESSENGER_PERF_RECEIVER =
            "com.facebook.messaging.analytics.perf.MessagingPerformanceLogger\$Receiver"
    }

    private data class ReplyItem(
        val notificationKey: String,
        val notification: StatusBarNotification
    )

    private val handler = Handler(Looper.getMainLooper())

    private val queueLock = Any()

    // Queue مستقلة لكل محادثة
    private val chatQueues =
        mutableMapOf<String, ArrayDeque<ReplyItem>>()

    // المحادثات التي لديها Worker قيد التشغيل
    private val processingChats =
        mutableSetOf<String>()

    // -------------------------------------------------------------
    // Smart Messenger Wake
    // -------------------------------------------------------------

    private var smartWakeRunning = false
    private var messengerLastActivity = 0L
    private var nextWakeDelay = FIRST_WAKE_DELAY

    private val smartWakeRunnable =
        object : Runnable {
            override fun run() {

                if (!isBotEnabled()) {
                    stopSmartWake()
                    return
                }

                val now = System.currentTimeMillis()
                val idleTime =
                    now - messengerLastActivity

                /*
                 * إذا كان Messenger نشطًا مؤخرًا:
                 * لا نحاول إيقاظه.
                 */
                if (idleTime < INITIAL_IDLE_CHECK) {

                    handler.postDelayed(
                        this,
                        INITIAL_IDLE_CHECK - idleTime
                    )

                    return
                }

                /*
                 * Messenger خامل لفترة طويلة.
                 * نجرب Wake واحد فقط.
                 */
                wakeMessenger()

                /*
                 * لا نكرر المحاولة بسرعة.
                 * كل فشل يجعل الفاصل أطول حتى 30 دقيقة.
                 */
                nextWakeDelay =
                    min(
                        nextWakeDelay * 2,
                        MAX_WAKE_DELAY
                    )

                handler.postDelayed(
                    this,
                    nextWakeDelay
                )
            }
        }

    override fun onListenerConnected() {
        super.onListenerConnected()

        Log.d(TAG, "BOT CONNECTED")

        messengerLastActivity =
            System.currentTimeMillis()

        nextWakeDelay =
            FIRST_WAKE_DELAY

        startSmartWake()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        if (sbn.packageName != MESSENGER_PACKAGE) {
            return
        }

        /*
         * أي نشاط حقيقي من Messenger يعتبر دليلًا
         * أن Messenger حي.
         */
        messengerLastActivity =
            System.currentTimeMillis()

        /*
         * بمجرد عودة نشاط Messenger:
         * نعيد دورة Smart Wake من البداية.
         */
        nextWakeDelay =
            FIRST_WAKE_DELAY

        if (!isBotEnabled()) {
            stopSmartWake()
            return
        }

        startSmartWake()

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

        val chatKey =
            getChatKey(sbn)

        synchronized(queueLock) {

            val queue =
                chatQueues.getOrPut(chatKey) {
                    ArrayDeque()
                }

            /*
             * لا نسمح بتراكم أكثر من 5 رسائل
             * في نفس المحادثة.
             */
            if (queue.size >= MAX_QUEUE_PER_CHAT) {

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

            /*
             * Worker واحد فقط لكل محادثة.
             */
            if (!processingChats.contains(chatKey)) {

                processingChats.add(chatKey)

                handler.post {
                    processChatQueue(chatKey)
                }
            }
        }
    }

    // =============================================================
    // Smart Wake control
    // =============================================================

    private fun isBotEnabled(): Boolean {

        return getSharedPreferences(
            "bot_settings",
            MODE_PRIVATE
        ).getBoolean(
            "enabled",
            false
        )
    }

    private fun startSmartWake() {

        if (!isBotEnabled()) {
            return
        }

        if (smartWakeRunning) {
            return
        }

        smartWakeRunning = true

        messengerLastActivity =
            System.currentTimeMillis()

        nextWakeDelay =
            FIRST_WAKE_DELAY

        handler.removeCallbacks(
            smartWakeRunnable
        )

        handler.postDelayed(
            smartWakeRunnable,
            INITIAL_IDLE_CHECK
        )

        Log.d(
            TAG,
            "Smart Messenger Wake started"
        )
    }

    private fun stopSmartWake() {

        smartWakeRunning = false

        handler.removeCallbacks(
            smartWakeRunnable
        )

        nextWakeDelay =
            FIRST_WAKE_DELAY

        Log.d(
            TAG,
            "Smart Messenger Wake stopped"
        )
    }

    /**
     * يحاول إحياء Messenger في الخلفية فقط.
     *
     * لا يفتح Messenger UI.
     *
     * نجرب أكثر من Component بالتتابع.
     */
    private fun wakeMessenger() {

        if (!isBotEnabled()) {
            return
        }

        /*
         * ---------------------------------------------------------
         * المحاولة 1:
         * FBNS Callback Receiver
         * ---------------------------------------------------------
         */
        try {

            val fbnsIntent =
                Intent(
                    MESSENGER_FBNS_ACTION
                ).apply {

                    component =
                        ComponentName(
                            MESSENGER_PACKAGE,
                            MESSENGER_FBNS_RECEIVER
                        )

                    setPackage(
                        MESSENGER_PACKAGE
                    )
                }

            sendBroadcast(
                fbnsIntent
            )

        } catch (e: SecurityException) {

            Log.d(
                TAG,
                "FBNS Wake rejected"
            )

        } catch (e: Exception) {

            Log.d(
                TAG,
                "FBNS Wake failed"
            )
        }

        /*
         * ---------------------------------------------------------
         * المحاولة 2:
         * Performance Logger Receiver
         * ---------------------------------------------------------
         */
        try {

            val perfIntent =
                Intent().apply {

                    component =
                        ComponentName(
                            MESSENGER_PACKAGE,
                            MESSENGER_PERF_RECEIVER
                        )

                    setPackage(
                        MESSENGER_PACKAGE
                    )
                }

            sendBroadcast(
                perfIntent
            )

        } catch (e: SecurityException) {

            Log.d(
                TAG,
                "Performance Wake rejected"
            )

        } catch (e: Exception) {

            Log.d(
                TAG,
                "Performance Wake failed"
            )
        }

        /*
         * نعتبر محاولة Wake نشاطًا مساعدًا فقط.
         *
         * لا نفتح Messenger ولا نشغل Activity.
         */
        messengerLastActivity =
            System.currentTimeMillis()
    }

    // =============================================================
    // Queue
    // =============================================================

    private fun getChatKey(
        sbn: StatusBarNotification
    ): String {

        val notification =
            sbn.notification

        val group =
            notification.group

        if (!group.isNullOrBlank()) {
            return "group:$group"
        }

        val title =
            notification.extras?.getCharSequence(
                Notification.EXTRA_TITLE
            )?.toString()

        if (!title.isNullOrBlank()) {
            return "title:$title"
        }

        return "notification:${sbn.id}:${sbn.tag ?: ""}"
    }

    /**
     * Worker واحد لكل محادثة.
     *
     * بعد كل محاولة إرسال:
     * ننتظر مدة التأخير كاملة قبل الرسالة التالية.
     */
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

        if (!prefs.getBoolean("enabled", false)) {

            synchronized(queueLock) {

                chatQueues.remove(
                    chatKey
                )

                processingChats.remove(
                    chatKey
                )
            }

            return
        }

        val text =
            prefs.getString(
                "reply",
                ""
            ) ?: ""

        if (text.isBlank()) {

            synchronized(queueLock) {

                chatQueues.remove(
                    chatKey
                )

                processingChats.remove(
                    chatKey
                )
            }

            return
        }

        val delaySeconds =
            prefs.getInt(
                "delay",
                10
            ).coerceAtLeast(0)

        val current =
            activeNotifications?.firstOrNull {
                it.key == item.notificationKey
            } ?: item.notification

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

        /*
         * حذف الرسالة التي تمت معالجتها.
         */
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

        /*
         * لا نرسل الرسالة التالية مباشرة.
         *
         * ننتظر مدة التأخير كاملة بعد كل رد.
         */
        val nextDelay =
            delaySeconds * 1000L

        handler.postDelayed(
            {
                processChatQueue(
                    chatKey
                )
            },
            nextDelay
        )
    }

    // =============================================================
    // Reply
    // =============================================================

    private fun sendReply(
        sbn: StatusBarNotification,
        text: String
    ) {

        val notification =
            sbn.notification

        // =========================================================
        // الطريقة 1:
        // Standard Actions + RemoteInput
        // =========================================================

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

        // =========================================================
        // الطريقة 2:
        // CarExtender / Android Auto
        // =========================================================

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

        if (remoteInputs.isEmpty()) {
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

    // =============================================================
    // Lifecycle
    // =============================================================

    override fun onListenerDisconnected() {

        super.onListenerDisconnected()

        stopSmartWake()

        Log.d(
            TAG,
            "BOT DISCONNECTED"
        )
    }

    override fun onDestroy() {

        stopSmartWake()

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

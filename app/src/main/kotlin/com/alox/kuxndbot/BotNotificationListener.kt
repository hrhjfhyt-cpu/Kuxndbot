package com.alox.kuxndbot

import android.app.Notification
import android.app.PendingIntent
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

        try {
            activeNotifications
                ?.filter {
                    it.packageName == MESSENGER_PACKAGE
                }
                ?.forEach {
                    Log.d(
                        TAG,
                        "📩 Existing Messenger notification: ${it.key}"
                    )
                }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        if (sbn.packageName != MESSENGER_PACKAGE) {
            return
        }

        val prefs = getSharedPreferences(
            "bot_settings",
            MODE_PRIVATE
        )

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

        val notification = sbn.notification
        val extras = notification.extras

        Log.d(TAG, "================================")
        Log.d(TAG, "📩 MESSENGER NOTIFICATION")
        Log.d(TAG, "Key: ${sbn.key}")
        Log.d(TAG, "ID: ${sbn.id}")
        Log.d(TAG, "Tag: ${sbn.tag}")
        Log.d(TAG, "Flags: ${notification.flags}")
        Log.d(TAG, "Category: ${notification.category}")
        Log.d(TAG, "Channel: ${sbn.notification.channelId}")

        // =========================
        // كل البيانات النصية المتاحة
        // =========================

        logExtra(
            extras,
            Notification.EXTRA_TITLE
        )

        logExtra(
            extras,
            Notification.EXTRA_TEXT
        )

        logExtra(
            extras,
            Notification.EXTRA_BIG_TEXT
        )

        logExtra(
            extras,
            Notification.EXTRA_SUB_TEXT
        )

        logExtra(
            extras,
            Notification.EXTRA_INFO_TEXT
        )

        // =========================
        // فحص كل Extras
        // =========================

        for (key in extras.keySet()) {
            try {
                val value = extras.get(key)

                Log.d(
                    TAG,
                    "EXTRA [$key] = ${describeValue(value)}"
                )

            } catch (e: Exception) {
                Log.d(
                    TAG,
                    "EXTRA [$key] = <unreadable>"
                )
            }
        }

        // =========================
        // فحص Actions
        // =========================

        val actions = notification.actions

        if (actions == null || actions.isEmpty()) {

            Log.d(
                TAG,
                "⚠️ No notification actions"
            )

        } else {

            Log.d(
                TAG,
                "🔎 Actions count: ${actions.size}"
            )

            actions.forEachIndexed { index, action ->

                Log.d(
                    TAG,
                    "ACTION[$index] title=${action.title}"
                )

                Log.d(
                    TAG,
                    "ACTION[$index] semanticAction=${action.semanticAction}"
                )

                Log.d(
                    TAG,
                    "ACTION[$index] showsUserInterface=${action.showsUserInterface}"
                )

                val remoteInputs =
                    action.remoteInputs

                if (
                    remoteInputs == null ||
                    remoteInputs.isEmpty()
                ) {

                    Log.d(
                        TAG,
                        "ACTION[$index] has no RemoteInput"
                    )

                } else {

                    Log.d(
                        TAG,
                        "ACTION[$index] RemoteInputs=${remoteInputs.size}"
                    )

                    remoteInputs.forEachIndexed { riIndex, input ->

                        Log.d(
                            TAG,
                            "REMOTE_INPUT[$index][$riIndex] " +
                                "resultKey=${input.resultKey}, " +
                                "label=${input.label}, " +
                                "allowFreeForm=${input.allowFreeFormInput}"
                        )
                    }
                }

                try {

                    Log.d(
                        TAG,
                        "ACTION[$index] intent=${
                            action.actionIntent
                        }"
                    )

                } catch (_: Exception) {
                }
            }
        }

        Log.d(TAG, "================================")

        val delay = prefs.getInt(
            "delay",
            10
        ).coerceAtLeast(0)

        /*
         * نحتفظ بنفس فكرة التأخير.
         *
         * نستخدم key للإشعار بدل الاعتماد على
         * كائن sbn القديم بعد مرور الوقت.
         */
        val notificationKey = sbn.key

        handler.postDelayed({

            try {

                val current =
                    activeNotifications
                        ?.firstOrNull {
                            it.key == notificationKey
                        }

                if (current == null) {

                    Log.d(
                        TAG,
                        "⚠️ Notification no longer active: $notificationKey"
                    )

                    /*
                     * لا نرسل من إشعار قديم.
                     */
                    return@postDelayed
                }

                sendReply(
                    current,
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

    // =========================
    // إرسال الرد
    // =========================

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

        if (
            actions == null ||
            actions.isEmpty()
        ) {

            Log.d(
                TAG,
                "❌ No actions available for reply"
            )

            return
        }

        /*
         * نبحث في جميع Actions.
         *
         * لا نكتفي بأول Action لا يحتوي
         * RemoteInput.
         */
        for ((index, action) in actions.withIndex()) {

            val remoteInputs =
                action.remoteInputs

            if (
                remoteInputs == null ||
                remoteInputs.isEmpty()
            ) {

                Log.d(
                    TAG,
                    "ACTION[$index] skipped: no RemoteInput"
                )

                continue
            }

            val usableInputs =
                remoteInputs.filter {
                    it.allowFreeFormInput
                }

            val inputs =
                if (usableInputs.isNotEmpty()) {
                    usableInputs
                } else {
                    remoteInputs.toList()
                }

            if (inputs.isEmpty()) {
                continue
            }

            val intent = Intent()

            val results = Bundle()

            for (input in inputs) {

                results.putCharSequence(
                    input.resultKey,
                    text
                )
            }

            /*
             * الطريقة الأصلية تبقى كما هي:
             *
             * RemoteInput
             *       ↓
             * actionIntent
             */
            RemoteInput.addResultsToIntent(
                inputs.toTypedArray(),
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
                    "================================"
                )

                Log.d(
                    TAG,
                    "✅ REPLY SENT"
                )

                Log.d(
                    TAG,
                    "Action index: $index"
                )

                Log.d(
                    TAG,
                    "================================"
                )

                return

            } catch (e: PendingIntent.CanceledException) {

                Log.e(
                    TAG,
                    "❌ PendingIntent canceled for ACTION[$index]",
                    e
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "❌ ACTION[$index] failed",
                    e
                )
            }
        }

        /*
         * لا يوجد RemoteInput قابل للاستخدام.
         *
         * مهم:
         * لا نحذف الإشعار،
         * لا نفتح Messenger،
         * ولا نستخدم Accessibility.
         *
         * نسجل فقط أن هذا النوع لا يوفر
         * قناة Reply يمكن لـNotificationListener
         * استخدامها.
         */
        Log.d(
            TAG,
            "⚠️ Messenger notification has no usable RemoteInput"
        )

        Log.d(
            TAG,
            "ℹ️ No direct notification reply channel exposed"
        )
    }

    // =========================
    // Logging helpers
    // =========================

    private fun logExtra(
        extras: Bundle,
        key: String
    ) {

        try {

            val value =
                extras.getCharSequence(key)

            if (value != null) {

                Log.d(
                    TAG,
                    "$key = $value"
                )
            }

        } catch (_: Exception) {
        }
    }

    private fun describeValue(
        value: Any?
    ): String {

        return when (value) {

            null ->
                "null"

            is Bundle ->
                "Bundle(${value.keySet().joinToString(",")})"

            is Array<*> ->
                "Array(size=${value.size})"

            is IntArray ->
                "IntArray(size=${value.size})"

            is LongArray ->
                "LongArray(size=${value.size})"

            is CharSequence ->
                value.toString()

            else ->
                value.toString()
        }
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

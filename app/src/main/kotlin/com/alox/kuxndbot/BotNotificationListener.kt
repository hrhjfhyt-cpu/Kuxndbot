Enterpackage com.alox.kuxndbot

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

        Log.d(TAG, "================================")
        Log.d(TAG, "BOT CONNECTED")
        Log.d(TAG, "================================")

        try {
            activeNotifications
                ?.filter { it.packageName == MESSENGER_PACKAGE }
                ?.forEach {
                    Log.d(
                        TAG,
                        "Existing Messenger notification: ${it.key}"
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading active notifications", e)
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
            Log.d(TAG, "Bot disabled")
            return
        }

        val reply = prefs.getString(
            "reply",
            ""
        ) ?: ""

        if (reply.isBlank()) {
            Log.d(TAG, "Reply text is empty")
            return
        }

        val notification = sbn.notification
        val extras = notification.extras

        Log.d(TAG, "================================")
        Log.d(TAG, "MESSENGER NOTIFICATION")
        Log.d(TAG, "Key: ${sbn.key}")
        Log.d(TAG, "ID: ${sbn.id}")
        Log.d(TAG, "Tag: ${sbn.tag}")
        Log.d(TAG, "Flags: ${notification.flags}")
        Log.d(TAG, "Category: ${notification.category}")
        Log.d(TAG, "Channel: ${sbn.notification.channelId}")

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

        val actions = notification.actions

        if (actions == null || actions.isEmpty()) {

            Log.d(
                TAG,
                "No notification actions"
            )

        } else {

            Log.d(
                TAG,
                "Actions count: ${actions.size}"
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

                val remoteInputs = action.remoteInputs

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
                        "ACTION[$index] intent=${action.actionIntent}"
                    )
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Could not read action intent",
                        e
                    )
                }
            }
        }

        Log.d(TAG, "================================")

        val delay = prefs.getInt(
            "delay",
            10
        ).coerceAtLeast(0)

        val notificationKey = sbn.key

        handler.postDelayed({

            try {

                var current =
                    activeNotifications
                        ?.firstOrNull {
                            it.key == notificationKey
                        }

                if (current == null) {
                    Log.d(
                        TAG,
                        "Notification no longer active in list, using original SBN for Vanish/Temporary message: $notificationKey"
                    )
                    current = sbn
                }

                val currentPrefs =
                    getSharedPreferences(
                        "bot_settings",
                        MODE_PRIVATE
                    )

                if (
                    !currentPrefs.getBoolean(
                        "enabled",
                        false
                    )
                ) {

                    Log.d(
                        TAG,
                        "Bot disabled before reply"
                    )

                    return@postDelayed
                }

                val currentReply =
                    currentPrefs.getString(
                        "reply",
                        ""
                    ) ?: ""

                if (currentReply.isBlank()) {
                    return@postDelayed
                }

                sendReply(
                    current,
                    currentReply
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Reply failed",
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

        val actions = notification.actions

        if (actions != null && actions.isNotEmpty()) {
            for ((index, action) in actions.withIndex()) {

                val remoteInputs = action.remoteInputs

                if (remoteInputs == null || remoteInputs.isEmpty()) {
                    Log.d(TAG, "ACTION[$index] skipped: no RemoteInput")
                    continue
                }

                val usableInputs = remoteInputs.filter { it.allowFreeFormInput }

                val inputs = if (usableInputs.isNotEmpty()) {
                    usableInputs
                } else {
                    remoteInputs.toList()
                }

                if (inputs.isEmpty()) {
                    continue
                }

                if (sendUsingRemoteInput(action, inputs, text, index)) {
                    return
                }
            }
        }

        Log.d(TAG, "Searching for WearableExtender RemoteInputs (Vanish Mode Fallback)...")
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        val wearableActions = wearableExtender.actions

        if (wearableActions.isNotEmpty()) {
            for ((wIndex, wAction) in wearableActions.withIndex()) {
                val compatInputs = wAction.remoteInputs
                if (compatInputs != null && compatInputs.isNotEmpty()) {
                    val nativeInputs = compatInputs.map { compatInput ->
                        RemoteInput.Builder(compatInput.resultKey)
                            .setLabel(compatInput.label)
                            .setChoices(compatInput.choices)
                            .build()
                    }

                    val pendingIntent = wAction.actionIntent
                    if (pendingIntent != null && sendUsingCompatAction(pendingIntent, nativeInputs, text, wIndex)) {
                        Log.d(TAG, "Replied successfully via WearableExtender!")
                        return
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Messenger notification has no usable RemoteInput"
        )

        Log.d(
            TAG,
            "No direct notification reply channel exposed"
        )
    }

    private fun sendUsingRemoteInput(
        action: Notification.Action,
        remoteInputs: List<RemoteInput>,
        text: String,
        actionIndex: Int
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

                    Log.d(
                        TAG,
                        "RemoteInput source = FREE_FORM_INPUT"
                    )

                } catch (e: Exception) {

                    Log.d(
                        TAG,
                        "Could not set RemoteInput source"
                    )
                }
            }

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
                "REPLY SENT"
            )

            Log.d(
                TAG,
                "Action index: $actionIndex"
            )

            Log.d(
                TAG,
                "RemoteInputs: ${remoteInputs.size}"
            )

            Log.d(
                TAG,
                "================================"
            )

            return true

        } catch (
            e: PendingIntent.CanceledException
        ) {

            Log.e(
                TAG,
                "PendingIntent canceled: ACTION[$actionIndex]",
                e
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "ACTION[$actionIndex] failed",
                e
            )
        }

        return false
    }

    private fun sendUsingCompatAction(
        pendingIntent: PendingIntent,
        remoteInputs: List<RemoteInput>,
        text: String,
        actionIndex: Int
    ): Boolean {
        if (remoteInputs.isEmpty()) return false

        try {
            val intent = Intent()
            val results = Bundle()

            for (input in remoteInputs) {
                results.putCharSequence(input.resultKey, text)
            }

            RemoteInput.addResultsToIntent(
                remoteInputs.toTypedArray(),
                intent,
                results
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
                } catch (e: Exception) {
                    Log.d(TAG, "Could not set RemoteInput source")
                }
            }

            pendingIntent.send(this, 0, intent)

            Log.d(TAG, "================================")
            Log.d(TAG, "REPLY SENT VIA WEARABLE FALLBACK")
            Log.d(TAG, "Action index: $actionIndex")
            Log.d(TAG, "================================")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Wearable Action[$actionIndex] failed", e)
        }
        return false
    }

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

        } catch (e: Exception) {

            Log.d(
                TAG,
                "$key = <unreadable>"
            )
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
            "Messenger notification removed: ${sbn.key}"
        )
    }

    override fun onListenerDisconnected() {

        super.onListenerDisconnected()

        Log.d(
            TAG,
            "BOT DISCONNECTED"
        )
    }
}

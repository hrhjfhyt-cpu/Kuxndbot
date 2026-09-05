package com.alox.kuxndbot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlin.math.max
import kotlin.math.min

class FloatingWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_bot"
        private const val NOTIFICATION_ID = 9911

        private const val MIN_WIDTH = 260
        private const val MIN_HEIGHT = 220
        private const val BUBBLE_SIZE = 64
    }

    private val prefs by lazy {
        getSharedPreferences(
            "bot_settings",
            MODE_PRIVATE
        )
    }

    private lateinit var windowManager: WindowManager

    private var windowView: View? = null
    private var bubbleView: TextView? = null

    private var windowParams: WindowManager.LayoutParams? = null

    private var windowWidth = 330
    private var windowHeight = 480

    private val purple =
        Color.rgb(124, 58, 237)

    private val blue =
        Color.rgb(59, 130, 246)

    private val bgColor =
        Color.rgb(9, 10, 20)

    private val cardColor =
        Color.rgb(22, 25, 39)

    private val fieldColor =
        Color.rgb(13, 17, 30)

    private val white =
        Color.WHITE

    private val gray =
        Color.rgb(170, 174, 190)

    override fun onCreate() {
        super.onCreate()

        windowManager =
            getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        createNotificationChannel()

        try {
            startForeground(
                NOTIFICATION_ID,
                createNotification()
            )
        } catch (_: Exception) {
        }

        if (Settings.canDrawOverlays(this)) {
            showWindow()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (windowView == null &&
            bubbleView == null &&
            Settings.canDrawOverlays(this)
        ) {
            showWindow()
        }

        return START_STICKY
    }

    // =====================================================
    // MAIN FLOATING WINDOW
    // =====================================================

    private fun showWindow() {

        if (windowView != null) {
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    14,
                    12,
                    14,
                    12
                )

                background =
                    roundedBackground(
                        bgColor,
                        22
                    )
            }

        // =========================
        // TOP BAR
        // =========================

        val topBar =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val title =
            TextView(this).apply {

                text =
                    "Alox Bot 👑"

                textSize =
                    19f

                setTextColor(
                    white
                )
            }

        topBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val minimize =
            TextView(this).apply {

                text =
                    "—"

                textSize =
                    27f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    white
                )

                setPadding(
                    12,
                    0,
                    12,
                    0
                )

                setOnClickListener {
                    collapseToBubble()
                }
            }

        topBar.addView(
            minimize,
            LinearLayout.LayoutParams(
                48,
                48
            )
        )

        val close =
            TextView(this).apply {

                text =
                    "×"

                textSize =
                    27f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    white
                )

                setPadding(
                    8,
                    0,
                    8,
                    0
                )

                setOnClickListener {
                    stopSelf()
                }
            }

        topBar.addView(
            close,
            LinearLayout.LayoutParams(
                48,
                48
            )
        )

        root.addView(
            topBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            )
        )

        // =========================
        // BOT STATUS
        // =========================

        val statusRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    4,
                    8,
                    4,
                    8
                )
            }

        val statusText =
            TextView(this).apply {

                text =
                    "Bot enabled"

                textSize =
                    17f

                setTextColor(
                    white
                )
            }

        statusRow.addView(
            statusText,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val botSwitch =
            Switch(this).apply {

                isChecked =
                    prefs.getBoolean(
                        "enabled",
                        false
                    )

                text =
                    if (isChecked)
                        "ON"
                    else
                        "OFF"

                setTextColor(
                    white
                )

                setOnCheckedChangeListener {
                        _,
                        checked ->

                    text =
                        if (checked)
                            "ON"
                        else
                            "OFF"

                    prefs.edit()
                        .putBoolean(
                            "enabled",
                            checked
                        )
                        .apply()
                }
            }

        statusRow.addView(
            botSwitch
        )

        root.addView(
            statusRow
        )

        // =========================
        // DELAY
        // =========================

        val delayLabel =
            smallLabel(
                "Delay (seconds)"
            )

        root.addView(
            delayLabel
        )

        val delayInput =
            editField()

        delayInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        delayInput.setText(
            prefs.getInt(
                "delay",
                10
            ).toString()
        )

        root.addView(
            delayInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52
            )
        )

        // =========================
        // REPLY
        // =========================

        val replyLabel =
            smallLabel(
                "Reply text"
            )

        root.addView(
            replyLabel
        )

        val replyInput =
            editField()

        replyInput.minLines = 3
        replyInput.maxLines = 5

        replyInput.gravity =
            Gravity.TOP or
            Gravity.START

        replyInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

        replyInput.setText(
            prefs.getString(
                "reply",
                "مرحباً، سأرد عليك لاحقاً."
            )
        )

        root.addView(
            replyInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100
            )
        )

        // =========================
        // SAVE
        // =========================

        val save =
            Button(this).apply {

                text =
                    "💾 SAVE"

                textSize =
                    14f

                setTextColor(
                    white
                )

                background =
                    roundedBackground(
                        purple,
                        16
                    )

                setOnClickListener {

                    val delay =
                        delayInput.text
                            .toString()
                            .toIntOrNull()
                            ?: 10

                    prefs.edit()
                        .putBoolean(
                            "enabled",
                            botSwitch.isChecked
                        )
                        .putInt(
                            "delay",
                            delay.coerceAtLeast(0)
                        )
                        .putString(
                            "reply",
                            replyInput.text.toString()
                        )
                        .putString(
                            "package",
                            "com.facebook.orca"
                        )
                        .apply()

                    Toast.makeText(
                        this@FloatingWindowService,
                        "Saved ✅",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        root.addView(
            save,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50
            )
        )

        // =========================
        // RESIZE HANDLE
        // =========================

        val resize =
            TextView(this).apply {

                text =
                    "↘"

                textSize =
                    22f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    gray
                )

                setPadding(
                    8,
                    0,
                    0,
                    0
                )
            }

        root.addView(
            resize,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                36
            )
        )

        // =========================
        // WINDOW PARAMS
        // =========================

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or Gravity.START

        params.x = 25
        params.y = 120

        windowParams =
            params

        // =========================
        // DRAG WINDOW
        // =========================

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0

        topBar.setOnTouchListener { _, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    downX =
                        event.rawX

                    downY =
                        event.rawY

                    startX =
                        params.x

                    startY =
                        params.y

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    params.x =
                        startX +
                            (
                                event.rawX -
                                    downX
                            ).toInt()

                    params.y =
                        startY +
                            (
                                event.rawY -
                                    downY
                            ).toInt()

                    try {
                        windowManager.updateViewLayout(
                            root,
                            params
                        )
                    } catch (_: Exception) {
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {

                    // إذا اقتربت النافذة من طرف الشاشة
                    // تتحول إلى فقاعة.
                    if (isNearEdge(params)) {
                        collapseToBubble()
                    }

                    true
                }

                else -> false
            }
        }

        // =========================
        // RESIZE
        // =========================

        var resizeDownX = 0f
        var resizeDownY = 0f
        var resizeStartWidth = 0
        var resizeStartHeight = 0

        resize.setOnTouchListener { _, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    resizeDownX =
                        event.rawX

                    resizeDownY =
                        event.rawY

                    resizeStartWidth =
                        params.width

                    resizeStartHeight =
                        params.height

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val newWidth =
                        resizeStartWidth +
                            (
                                event.rawX -
                                    resizeDownX
                            ).toInt()

                    val newHeight =
                        resizeStartHeight +
                            (
                                event.rawY -
                                    resizeDownY
                            ).toInt()

                    params.width =
                        max(
                            MIN_WIDTH,
                            newWidth
                        )

                    params.height =
                        max(
                            MIN_HEIGHT,
                            newHeight
                        )

                    windowWidth =
                        params.width

                    windowHeight =
                        params.height

                    try {
                        windowManager.updateViewLayout(
                            root,
                            params
                        )
                    } catch (_: Exception) {
                    }

                    true
                }

                else -> true
            }
        }

        try {

            windowManager.addView(
                root,
                params
            )

            windowView =
                root

        } catch (_: Exception) {

            stopSelf()
        }
    }

    // =====================================================
    // BUBBLE
    // =====================================================

    private fun collapseToBubble() {

        val oldView =
            windowView

        if (oldView != null) {

            try {
                windowManager.removeView(
                    oldView
                )
            } catch (_: Exception) {
            }

            windowView =
                null
        }

        if (bubbleView != null) {
            return
        }

        val bubble =
            TextView(this).apply {

                text =
                    "👑"

                textSize =
                    27f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    white
                )

                background =
                    roundedBackground(
                        purple,
                        100
                    )

                setOnClickListener {
                    expandFromBubble()
                }
            }

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager.LayoutParams(
                BUBBLE_SIZE,
                BUBBLE_SIZE,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or Gravity.START

        params.x =
            if (windowParams != null)
                windowParams!!.x
            else
                10

        params.y =
            if (windowParams != null)
                windowParams!!.y
            else
                200

        // ضعها على أقرب طرف
        val screenWidth =
            resources.displayMetrics.widthPixels

        if (params.x <
            screenWidth / 2
        ) {
            params.x = 5
        } else {
            params.x =
                screenWidth -
                    BUBBLE_SIZE -
                    5
        }

        try {

            windowManager.addView(
                bubble,
                params
            )

            bubbleView =
                bubble

        } catch (_: Exception) {
        }
    }

    // =====================================================
    // EXPAND
    // =====================================================

    private fun expandFromBubble() {

        val bubble =
            bubbleView

        if (bubble != null) {

            try {
                windowManager.removeView(
                    bubble
                )
            } catch (_: Exception) {
            }

            bubbleView =
                null
        }

        showWindow()
    }

    // =====================================================
    // EDGE DETECTION
    // =====================================================

    private fun isNearEdge(
        params: WindowManager.LayoutParams
    ): Boolean {

        val width =
            resources.displayMetrics.widthPixels

        val height =
            resources.displayMetrics.heightPixels

        val edge =
            35

        return params.x <= edge ||
            params.x +
                params.width >=
                width - edge ||
            params.y <= edge ||
            params.y +
                params.height >=
                height - edge
    }

    // =====================================================
    // FOREGROUND NOTIFICATION
    // =====================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Alox Floating Bot",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Keeps the floating bot service running"

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification():
            Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "Alox Dashboard Bot"
                )
                .setContentText(
                    "Floating window is active"
                )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentIntent(
                    pendingIntent
                )
                .setOngoing(true)
                .build()

        } else {

            Notification.Builder(this)
                .setContentTitle(
                    "Alox Dashboard Bot"
                )
                .setContentText(
                    "Floating window is active"
                )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentIntent(
                    pendingIntent
                )
                .setOngoing(true)
                .build()
        }
    }

    // =====================================================
    // UI HELPERS
    // =====================================================

    private fun smallLabel(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text =
                text

            textSize =
                14f

            setTextColor(
                gray
            )

            setPadding(
                4,
                8,
                4,
                5
            )
        }
    }

    private fun editField():
            EditText {

        return EditText(this).apply {

            textSize =
                16f

            setTextColor(
                white
            )

            setHintTextColor(
                gray
            )

            setPadding(
                14,
                4,
                14,
                4
            )

            background =
                roundedBackground(
                    fieldColor,
                    15
                )
        }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(
                color
            )

            cornerRadius =
                radiusDp.toFloat() *
                    resources
                        .displayMetrics
                        .density

            setStroke(
                (
                    1 *
                        resources
                            .displayMetrics
                            .density
                    ).toInt(),
                Color.rgb(
                    42,
                    46,
                    65
                )
            )
        }
    }

    override fun onDestroy() {

        val view =
            windowView

        if (view != null) {

            try {
                windowManager.removeView(
                    view
                )
            } catch (_: Exception) {
            }

            windowView =
                null
        }

        val bubble =
            bubbleView

        if (bubble != null) {

            try {
                windowManager.removeView(
                    bubble
                )
            } catch (_: Exception) {
            }

            bubbleView =
                null
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}

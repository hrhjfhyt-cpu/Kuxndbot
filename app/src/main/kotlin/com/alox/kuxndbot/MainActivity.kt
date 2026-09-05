package com.alox.kuxndbot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private lateinit var botSwitch: Switch
    private lateinit var delayInput: EditText
    private lateinit var replyInput: EditText

    private val prefs by lazy {
        getSharedPreferences("bot_settings", MODE_PRIVATE)
    }

    private val bgColor = Color.rgb(9, 10, 20)
    private val cardColor = Color.rgb(22, 25, 39)
    private val fieldColor = Color.rgb(13, 17, 30)
    private val purple = Color.rgb(124, 58, 237)
    private val blue = Color.rgb(59, 130, 246)
    private val white = Color.WHITE
    private val gray = Color.rgb(170, 174, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildDashboard()
    }

    private fun buildDashboard() {

        /*
         * الصفحة الرئيسية قابلة للتمرير فقط بمقدار
         * المحتوى الموجود فعلياً، وليست Scroll لا نهائية.
         */
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 28)
        }

        // =========================
        // HEADER
        // =========================

        val title = textView(
            "Alox Dashboard Bot 👑",
            27f,
            white
        ).apply {
            gravity = Gravity.CENTER
        }

        val subtitle = textView(
            "Astro Kings 🛑",
            20f,
            white
        ).apply {
            gravity = Gravity.CENTER
        }

        val settingsTitle = textView(
            "Bot settings 🔵",
            23f,
            white
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }

        page.addView(title)
        page.addView(subtitle)
        page.addView(settingsTitle)

        // =========================
        // BOT STATUS CARD
        // =========================

        val statusCard = card()

        val statusTitle = textView(
            "Bot enabled",
            18f,
            white
        )

        botSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("enabled", false)
            text = if (isChecked) "ON" else "OFF"
            textSize = 17f
            setTextColor(white)

            setOnCheckedChangeListener { _, checked ->
                text = if (checked) "ON" else "OFF"

                prefs.edit()
                    .putBoolean("enabled", checked)
                    .apply()
            }
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusRow.addView(
            statusTitle,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        statusRow.addView(
            botSwitch,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        statusCard.addView(statusRow)

        page.addView(statusCard)

        // =========================
        // TARGET APPLICATION
        // =========================

        page.addView(
            sectionLabel("Target application")
        )

        val appField = roundedField()

        // المستخدم يرى Messenger فقط.
        // com.facebook.orca لا يظهر في الواجهة.
        appField.setText("Messenger")
        appField.isFocusable = false
        appField.isClickable = false

        page.addView(appField)

        // =========================
        // DELAY
        // =========================

        page.addView(
            sectionLabel("Delay (seconds)")
        )

        delayInput = roundedField()

        delayInput.inputType =
            InputType.TYPE_CLASS_NUMBER

        delayInput.setText(
            prefs.getInt(
                "delay",
                10
            ).toString()
        )

        delayInput.setSelection(
            delayInput.text.length
        )

        page.addView(delayInput)

        // =========================
        // REPLY TEXT
        // =========================

        page.addView(
            sectionLabel("Reply text")
        )

        replyInput = roundedField()

        replyInput.minLines = 4
        replyInput.maxLines = 8
        replyInput.gravity = Gravity.TOP or Gravity.START
        replyInput.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        replyInput.setText(
            prefs.getString(
                "reply",
                "مرحباً، سأرد عليك لاحقاً."
            )
        )

        replyInput.setSelection(
            replyInput.text.length
        )

        page.addView(replyInput)

        // =========================
        // SAVE BUTTON
        // =========================

        val saveButton = Button(this).apply {

            text = "💾 SAVE SETTINGS"
            textSize = 16f
            setTextColor(white)

            background = roundedBackground(
                purple,
                18
            )

            setPadding(20, 16, 20, 16)

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
                    // الاسم الداخلي فقط.
                    // المستخدم لا يراه.
                    .putString(
                        "package",
                        "com.facebook.orca"
                    )
                    .apply()

                Toast.makeText(
                    this@MainActivity,
                    "Settings saved ✅",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val saveParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                58.dp()
            )

        saveParams.setMargins(
            0,
            24.dp(),
            0,
            12.dp()
        )

        page.addView(
            saveButton,
            saveParams
        )

        // =========================
        // NOTIFICATION ACCESS
        // =========================

        val notificationButton = Button(this).apply {

            text = "🔔 NOTIFICATION ACCESS"
            textSize = 16f
            setTextColor(white)

            background = roundedBackground(
                blue,
                18
            )

            setPadding(20, 16, 20, 16)

            setOnClickListener {

                try {

                    startActivity(
                        Intent(
                            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                        )
                    )

                } catch (_: Exception) {

                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_SETTINGS
                        )
                    )
                }
            }
        }

        val notificationParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                58.dp()
            )

        page.addView(
            notificationButton,
            notificationParams
        )

        // =========================
        // FOOTER
        // =========================

        val footer = textView(
            "Astro Kings 🛑",
            15f,
            gray
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 4)
        }

        page.addView(footer)

        scrollView.addView(page)

        setContentView(scrollView)
    }

    // =========================
    // HELPERS
    // =========================

    private fun textView(
        text: String,
        size: Float,
        color: Int
    ): TextView {

        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
        }
    }

    private fun sectionLabel(
        text: String
    ): TextView {

        return textView(
            text,
            17f,
            white
        ).apply {
            setPadding(
                6,
                18,
                6,
                8
            )
        }
    }

    private fun card(): LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                18,
                14,
                18,
                14
            )

            background =
                roundedBackground(
                    cardColor,
                    22
                )

            val params =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            params.setMargins(
                0,
                4.dp(),
                0,
                8.dp()
            )

            layoutParams = params
        }
    }

    private fun roundedField(): EditText {

        return EditText(this).apply {

            textSize = 17f

            setTextColor(white)
            setHintTextColor(gray)

            setPadding(
                18,
                4,
                18,
                4
            )

            background =
                roundedBackground(
                    fieldColor,
                    17
                )

            val params =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    58.dp()
                )

            params.setMargins(
                4.dp(),
                0,
                4.dp(),
                4.dp()
            )

            layoutParams = params
        }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                radiusDp.dp().toFloat()

            setStroke(
                1.dp(),
                Color.rgb(42, 46, 65)
            )
        }
    }

    private fun Int.dp(): Int {

        return (
            this *
                resources.displayMetrics.density
            ).toInt()
    }
}

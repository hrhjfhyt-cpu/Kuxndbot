package com.alox.kuxndbot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var botSwitch: Switch
    private lateinit var delayInput: EditText
    private lateinit var replyInput: EditText
    private lateinit var packageInput: EditText

    private val prefs by lazy {
        getSharedPreferences("bot_settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 30, 28, 28)
            setBackgroundColor(Color.rgb(8, 8, 8))
        }

        fun text(
            value: String,
            size: Float
        ): TextView {
            return TextView(this).apply {
                this.text = value
                textSize = size
                setTextColor(Color.WHITE)
                setPadding(0, 10, 0, 10)
            }
        }

        val title = text(
            "Alox Dashboard Bot 👑",
            26f
        ).apply {
            gravity = Gravity.CENTER
        }

        val subtitle = text(
            "Astro Kings 🛑",
            19f
        ).apply {
            gravity = Gravity.CENTER
        }

        val settingsTitle = text(
            "Bot settings 🔵",
            21f
        )

        botSwitch = Switch(this).apply {
            text = "Bot enabled"
            textSize = 17f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("enabled", false)
        }

        val appLabel = text(
            "Target application",
            16f
        )

        packageInput = EditText(this).apply {
            hint = "Application package"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)

            setText(
                prefs.getString(
                    "package",
                    "com.facebook.orca"
                )
            )
        }

        val delayLabel = text(
            "Delay (seconds)",
            16f
        )

        delayInput = EditText(this).apply {
            hint = "10"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            inputType = 2

            setText(
                prefs.getInt(
                    "delay",
                    10
                ).toString()
            )
        }

        val replyLabel = text(
            "Reply text",
            16f
        )

        replyInput = EditText(this).apply {
            hint = "Write your reply..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 3
            gravity = Gravity.TOP

            setText(
                prefs.getString(
                    "reply",
                    "مرحباً، سأرد عليك لاحقاً."
                )
            )
        }

        val saveButton = Button(this).apply {
            text = "💾 Save settings"

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
                    .putString(
                        "package",
                        packageInput.text
                            .toString()
                            .trim()
                    )
                    .putInt(
                        "delay",
                        delay.coerceAtLeast(0)
                    )
                    .putString(
                        "reply",
                        replyInput.text.toString()
                    )
                    .apply()

                Toast.makeText(
                    this@MainActivity,
                    "Settings saved ✅",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val notificationButton = Button(this).apply {
            text = "🔔 Notification Access"

            setOnClickListener {
                startActivity(
                    Intent(
                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                    )
                )
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(settingsTitle)

        root.addView(botSwitch)

        root.addView(appLabel)
        root.addView(packageInput)

        root.addView(delayLabel)
        root.addView(delayInput)

        root.addView(replyLabel)
        root.addView(replyInput)

        root.addView(saveButton)
        root.addView(notificationButton)

        setContentView(root)
    }
}

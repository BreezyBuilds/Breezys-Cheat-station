package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class WelcomeActivity : RetroThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setupPrefs = getSharedPreferences(RetroSetupActivity.PREFS, MODE_PRIVATE)
        if (!intent.getBooleanExtra("skip_retro_setup", false) && !setupPrefs.getBoolean(RetroSetupActivity.FINISHED, false)) {
            startActivity(Intent(this, RetroSetupActivity::class.java))
            finish()
            return
        }

        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Ui.Appearance.background(this@WelcomeActivity))
        }

        val root = Ui.vbox(this, 20)

        Ui.edgeToEdge(scroll)

        val title = Ui.tv(this, "Breezy's Retro Loader", 28f, bold = true)
        root.addView(title, Ui.lp())

        val subtitle = Ui.tv(
            this,
            "Choose a system to get started.",
            16f,
            secondary = true
        )
        root.addView(subtitle, Ui.lp())

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }

        val systems = listOf(
            Triple("Nintendo 3DS", "Cheats, game scanning and installation.") {
                startActivity(Intent(this, SystemHubActivity::class.java).putExtra("system", 0))
            },
            Triple("PlayStation 2", "NetherSX2 / AetherSX2 PNACH cheats.") {
                startActivity(Intent(this, SystemHubActivity::class.java).putExtra("system", 1))
            },
            Triple("Nintendo Wii", "Scan and manage Wii games and cheats.") {
                startActivity(Intent(this, SystemHubActivity::class.java).putExtra("system", 2))
            },
            Triple("Nintendo GameCube", "Scan and manage GameCube games and cheats.") {
                startActivity(Intent(this, SystemHubActivity::class.java).putExtra("system", 3))
            }
        )

        systems.forEachIndexed { index, item ->
            val card = systemButton(
                item.first,
                item.second,
                enabled = true,
                onClick = item.third
            )

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = Ui.dp(this@WelcomeActivity, 94)
                columnSpec = GridLayout.spec(index % 2, 1f)
                rowSpec = GridLayout.spec(index / 2)
                setMargins(
                    Ui.dp(this@WelcomeActivity, 2),
                    Ui.dp(this@WelcomeActivity, 2),
                    Ui.dp(this@WelcomeActivity, 2),
                    Ui.dp(this@WelcomeActivity, 2)
                )
            }

            grid.addView(card, params)
        }

        root.addView(
            grid,
            Ui.lp().apply {
                topMargin = Ui.dp(this@WelcomeActivity, 8)
            }
        )

        val settings = Ui.button(this, "Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        root.addView(
            settings,
            Ui.lp().apply {
                topMargin = Ui.dp(this@WelcomeActivity, 8)
            }
        )

        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(scroll)
    }

    private fun systemButton(
        title: String,
        description: String,
        enabled: Boolean = true,
        onClick: () -> Unit
    ): MaterialCardView {
        val key = when {
            title.contains("3DS") -> "3DS"
            title.contains("PlayStation") -> "PS2"
            title.contains("Wii") -> "WII"
            else -> "GAMECUBE"
        }
        val base = Ui.Appearance.surface(this)
        val start = Ui.Appearance.surface(this)
        val end = Ui.Appearance.raised(this)
        val card = MaterialCardView(this).apply {
            radius = Ui.dp(this@WelcomeActivity, 9).toFloat()
            strokeWidth = Ui.dp(this@WelcomeActivity, 1)
            strokeColor = Ui.Appearance.divider(this@WelcomeActivity)
            cardElevation = 0f
            setCardBackgroundColor(base)
            alpha = if (enabled) 1f else 0.55f
            if (enabled) Ui.interact(this@WelcomeActivity, this, onClick)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@WelcomeActivity, 10), Ui.dp(this@WelcomeActivity, 9), Ui.dp(this@WelcomeActivity, 10), Ui.dp(this@WelcomeActivity, 9))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(start, end)
            ).apply { cornerRadius = Ui.dp(this@WelcomeActivity, 9).toFloat() }
        }
        box.addView(ConsolePackIcon(this, key).apply {
            contentDescription = "$title console"
        }, LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)).apply {
            rightMargin = Ui.dp(this@WelcomeActivity, 9)
        })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(Ui.tv(this, title, 15f, bold = true).apply {
            setTextColor(Ui.Appearance.foreground(this@WelcomeActivity))
            maxLines = 2
        })
        labels.addView(Ui.tv(this, description, 11f, secondary = true).apply {
            setTextColor(Ui.Appearance.secondary(this@WelcomeActivity))
            maxLines = 2
        })
        box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return card
    }
}

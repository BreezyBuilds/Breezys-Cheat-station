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

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            isFillViewport = true
        }

        val root = Ui.vbox(this, 20)

        Ui.edgeToEdge(scroll)

        val title = Ui.tv(this, "Breezy's Cheat Station", 28f, bold = true)
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
            Triple("Nintendo Wii", "Wii cheat management — coming soon.") {
                // Reserved for Wii implementation
            },
            Triple("Nintendo GameCube", "GameCube cheat management — coming soon.") {
                // Reserved for GameCube implementation
            }
        )

        systems.forEachIndexed { index, item ->
            val card = systemButton(
                item.first,
                item.second,
                enabled = index < 2,
                onClick = item.third
            )

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = Ui.dp(this@WelcomeActivity, 110)
                columnSpec = GridLayout.spec(index % 2, 1f)
                rowSpec = GridLayout.spec(index / 2)
                setMargins(
                    Ui.dp(this@WelcomeActivity, 4),
                    Ui.dp(this@WelcomeActivity, 4),
                    Ui.dp(this@WelcomeActivity, 4),
                    Ui.dp(this@WelcomeActivity, 4)
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
        val card = MaterialCardView(this).apply {
            radius = Ui.dp(this@WelcomeActivity, 16).toFloat()
            setCardElevation(Ui.dp(this@WelcomeActivity, 2).toFloat())
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else 0.55f

            if (enabled) {
                setOnClickListener { onClick() }
            }
        }

        val box = Ui.vbox(this, 16).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        box.addView(
            Ui.tv(this, title, 19f, bold = true),
            Ui.lp()
        )

        box.addView(
            Ui.tv(this, description, 12f, secondary = true),
            Ui.lp().apply {
                topMargin = Ui.dp(this@WelcomeActivity, 4)
            }
        )

        card.addView(
            box,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        return card
    }
}

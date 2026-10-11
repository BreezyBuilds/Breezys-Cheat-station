package com.breezybuilds.cheatstation.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator

/** One compact, consistent empty/loading presentation shared by every system tab. */
internal class LibraryEmptyState(context: Context) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private val heading = TextView(context)
    private val detail = TextView(context)
    private val bar = LinearProgressIndicator(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(12), dp(18), dp(14))
        minimumHeight = dp(136)
        val backgroundShape = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Ui.Appearance.surface(context))
        }
        background = backgroundShape

        val icon = TextView(context).apply {
            text = "⌕"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Ui.Appearance.action(context))
        }
        addView(icon, LayoutParams(LayoutParams.MATCH_PARENT, dp(32)))
        heading.apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Ui.Appearance.foreground(context))
        }
        addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        detail.apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Ui.Appearance.secondary(context))
            setPadding(0, dp(4), 0, dp(8))
        }
        addView(detail, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bar.isIndeterminate = true
        bar.setIndicatorColor(Ui.Appearance.action(context))
        bar.trackColor = Ui.Appearance.divider(context)
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(5)).apply {
            marginStart = dp(32); marginEnd = dp(32)
        })
        heading.text = "Loading your game library…"
        detail.text = "Checking saved games and discovering new titles."
    }

    fun showProgress(title: String, message: String) {
        heading.text = title
        detail.text = message
        detail.visibility = View.VISIBLE
        bar.visibility = View.VISIBLE
    }

    var text: CharSequence
        get() = heading.text
        set(value) {
            val loading = value.contains("Scanning", true) || value.contains("Loading", true) ||
                value.contains("Checking", true) || value.contains("Identifying", true)
            heading.text = if (loading) "Loading your game library…" else value
            detail.text = if (loading) "Checking saved games and discovering new titles." else ""
            detail.visibility = if (loading) View.VISIBLE else View.GONE
            bar.visibility = if (loading) View.VISIBLE else View.GONE
        }
}

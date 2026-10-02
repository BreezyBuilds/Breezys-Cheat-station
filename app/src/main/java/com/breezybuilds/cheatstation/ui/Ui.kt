package com.breezybuilds.cheatstation.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.breezybuilds.cheatstation.storage.StorageStatus
import com.breezybuilds.cheatstation.storage.StorageState

/** Tiny helpers so the screens can be built in code (no layout XML needed). */
object Ui {
    enum class Kind(val color: Int) { INFO(0x332196F3), OK(0x334CAF50), WARN(0x44FF9800), ERROR(0x44F44336) }

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun attrColor(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
    }

    fun tv(ctx: Context, text: String, sp: Float = 14f, bold: Boolean = false, secondary: Boolean = false, mono: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = sp
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (mono) typeface = Typeface.MONOSPACE
            if (secondary) setTextColor(attrColor(ctx, android.R.attr.textColorSecondary))
        }

    fun button(ctx: Context, label: String, filled: Boolean = true, onClick: () -> Unit): MaterialButton =
        (if (filled) MaterialButton(ctx) else MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    fun compactButton(ctx: Context, label: String, onClick: () -> Unit): MaterialButton =
        button(ctx, label, true, onClick).apply {
            minHeight = dp(ctx, 38)
            minimumHeight = dp(ctx, 38)
            minWidth = 0
            textSize = 12f
            setPadding(dp(ctx, 10), 0, dp(ctx, 10), 0)
            insetTop = 0
            insetBottom = 0
        }


    /** Consistent navigation card used by the Settings hub and system pages. */
    fun settingsCard(
        ctx: Context,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): MaterialCardView = MaterialCardView(ctx).apply {
        isClickable = true
        isFocusable = true
        radius = dp(ctx, 14).toFloat()
        strokeWidth = dp(ctx, 1)
        setOnClickListener { onClick() }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 12), dp(ctx, 14))
        }

        val textBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        textBox.addView(tv(ctx, title, 16f, bold = true), lp(weight = 1f))
        textBox.addView(tv(ctx, subtitle, 13f, secondary = true).apply {
            setPadding(0, dp(ctx, 3), 0, 0)
        }, lp())

        row.addView(textBox, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(tv(ctx, "›", 28f, secondary = true).apply {
            gravity = android.view.Gravity.CENTER
        }, lp(dp(ctx, 32), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(row, lp())
    }


    /** Compact Station-style status panel used by system settings. */
    fun statusPanel(
        ctx: Context,
        system: String,
        status: StorageStatus,
        emulator: String? = null
    ): MaterialCardView = MaterialCardView(ctx).apply {
        radius = dp(ctx, 14).toFloat()
        strokeWidth = dp(ctx, 1)
        setCardBackgroundColor(
            attrColor(ctx, com.google.android.material.R.attr.colorSurface)
        )
        strokeColor = attrColor(
            ctx,
            com.google.android.material.R.attr.colorOutline
        )

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val stateDot = tv(ctx, "●", 12f, bold = true).apply {
            setTextColor(statusColor(ctx, status.state))
        }

        header.addView(
            stateDot,
            lp(dp(ctx, 20), ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        header.addView(
            tv(ctx, status.title, 12f, bold = true, mono = true),
            lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        header.addView(
            tv(ctx, system, 12f, bold = true, secondary = true, mono = true)
        )

        row.addView(header, lp())

        row.addView(
            tv(ctx, status.detail, 13f, secondary = true).apply {
                setPadding(0, dp(ctx, 5), 0, 0)
            },
            lp()
        )

        if (!emulator.isNullOrBlank()) {
            row.addView(
                tv(ctx, emulator, 12f, secondary = true, mono = true).apply {
                    setPadding(0, dp(ctx, 6), 0, 0)
                },
                lp()
            )
        }

        addView(row, lp())
    }

    private fun statusColor(ctx: Context, state: StorageState): Int =
        when (state) {
            StorageState.ACCESSIBLE ->
                attrColor(ctx, android.R.attr.colorAccent)

            StorageState.DETECTED ->
                attrColor(ctx, android.R.attr.textColorSecondary)

            StorageState.NOT_CONFIGURED,
            StorageState.PERMISSION_REQUIRED,
            StorageState.TRANSFER_ONLY,
            StorageState.INVALID ->
                attrColor(ctx, android.R.attr.textColorSecondary)
        }

    fun banner(ctx: Context): TextView = TextView(ctx).apply {
        setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
        textSize = 13f
        visibility = View.GONE
    }

    fun show(b: TextView, kind: Kind, msg: String) { b.setBackgroundColor(kind.color); b.text = msg; b.visibility = View.VISIBLE }
    fun hide(b: TextView) { b.visibility = View.GONE }

    fun vbox(ctx: Context, pad: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, pad), dp(ctx, pad), dp(ctx, pad), dp(ctx, pad))
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    /** Keeps content clear of the status bar, navigation bar and keyboard (Android 15 draws edge-to-edge). */
    fun edgeToEdge(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
    }
}

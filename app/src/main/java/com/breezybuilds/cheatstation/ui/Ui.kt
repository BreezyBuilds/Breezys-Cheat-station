package com.breezybuilds.cheatstation.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

    // One consistent dark button style across every settings page.
    fun button(ctx: Context, label: String, filled: Boolean = true, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(if (filled) 0xFF2459A6.toInt() else 0xFFE7EDF8.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (filled) 0xFFFFFFFF.toInt() else 0xFF1A273B.toInt())
            strokeColor = android.content.res.ColorStateList.valueOf(if (filled) 0xFFB8CBE8.toInt() else 0xFF34435A.toInt())
            strokeWidth = dp(ctx, 1)
            cornerRadius = dp(ctx, 12)
            minHeight = dp(ctx, 44)
            minimumHeight = dp(ctx, 44)
            insetTop = 0
            insetBottom = 0
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


    /** Shared visual tokens for the illustrated-console / clean-settings hybrid. */
    private const val SURFACE = 0xFF151E2C.toInt()
    private const val OUTLINE = 0xFF2A364A.toInt()
    private const val MUTED = 0xFFABB8CD.toInt()

    fun systemAccent(name: String): Int = when (name.uppercase()) {
        "3DS" -> 0xFFE34050.toInt()
        "PS2" -> 0xFF247EE8.toInt()
        "WII" -> 0xFF13A5D6.toInt()
        "GAMECUBE" -> 0xFF8459DB.toInt()
        else -> 0xFF6284B8.toInt()
    }

    private fun glyph(title: String): String = when (title.lowercase()) {
        "3ds" -> "3DS"
        "ps2" -> "PS"
        "wii" -> "Wii"
        "gamecube" -> "GC"
        "systems" -> "▤"
        "cheats & sources" -> "⌘"
        "backups" -> "↶"
        "advanced", "additional options" -> "⚙"
        "about" -> "ⓘ"
        "compatible emulators", "emulator" -> "▣"
        "emulator data folder", "emulator data path", "games folder", "cheats folder", "transfer folder", "cheat storage" -> "▱"
        else -> "•"
    }

    private fun gradient(ctx: Context, a: Int, b: Int, radius: Int = 16): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)).apply {
            cornerRadius = dp(ctx, radius).toFloat()
        }

    /** Original in-app vector console silhouettes; no third-party logos or artwork. */
    private fun icon(ctx: Context, title: String): View {
        val drawable = when (title.uppercase()) {
            "3DS", "NINTENDO 3DS" -> com.breezybuilds.cheatstation.R.drawable.ic_breezy_3ds
            "PS2", "PLAYSTATION 2" -> com.breezybuilds.cheatstation.R.drawable.ic_breezy_ps2
            "WII", "NINTENDO WII" -> com.breezybuilds.cheatstation.R.drawable.ic_breezy_wii
            "GAMECUBE", "NINTENDO GAMECUBE" -> com.breezybuilds.cheatstation.R.drawable.ic_breezy_gamecube
            else -> 0
        }
        val accent = systemAccent(title)
        val background = gradient(ctx, accent, 0xFF17253C.toInt(), 12)
        return if (drawable != 0) ImageView(ctx).apply {
            setImageResource(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(ctx, 7), dp(ctx, 7), dp(ctx, 7), dp(ctx, 7))
            this.background = background
            contentDescription = "$title console icon"
        } else TextView(ctx).apply {
            text = glyph(title)
            gravity = android.view.Gravity.CENTER
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            this.background = background
            contentDescription = "$title icon"
        }
    }

    /** Clean grouped setting row; no technical detail is lost from the subtitle. */
    fun settingsCard(ctx: Context, title: String, subtitle: String, onClick: () -> Unit): MaterialCardView =
        MaterialCardView(ctx).apply {
            isClickable = true
            isFocusable = true
            radius = 0f
            strokeWidth = 0
            setCardBackgroundColor(0xFF0A111E.toInt())
            setOnClickListener { onClick() }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 6), dp(ctx, 11), dp(ctx, 6), dp(ctx, 11))
            }
            // Navigation is identified by its text, not a decorative icon tile.
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(tv(ctx, title, 14f, bold = true).apply { setTextColor(Color.WHITE) }, lp())
            if (subtitle.isNotBlank()) texts.addView(tv(ctx, subtitle, 12f).apply {
                setTextColor(MUTED)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(ctx, 3), 0, 0)
            }, lp())
            row.addView(texts, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(tv(ctx, "›", 23f).apply {
                gravity = android.view.Gravity.CENTER
                setTextColor(MUTED)
            }, lp(dp(ctx, 22), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(row, lp())
            // Flat settings rows use a restrained separator, like the game library.
            addView(View(ctx).apply { setBackgroundColor(0xFF253044.toInt()) },
                lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)))
        }

    /** Console-coloured illustrated header, reused on the overview and each settings page. */
    fun statusPanel(ctx: Context, system: String, status: StorageStatus, emulator: String? = null): MaterialCardView =
        MaterialCardView(ctx).apply {
            // System destinations are flat menu rows, not rounded action cards.
            radius = 0f
            strokeWidth = 0
            val accent = systemAccent(system)
            setCardBackgroundColor(accent)
            val outer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = gradient(ctx, accent, 0xFF131D34.toInt(), 0)
                setPadding(dp(ctx, 15), dp(ctx, 12), dp(ctx, 15), dp(ctx, 12))
            }
            val header = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            header.addView(icon(ctx, system), lp(dp(ctx, 48), dp(ctx, 48)).apply {
                rightMargin = dp(ctx, 12)
            })
            val identity = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val displayName = when (system.uppercase()) {
                "WII" -> "Wii"
                "GAMECUBE" -> "GameCube"
                else -> system
            }
            identity.addView(tv(ctx, displayName, 19f, bold = true).apply {
                setTextColor(Color.WHITE)
            }, lp())
            if (!emulator.isNullOrBlank()) identity.addView(tv(ctx, emulator, 12f).apply {
                setTextColor(0xFFE3EAF5.toInt())
                setPadding(0, dp(ctx, 3), 0, 0)
            }, lp())
            header.addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val statusLabel = tv(ctx, "●  ${status.title}", 11f, bold = true).apply {
                setTextColor(Color.WHITE)
                setPadding(dp(ctx, 9), dp(ctx, 6), dp(ctx, 9), dp(ctx, 6))
                background = gradient(ctx, 0xAA142337.toInt(), 0xAA142337.toInt(), 12)
                maxLines = 2
            }
            header.addView(statusLabel, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            outer.addView(header, lp())
            // Status details belong on the system-specific configuration page;
            // the overview already displays readiness beside the system name.
            addView(outer, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun statusColor(ctx: Context, state: StorageState): Int =
        when (state) {
            StorageState.ACCESSIBLE ->
                ContextCompat.getColor(ctx, android.R.color.holo_green_light)

            StorageState.DETECTED,
            StorageState.PERMISSION_REQUIRED,
            StorageState.TRANSFER_ONLY ->
                ContextCompat.getColor(ctx, android.R.color.holo_orange_light)

            StorageState.NOT_CONFIGURED ->
                attrColor(ctx, android.R.attr.textColorSecondary)

            StorageState.INVALID ->
                ContextCompat.getColor(ctx, android.R.color.holo_red_light)
        }

    /** Compact, theme-matched notice used throughout the app. */
    fun banner(ctx: Context): TextView = TextView(ctx).apply {
        setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        textSize = 13f
        setTextColor(0xFFEAF1FF.toInt())
        maxLines = 3
        visibility = View.GONE
        compoundDrawablePadding = dp(ctx, 12)
        setCompoundDrawablesWithIntrinsicBounds(0, 0, com.breezybuilds.cheatstation.R.drawable.ic_close, 0)
        setOnClickListener { hide(this) }
    }

    private val noticeTimers = java.util.WeakHashMap<TextView, Runnable>()

    fun show(b: TextView, kind: Kind, msg: String) {
        noticeTimers.remove(b)?.let { b.removeCallbacks(it) }
        val (fill, stroke) = when (kind) {
            Kind.INFO -> 0xFF162B45.toInt() to 0xFF355B83.toInt()
            Kind.OK -> 0xFF142C2B.toInt() to 0xFF31675A.toInt()
            Kind.WARN -> 0xFF2A271F.toInt() to 0xFF6D5736.toInt()
            Kind.ERROR -> 0xFF302128.toInt() to 0xFF824354.toInt()
        }
        b.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(b.context, 12).toFloat()
            setStroke(dp(b.context, 1), stroke)
        }
        b.text = msg
        b.visibility = View.VISIBLE
        // Progress messages remain visible while work continues; completion replaces them.
        val isProgress = kind == Kind.INFO && (
            msg.contains("updating", ignoreCase = true) ||
            msg.contains("scanning", ignoreCase = true) ||
            msg.contains("refreshing", ignoreCase = true) ||
            msg.contains("checking", ignoreCase = true)
        )
        val duration = when {
            isProgress -> 0L
            kind == Kind.OK -> 4_000L
            kind == Kind.INFO -> 5_000L
            kind == Kind.WARN -> 9_000L
            else -> 0L
        }
        if (duration > 0) {
            val task = Runnable { b.visibility = View.GONE; noticeTimers.remove(b) }
            noticeTimers[b] = task
            b.postDelayed(task, duration)
        }
    }

    fun hide(b: TextView) {
        noticeTimers.remove(b)?.let { b.removeCallbacks(it) }
        b.visibility = View.GONE
    }

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

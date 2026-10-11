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
    /** Persisted appearance preferences. The existing Navy palette remains the default. */
    object Appearance {
        private const val PREF = "breezy_appearance"
        fun theme(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("theme", "navy") ?: "navy"
        fun iconPack(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("icon_pack", "duotone") ?: "duotone"
        fun setIconPack(ctx: Context, value: String) { if (value in setOf("outline", "duotone", "pixel")) ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("icon_pack", value).apply() }
        fun haptics(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("haptics", true)
        fun setHaptics(ctx: Context, enabled: Boolean) { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("haptics", enabled).apply() }
        fun systemMode(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("system_mode", "matched") ?: "matched"
        fun setTheme(ctx: Context, value: String) { if (value in setOf("navy", "oled", "light")) ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("theme", value).apply() }
        fun setSystemMode(ctx: Context, value: String) { if (value in setOf("matched", "theme", "console")) ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("system_mode", value).apply() }
        fun background(ctx: Context) = when (theme(ctx)) { "oled" -> 0xFF000000.toInt(); "light" -> 0xFFE8E9EC.toInt(); else -> 0xFF0A111E.toInt() }
        fun foreground(ctx: Context) = if (theme(ctx) == "light") 0xFF202938.toInt() else 0xFFFFFFFF.toInt()
        fun secondary(ctx: Context) = if (theme(ctx) == "light") 0xFF4B5565.toInt() else 0xFFACB9CD.toInt()
        fun divider(ctx: Context) = when (theme(ctx)) { "oled" -> 0xFF333333.toInt(); "light" -> 0xFFBEC5CF.toInt(); else -> 0xFF253044.toInt() }
        fun action(ctx: Context) = when (theme(ctx)) { "oled" -> 0xFFFFFFFF.toInt(); "light" -> 0xFF315F9C.toInt(); else -> 0xFF8FB5F2.toInt() }
        fun raised(ctx: Context) = when (theme(ctx)) { "oled" -> 0xFF1D1D1D.toInt(); "light" -> 0xFFF6F8FB.toInt(); else -> 0xFF1E3047.toInt() }
        fun positive(ctx: Context) = if (theme(ctx) == "light") 0xFF16704B.toInt() else 0xFF77D6AD.toInt()
        fun negative(ctx: Context) = if (theme(ctx) == "light") 0xFFA4363E.toInt() else 0xFFFF9399.toInt()
        fun warning(ctx: Context) = if (theme(ctx) == "light") 0xFF94601B.toInt() else 0xFFECC27D.toInt()
        fun onAction(ctx: Context) = if (theme(ctx) == "light") 0xFFFFFFFF.toInt() else 0xFF111820.toInt()
        fun surface(ctx: Context) = when (theme(ctx)) { "oled" -> 0xFF101010.toInt(); "light" -> 0xFFFFFFFF.toInt(); else -> 0xFF17273B.toInt() }
        fun accent(ctx: Context, system: String): Int = when (systemMode(ctx)) {
            "theme" -> action(ctx)
            "console" -> when (system.uppercase()) {
                "3DS" -> if (theme(ctx) == "light") 0xFF526174.toInt() else 0xFFD2D3D8.toInt()
                "PS2" -> if (theme(ctx) == "light") 0xFF354466.toInt() else 0xFF9CABCA.toInt()
                "WII" -> if (theme(ctx) == "light") 0xFF3B7082.toInt() else 0xFFCADDE6.toInt()
                "GAMECUBE" -> if (theme(ctx) == "light") 0xFF5A4297.toInt() else 0xFFA994DC.toInt()
                else -> systemAccent(system)
            }
            else -> when (system.uppercase()) {
                "3DS" -> if (theme(ctx) == "light") 0xFFB72940.toInt() else systemAccent(system)
                "PS2" -> if (theme(ctx) == "light") 0xFF205FBA.toInt() else systemAccent(system)
                "WII" -> if (theme(ctx) == "light") 0xFF087EAB.toInt() else systemAccent(system)
                "GAMECUBE" -> if (theme(ctx) == "light") 0xFF6840B8.toInt() else systemAccent(system)
                else -> systemAccent(system)
            }
        }
    }

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
            setTextColor(if (secondary) Appearance.secondary(ctx) else Appearance.foreground(ctx))
        }

    // One consistent dark button style across every settings page.
    fun button(ctx: Context, label: String, filled: Boolean = true, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(if (filled) Appearance.onAction(ctx) else Appearance.foreground(ctx))
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (filled) Appearance.action(ctx) else Appearance.surface(ctx))
            strokeColor = android.content.res.ColorStateList.valueOf(if (filled) Appearance.divider(ctx) else Appearance.divider(ctx))
            strokeWidth = dp(ctx, 1)
            cornerRadius = dp(ctx, 12)
            minHeight = dp(ctx, 44)
            minimumHeight = dp(ctx, 44)
            insetTop = 0
            insetBottom = 0
            interact(ctx, this, onClick)
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

    /** Theme-aware, pack-switchable console drawings. */
    private fun icon(ctx: Context, title: String): View = ConsolePackIcon(ctx, title).apply {
        contentDescription = "$title console icon"
    }

    /** Shared press behaviour. Touch, keyboard and accessibility activation all use the
     * same click callback; animations are purely decorative and never consume events. */
    fun interact(ctx: Context, view: View, onClick: () -> Unit) {
        view.isClickable = true
        view.isFocusable = true
        view.isHapticFeedbackEnabled = Appearance.haptics(ctx)
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(0.985f).scaleY(0.985f).alpha(0.90f).setDuration(90L).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140L).start()
                }
            }
            false
        }
        view.setOnClickListener {
            // Also reset after keyboard / accessibility clicks, which may not emit touch UP.
            view.animate().cancel()
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140L).start()
            if (Appearance.haptics(ctx))
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }

    fun selectionRow(ctx: Context, title: String, description: String, selected: Boolean, onClick: () -> Unit, iconPreview: String? = null): MaterialCardView =
        MaterialCardView(ctx).apply {
            radius = dp(ctx, 2).toFloat()
            strokeWidth = if (selected) dp(ctx, 1) else 0
            strokeColor = if (selected) Appearance.action(ctx) else Appearance.divider(ctx)
            setCardBackgroundColor(if (selected) Appearance.surface(ctx) else Appearance.background(ctx))
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 13), dp(ctx, if (iconPreview != null) 8 else 6), dp(ctx, 13), dp(ctx, if (iconPreview != null) 8 else 6))
            }
            val labels = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(tv(ctx, title, 14f, bold = true))
            labels.addView(tv(ctx, description, 12f, secondary = true).apply { setPadding(0, dp(ctx, 3), 0, 0) })
            if (iconPreview != null) {
                val previews = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                listOf("3DS", "PS2", "Wii", "GameCube").forEach { system ->
                    previews.addView(ConsolePackIcon(ctx, system, iconPreview).apply {
                        contentDescription = "$title, $system illustration preview"
                    }, LinearLayout.LayoutParams(dp(ctx, 43), dp(ctx, 43)).apply {
                        marginEnd = dp(ctx, 5)
                    })
                }
                listOf("settings", "cheats", "sources", "backups").forEach { action ->
                    previews.addView(CommonPackIcon(ctx, action, iconPreview).apply {
                        contentDescription = "$title, $action illustration preview"
                    }, LinearLayout.LayoutParams(dp(ctx, 35), dp(ctx, 43)).apply { marginEnd = dp(ctx, 2) })
                }
                labels.addView(previews, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 46)).apply { topMargin = dp(ctx, 5) })
            }
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(tv(ctx, if (selected) "✓" else "○", 22f, bold = selected).apply {
                gravity = android.view.Gravity.CENTER
                setTextColor(if (selected) Appearance.action(ctx) else Appearance.secondary(ctx))
            }, lp(dp(ctx, 30), dp(ctx, 32)))
            addView(row)
            interact(ctx, this, onClick)
        }

    /** Clean grouped setting row; no technical detail is lost from the subtitle. */
    fun settingsCard(ctx: Context, title: String, subtitle: String, onClick: () -> Unit): MaterialCardView =
        MaterialCardView(ctx).apply {
            isClickable = true
            isFocusable = true
            radius = 0f
            strokeWidth = 0
            setCardBackgroundColor(Appearance.background(ctx))
            interact(ctx, this, onClick)

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 6), dp(ctx, 11), dp(ctx, 6), dp(ctx, 11))
            }
            // Navigation is identified by its text, not a decorative icon tile.
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(tv(ctx, title, 14f, bold = true).apply { setTextColor(Appearance.foreground(ctx)) }, lp())
            if (subtitle.isNotBlank()) texts.addView(tv(ctx, subtitle, 12f).apply {
                setTextColor(Appearance.secondary(ctx))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(ctx, 3), 0, 0)
            }, lp())
            row.addView(texts, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(tv(ctx, "›", 23f).apply {
                gravity = android.view.Gravity.CENTER
                setTextColor(Appearance.secondary(ctx))
            }, lp(dp(ctx, 22), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(row, lp())
            // Flat settings rows use a restrained separator, like the game library.
            addView(View(ctx).apply { setBackgroundColor(Appearance.divider(ctx)) },
                lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)))
        }

    /** Console-coloured illustrated header, reused on the overview and each settings page. */
    fun statusPanel(ctx: Context, system: String, status: StorageStatus, emulator: String? = null): MaterialCardView =
        MaterialCardView(ctx).apply {
            // System destinations are flat menu rows, not rounded action cards.
            radius = 0f
            strokeWidth = 0
            val accent = Appearance.accent(ctx, system)
            setCardBackgroundColor(Appearance.surface(ctx))
            val outer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = gradient(ctx, Appearance.surface(ctx), Appearance.raised(ctx), 0)
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
                setTextColor(Appearance.foreground(ctx))
            }, lp())
            if (!emulator.isNullOrBlank()) identity.addView(tv(ctx, emulator, 12f).apply {
                setTextColor(Appearance.secondary(ctx))
                setPadding(0, dp(ctx, 3), 0, 0)
            }, lp())
            header.addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val statusLabel = tv(ctx, "●  ${status.title}", 11f, bold = true).apply {
                setTextColor(Appearance.foreground(ctx))
                setPadding(dp(ctx, 9), dp(ctx, 6), dp(ctx, 9), dp(ctx, 6))
                background = gradient(ctx, Appearance.surface(ctx), Appearance.surface(ctx), 12)
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
        setTextColor(Appearance.foreground(ctx))
        maxLines = 3
        visibility = View.GONE
        compoundDrawablePadding = dp(ctx, 12)
        setCompoundDrawablesWithIntrinsicBounds(0, 0, com.breezybuilds.cheatstation.R.drawable.ic_close, 0)
        setOnClickListener { hide(this) }
    }

    private val noticeTimers = java.util.WeakHashMap<TextView, Runnable>()

    fun show(b: TextView, kind: Kind, msg: String) {
        noticeTimers.remove(b)?.let { b.removeCallbacks(it) }
        val ctx = b.context
        val (fill, stroke, label) = when (kind) {
            Kind.INFO -> Triple(Appearance.surface(ctx), Appearance.action(ctx), Appearance.foreground(ctx))
            Kind.OK -> Triple(Appearance.surface(ctx), Appearance.positive(ctx), Appearance.foreground(ctx))
            Kind.WARN -> Triple(Appearance.surface(ctx), Appearance.warning(ctx), Appearance.foreground(ctx))
            Kind.ERROR -> Triple(Appearance.surface(ctx), Appearance.negative(ctx), Appearance.foreground(ctx))
        }
        b.setTextColor(label)
        b.compoundDrawablesRelative.lastOrNull()?.mutate()?.setTint(label)
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
        setBackgroundColor(Appearance.background(ctx))
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

package dev.azaharcheats.manager.ui

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

package com.breezybuilds.cheatstation.ui

import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.breezybuilds.cheatstation.model.Game
import com.google.android.material.card.MaterialCardView

class GameAdapter(
    private val describe: (Game, Boolean) -> List<String>,
    private val onClick: (Game) -> Unit,
    private val onLong: (Game) -> Unit,
) : RecyclerView.Adapter<GameAdapter.VH>() {

    private var items: List<Game> = emptyList()
    private var showTechnicalDetails = false

    fun setShowTechnicalDetails(show: Boolean) {
        if (showTechnicalDetails == show) return
        showTechnicalDetails = show
        notifyDataSetChanged()
    }

    fun submit(list: List<Game>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(
        val row: LinearLayout,
        val lines: List<TextView>
    ) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            foreground = android.util.TypedValue().let { value ->
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                ctx.getDrawable(value.resourceId)
            }
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 10), Ui.dp(ctx, 16), Ui.dp(ctx, 10))
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val lines = listOf(
            Ui.tv(ctx, "", 16f, bold = true),
            Ui.tv(ctx, "", 12f, secondary = true),
            Ui.tv(ctx, "", 11f, secondary = true, mono = true),
            Ui.tv(ctx, "", 12f, secondary = true),
            Ui.tv(ctx, "", 12f, secondary = true)
        )
        lines.forEachIndexed { index, line ->
            if (index > 0) line.setPadding(0, Ui.dp(ctx, 3), 0, 0)
            texts.addView(line, Ui.lp())
        }
        body.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(Ui.tv(ctx, "›", 25f, secondary = true), LinearLayout.LayoutParams(Ui.dp(ctx, 18), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(body)
        row.addView(View(ctx).apply { setBackgroundColor(0xFF293141.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 1)).apply {
            leftMargin = Ui.dp(ctx, 16)
            rightMargin = Ui.dp(ctx, 16)
        })
        return VH(row, lines)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val g = items[position]

        val descriptions = describe(g, showTechnicalDetails)
        h.lines.forEachIndexed { i, line ->
            val text = descriptions.getOrNull(i).orEmpty()
            line.text = text
            if (i == 1 && !showTechnicalDetails || i == 3 && showTechnicalDetails) GameCheatStatus.style(line, text)
            line.visibility = if (text.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }

        h.row.setOnClickListener { onClick(g) }
        h.row.setOnLongClickListener {
            onLong(g)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}

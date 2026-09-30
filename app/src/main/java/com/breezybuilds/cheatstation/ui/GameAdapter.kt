package com.breezybuilds.cheatstation.ui

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.breezybuilds.cheatstation.model.Game
import com.google.android.material.card.MaterialCardView

class GameAdapter(
    private val describe: (Game) -> List<String>,
    private val onClick: (Game) -> Unit,
    private val onLong: (Game) -> Unit,
) : RecyclerView.Adapter<GameAdapter.VH>() {

    private var items: List<Game> = emptyList()

    fun submit(list: List<Game>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(
        val card: MaterialCardView,
        val lines: List<TextView>
    ) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context

        val card = MaterialCardView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.setMargins(
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 5),
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 5)
                )
            }
            isClickable = true
            isFocusable = true
            radius = Ui.dp(ctx, 14).toFloat()
        }

        val box = Ui.vbox(ctx, 14)

        val lines = listOf(
            Ui.tv(ctx, "", 17f, bold = true),
            Ui.tv(ctx, "", 13f, secondary = true),
            Ui.tv(ctx, "", 12f, secondary = true, mono = true),
            Ui.tv(ctx, "", 13f),
            Ui.tv(ctx, "", 13f, bold = true)
        )

        lines.forEach { box.addView(it, Ui.lp()) }
        card.addView(box)

        return VH(card, lines)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val g = items[position]

        describe(g).forEachIndexed { i, text ->
            if (i < h.lines.size) {
                h.lines[i].text = text
            }
        }

        h.card.setOnClickListener { onClick(g) }
        h.card.setOnLongClickListener {
            onLong(g)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}

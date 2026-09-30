package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.breezybuilds.cheatstation.App
import com.breezybuilds.cheatstation.R
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.scan.DolphinPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DolphinPageFragment(
    private val platform: DolphinPlatform
) : Fragment(R.layout.fragment_dolphin_page) {

    private lateinit var status: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DolphinGameAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        status = view.findViewById(R.id.dolphin_status)
        recycler = view.findViewById(R.id.dolphin_games)


        adapter = DolphinGameAdapter { game -> openCheats(game) }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        scan()
    }

    private fun openCheats(game: Game) {
        val intent = Intent(requireContext(), DolphinCheatsActivity::class.java).apply {
            putExtra("title", game.title)
            putExtra("titleId", game.titleId)
            putExtra(
                "platform",
                if (platform == DolphinPlatform.WII) "wii" else "gamecube"
            )
        }

        startActivity(intent)
    }

    fun rescanGames() {
        scan()
    }

    fun filterGames(query: String) {
        adapter.filter(query)

        status.text = if (query.isBlank()) {
            "${adapter.itemCount} game(s) found"
        } else {
            "${adapter.itemCount} matching game(s)"
        }
    }

    private fun scan() {
        status.text = when (platform) {
            DolphinPlatform.WII -> "Scanning Wii games…"
            DolphinPlatform.GAMECUBE -> "Scanning GameCube games…"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                (requireActivity().application as App)
                    .dolphinScanner
                    .scan(platform)
            }

            adapter.submitList(result.games)

            status.text = when {
                result.error != null -> result.error

                result.games.isEmpty() -> when (platform) {
                    DolphinPlatform.WII -> "No Wii games found."
                    DolphinPlatform.GAMECUBE -> "No GameCube games found."
                }

                else -> "${result.games.size} game(s) found" +
                    if (result.warnings.isNotEmpty()) {
                        "\nUnsupported: " +
                            result.warnings
                                .mapNotNull {
                                    Regex("\\.(\\w+)")
                                        .find(it)
                                        ?.groupValues
                                        ?.get(1)
                                        ?.uppercase()
                                }
                                .groupingBy { it }
                                .eachCount()
                                .entries
                                .joinToString(", ") {
                                    "${it.key}: ${it.value}"
                                }
                    } else {
                        ""
                    }
            }
        }
    }
}

private class DolphinGameAdapter(
    private val onClick: (Game) -> Unit
) : RecyclerView.Adapter<DolphinGameAdapter.GameViewHolder>() {

    private val games = mutableListOf<Game>()
    private val allGames = mutableListOf<Game>()

    fun submitList(items: List<Game>) {
        allGames.clear()
        allGames.addAll(items)

        games.clear()
        games.addAll(items)

        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()

        games.clear()

        if (q.isBlank()) {
            games.addAll(allGames)
        } else {
            games.addAll(
                allGames.filter { game ->
                    game.title.lowercase().contains(q) ||
                        game.titleId.lowercase().contains(q) ||
                        game.source.lowercase().contains(q)
                }
            )
        }

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GameViewHolder {
        val view = android.view.LayoutInflater
            .from(parent.context)
            .inflate(R.layout.item_dolphin_game, parent, false)

        return GameViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: GameViewHolder,
        position: Int
    ) {
        holder.bind(games[position])
    }

    override fun getItemCount(): Int = games.size

    inner class GameViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        private val title =
            view.findViewById<TextView>(R.id.dolphin_game_title)

        private val details =
            view.findViewById<TextView>(R.id.dolphin_game_details)

        fun bind(game: Game) {
            title.text = game.title
            details.text = "${game.titleId}  •  ${game.source}"

            itemView.setOnClickListener {
                onClick(game)
            }
        }
    }
}

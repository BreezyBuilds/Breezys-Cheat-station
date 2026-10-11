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
import com.breezybuilds.cheatstation.emulator.LibraryRefresh
import com.breezybuilds.cheatstation.emulator.UniversalRoms
import com.breezybuilds.cheatstation.scan.DolphinPlatform
import com.breezybuilds.cheatstation.provider.DolphinCheatProvider
import com.breezybuilds.cheatstation.provider.ProviderException
import com.breezybuilds.cheatstation.provider.ProviderError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DolphinPageFragment(
    private val platform: DolphinPlatform
) : Fragment(R.layout.fragment_dolphin_page) {

    private lateinit var status: TextView
    private lateinit var emptyState: LibraryEmptyState
    private var scanning = false
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DolphinGameAdapter
    private val cheatCounts = mutableMapOf<String, Int>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        status = view.findViewById(R.id.dolphin_status)
        recycler = view.findViewById(R.id.dolphin_games)
        val parent = recycler.parent as android.widget.FrameLayout
        emptyState = LibraryEmptyState(requireContext())
        parent.addView(emptyState, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
        status.visibility = View.GONE


        adapter = DolphinGameAdapter(
            countFor = { game -> cheatCounts[game.titleId] ?: GameCheatStatus.get(requireContext(), platform.name, game.titleId) },
            showTechnicalDetails = (requireActivity().application as App).settings.showTechnicalGameDetails
        ) { game -> openCheats(game) }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val application = requireActivity().application as App
        val key = cacheKey()
        val cached = application.cache.loadSystemGames(key)
        adapter.submitList(cached)
        updateEmptyState(cached.size)
        status.text = if (cached.isEmpty()) "Checking saved ${cacheKey()} games…" else "${cached.size} saved game(s)"
        if (LibraryRefresh.needsScan(requireContext(), key, gamesSignature(), application.cache.hasSystemGames(key))) {
            scan()
        } else if (cached.isNotEmpty()) {
            updateCheatCounts(cached)
        } else {
            status.text = "No saved games yet. Use Rescan games to check your library."
        }
    }

    private fun updateEmptyState(count: Int) {
        if (!::emptyState.isInitialized) return
        emptyState.visibility = if (count == 0) View.VISIBLE else View.GONE
        emptyState.text = if (scanning) "Scanning games…" else "No games found in this library."
    }

    private fun cacheKey() = if (platform == DolphinPlatform.WII) "Wii" else "GameCube"
    private fun gamesSignature(): String {
        val storage = (requireActivity().application as App).dolphinStorage
        val folder = if (platform == DolphinPlatform.WII) storage.wiiGamesUri else storage.gameCubeGamesUri
        return listOf(folder?.toString().orEmpty(),
            UniversalRoms.uri(requireContext())?.toString().orEmpty(),
            UniversalRoms.scanMode(requireContext()).name).joinToString("|")
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.setShowTechnicalDetails(
                (requireActivity().application as App).settings.showTechnicalGameDetails
            )
        }
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

        updateEmptyState(adapter.itemCount)
        status.text = if (query.isBlank()) {
            "${adapter.itemCount} game(s) found"
        } else {
            "${adapter.itemCount} matching game(s)"
        }

    }


    private fun updateCheatCounts(items: List<Game>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val application = requireActivity().application as App
            for (game in items) {
                val id = game.titleId
                if (cheatCounts.containsKey(id)) continue
                val cached = GameCheatStatus.get(requireContext(), platform.name, id)
                if (cached != null) {
                    cheatCounts[id] = cached
                } else {
                    val count = withContext(Dispatchers.IO) {
                        try {
                            DolphinCheatProvider({ application.dolphinSettings.sources() }).fetch(id).cheats.size
                        } catch (e: ProviderException) {
                            if (e.kind == ProviderError.NOT_FOUND) 0 else null
                        } catch (_: Exception) { null }
                    }
                    if (count != null) {
                        cheatCounts[id] = count
                        GameCheatStatus.put(requireContext(), platform.name, id, count)
                    }
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun scan() {
        scanning = true
        updateEmptyState(adapter.itemCount)
        status.text = when (platform) {
            DolphinPlatform.WII -> "Scanning Wii games… Your saved games remain available."
            DolphinPlatform.GAMECUBE -> "Scanning GameCube games… Your saved games remain available."
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                (requireActivity().application as App)
                    .dolphinScanner
                    .scan(platform) { message ->
                        activity?.runOnUiThread {
                            if (::emptyState.isInitialized && adapter.itemCount == 0 && scanning) {
                                emptyState.showProgress("Discovering ${cacheKey()} games", message)
                            }
                        }
                    }
            }

            if (result.error == null) {
                val application = requireActivity().application as App
                application.cache.saveSystemGames(cacheKey(), result.games)
                LibraryRefresh.scanned(requireContext(), cacheKey(), gamesSignature())
                adapter.submitList(result.games)
                updateCheatCounts(result.games)
            }

            scanning = false
            updateEmptyState(adapter.itemCount)
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
    private val countFor: (Game) -> Int?,
    private var showTechnicalDetails: Boolean,
    private val onClick: (Game) -> Unit
) : RecyclerView.Adapter<DolphinGameAdapter.GameViewHolder>() {

    fun setShowTechnicalDetails(show: Boolean) {
        if (showTechnicalDetails == show) return
        showTechnicalDetails = show
        notifyDataSetChanged()
    }

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
        private val availability = view.findViewById<TextView>(R.id.dolphin_game_availability)

        fun bind(game: Game) {
            title.text = game.title
            val count = countFor(game)
            GameCheatStatus.style(availability, count?.let { "$it cheats available" } ?: "Check cheats")
            details.text = "${game.titleId}  •  ${game.source}"
            details.visibility = if (showTechnicalDetails) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onClick(game)
            }
        }
    }
}

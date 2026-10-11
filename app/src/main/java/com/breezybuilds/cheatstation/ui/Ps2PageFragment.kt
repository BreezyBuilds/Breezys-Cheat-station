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
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.emulator.LibraryRefresh
import com.breezybuilds.cheatstation.emulator.UniversalRoms
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Ps2PageFragment : Fragment() {

    private lateinit var banner: TextView
    private lateinit var progress: LinearProgressIndicator
    private var searchQuery = ""
    private lateinit var empty: LibraryEmptyState
    private lateinit var setupStatus: TextView
    private lateinit var adapter: GameAdapter

    private var games: List<Game> = emptyList()
    private var busy = false
    private val cheatCounts = mutableMapOf<String, Int>()
    private val checkingCounts = mutableSetOf<String>()
    private val failedCounts = mutableSetOf<String>()
    private var forceRefreshCounts = false
    private var countJob: Job? = null

    private val app get() = requireContext().app

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val root = Ui.vbox(ctx)
        Ui.edgeToEdge(root)


        progress = LinearProgressIndicator(ctx).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progress, Ui.lp())

        banner = Ui.banner(ctx)
        root.addView(banner, Ui.lp())

        setupStatus = Ui.tv(ctx, "", 12f)
        setupStatus.visibility = View.GONE
        root.addView(
            setupStatus,
            Ui.lp().apply {
                setMargins(
                    Ui.dp(ctx, 12),
                    0,
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 4)
                )
            }
        )

        adapter = GameAdapter(
            { game, showTechnicalDetails ->
                if (showTechnicalDetails) {
                    listOf(
                        game.title,
                        "${game.productCode ?: "Serial unknown"}  •  CRC ${game.version ?: "unknown"}",
                        "ID: ${game.titleId}",
                        cheatLabel(game)
                    )
                } else {
                    listOf(game.title, cheatLabel(game))
                }
            },
            { game ->
                startActivity(
                    Intent(ctx, CheatsActivity::class.java)
                        .putExtra("platform", "ps2")
                        .putExtra("titleId", game.titleId)
                        .putExtra("title", game.title)
                        .putExtra("version", game.version)
                        .putExtra("region", game.region)
                )
            },
            {}
        ).apply {
            setShowTechnicalDetails(app.settings.showTechnicalGameDetails)
        }

        val list = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = this@Ps2PageFragment.adapter
        }

        empty = LibraryEmptyState(ctx)
        empty.visibility = View.GONE
        val libraryArea = android.widget.FrameLayout(ctx)
        libraryArea.addView(list, android.widget.FrameLayout.LayoutParams(-1, -1))
        libraryArea.addView(empty, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
        root.addView(libraryArea, Ui.lp(-1, 0, 1f))

        return root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        updateSetup()

        games = app.cache.loadSystemGames("PS2")
        updateAdapter()
        if (LibraryRefresh.needsScan(requireContext(), "PS2", gamesSignature(), app.cache.hasSystemGames("PS2"))) {
            scan()
        } else if (games.isNotEmpty()) {
            updateCheatCounts(games)
        }
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            adapter.setShowTechnicalDetails(app.settings.showTechnicalGameDetails)
            // The details activity writes the authoritative merged count when it finishes.
            cheatCounts.clear()
            updateCheatCounts(games)
            updateAdapter()
        }

        if (::setupStatus.isInitialized) {
            updateSetup()
        }

        if (
            ::adapter.isInitialized &&
            !app.cache.hasSystemGames("PS2") &&
            app.ps2Storage.gamesDoc() != null
        ) {
            scan()
        }
    }

    private fun updateSetup() {
        if (!::setupStatus.isInitialized) return

        val emulator =
            app.ps2Storage.detectEmulator()

        val emulatorText =
            emulator?.name ?: "NetherSX2/AetherSX2 not detected"

        val gamesText =
            if (app.ps2Storage.gamesDoc() != null) {
                "✓ Games folder configured"
            } else {
                "Games folder not configured"
            }

        val cheatText =
            if (
                app.ps2Storage.rootDoc() != null ||
                app.ps2Storage.directCheatsStore() != null ||
                app.ps2Storage.transferDoc() != null
            ) {
                "✓ Cheat storage configured"
            } else {
                "Cheat storage not configured"
            }

        setupStatus.text =
            "$emulatorText  •  $gamesText  •  $cheatText"
    }

    private fun cheatLabel(game: Game): String {
        val count = cheatCounts[game.titleId]
            ?: GameCheatStatus.get(requireContext(), "ps2", game.titleId)
        return count?.let { "$it cheats available" }
            ?: if (game.titleId in checkingCounts) "Scanning cheats…"
            else if (game.titleId in failedCounts) "Unable to check cheats" else "Check cheats"
    }

    private fun updateCheatCounts(items: List<Game>, useSavedCounts: Boolean = true) {
        countJob?.cancel()
        checkingCounts.clear()
        failedCounts.clear()
        checkingCounts.addAll(items.map { it.titleId }.filter { !cheatCounts.containsKey(it) && (!useSavedCounts || GameCheatStatus.get(requireContext(), "ps2", it) == null) })
        updateAdapter()
        countJob = viewLifecycleOwner.lifecycleScope.launch {
            for (game in items) {
                val id = game.titleId
                if (cheatCounts.containsKey(id)) continue
                val cached = if (useSavedCounts) GameCheatStatus.get(requireContext(), "ps2", id) else null
                if (cached != null) {
                    cheatCounts[id] = cached
                } else {
                    val result = withContext(Dispatchers.IO) {
                        if (useSavedCounts) app.ps2Repository.cached(id) ?: app.ps2Repository.load(id) else app.ps2Repository.load(id)
                    }
                    val count = when (result) {
                        is com.breezybuilds.cheatstation.data.CheatResult.Loaded -> result.file.cheats.size
                        is com.breezybuilds.cheatstation.data.CheatResult.Unavailable ->
                            if (result.kind == com.breezybuilds.cheatstation.provider.ProviderError.NOT_FOUND) 0 else null
                    }
                    if (count == null) failedCounts.add(id)
                    if (count != null) {
                        cheatCounts[id] = count
                        GameCheatStatus.put(requireContext(), "ps2", id, count)
                    }
                }
                checkingCounts.remove(id)
                updateAdapter()
            }
        }
    }

    private fun filtered(): List<Game> {
        val query = searchQuery.trim()

        if (query.isEmpty()) return games

        return games.filter {
            it.title.contains(query, true) ||
                it.titleId.contains(query, true)
        }
    }

    private fun updateAdapter() {
        if (!::adapter.isInitialized) return

        adapter.submit(filtered())
        updateEmpty()
    }

    private fun updateEmpty() {
        if (!::empty.isInitialized) return

        empty.visibility =
            if (filtered().isEmpty()) View.VISIBLE else View.GONE

        empty.text =
            if (games.isEmpty() && busy) {
                "Scanning PS2 games… Your library is being checked."
            } else if (games.isEmpty()) {
                "No saved PS2 games yet. Use Rescan games to check your library."
            } else {
                "No games match your search."
            }
    }

    private fun gamesSignature(): String = listOf(
        app.ps2Storage.gamesUri?.toString().orEmpty(),
        UniversalRoms.uri(requireContext())?.toString().orEmpty(),
        UniversalRoms.scanMode(requireContext()).name
    ).joinToString("|")

    fun rescanGames() {
        forceRefreshCounts = true
        countJob?.cancel()
        cheatCounts.clear()
        // Remove stale labels immediately, including the persistent 7-day summaries.
        games.forEach { GameCheatStatus.remove(requireContext(), "ps2", it.titleId) }
        updateAdapter()
        scan()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        updateAdapter()
    }

    fun openCheatSource() { sourceDialog() }

    private fun scan() {
        if (busy) return

        // The scanner resolves manual or universal folders on its IO worker.
        if (app.ps2Storage.gamesUri == null && UniversalRoms.uri(requireContext()) == null) {
            updateSetup()
            return
        }

        busy = true
        updateEmpty()
        progress.visibility = View.GONE

        banner.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    app.ps2Scanner.scan { message ->
                        requireActivity().runOnUiThread {
                            if (::empty.isInitialized && games.isEmpty()) {
                                empty.showProgress("Discovering PS2 games", message)
                            }
                        }
                    }
                }

                // Keep the last good library if the scan reports an error.
                if (result.error == null) {
                    games = result.games
                    app.cache.saveSystemGames("PS2", games)
                    LibraryRefresh.scanned(requireContext(), "PS2", gamesSignature())
                }

                updateAdapter()
                if (result.error == null) {
                    val force = forceRefreshCounts
                    forceRefreshCounts = false
                    if (force) cheatCounts.clear()
                    updateCheatCounts(result.games, useSavedCounts = !force)
                }
                updateSetup()

                if (result.warnings.isNotEmpty()) {
                    Ui.show(
                        banner,
                        Ui.Kind.WARN,
                        result.warnings.first()
                    )
                } else {
                    banner.visibility = View.GONE
                }
            } catch (e: Exception) {
                Ui.show(
                    banner,
                    Ui.Kind.ERROR,
                    "PS2 scan failed: ${e.message ?: "Unknown error"}"
                )
            } finally {
                busy = false
                updateEmpty()
                progress.visibility = View.GONE
            }
        }
    }

    private fun sourceDialog() {
        val sources =
            com.breezybuilds.cheatstation.provider.Ps2CheatSources.all

        var selected =
            sources.indexOfFirst {
                it.id == app.ps2Settings.source().id
            }.coerceAtLeast(0)

        val labels =
            sources.map {
                it.name + "\n" + it.description
            }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("PS2 cheat sources")
            .setSingleChoiceItems(labels, selected) { _, which ->
                selected = which
            }
            .setPositiveButton("Use selected") { _, _ ->
                app.ps2Settings.saveSource(sources[selected])

                Ui.show(
                    banner,
                    Ui.Kind.OK,
                    "Using ${sources[selected].name}."
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

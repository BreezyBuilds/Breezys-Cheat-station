package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.text.InputType
import android.widget.EditText
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.R
import com.breezybuilds.cheatstation.data.DatabaseUpdater
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.util.AppLog
import com.breezybuilds.cheatstation.util.TitleId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreeDsPageFragment : Fragment() {

    private lateinit var content: ViewGroup
    private lateinit var banner: TextView
    private lateinit var progress: LinearProgressIndicator
    private var searchQuery = ""
    private lateinit var empty: LibraryEmptyState
    private lateinit var adapter: GameAdapter

    private var scanned: List<Game> = emptyList()
    private var all: List<Game> = emptyList()
    private val installedCounts = HashMap<String, Int>()
    private val availableCounts = HashMap<String, Int>()
    private var busy = false
    private val app get() = requireContext().app

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        content = Ui.vbox(ctx)


        progress = LinearProgressIndicator(ctx).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        content.addView(progress, Ui.lp())

        banner = Ui.banner(ctx)
        banner.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_close, 0)
        banner.setCompoundDrawablePadding(Ui.dp(ctx, 8))
        banner.setOnClickListener {
            banner.visibility = View.GONE
        }
        content.addView(banner, Ui.lp())

        adapter = GameAdapter(
            ::describe,
            ::openGame,
            ::onLongPress
        ).apply {
            setShowTechnicalDetails(app.settings.showTechnicalGameDetails)
        }

        val list = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = this@ThreeDsPageFragment.adapter
            clipToPadding = false
            setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 16))
        }

        empty = LibraryEmptyState(ctx)

        val libraryArea = android.widget.FrameLayout(ctx)
        libraryArea.addView(list, android.widget.FrameLayout.LayoutParams(-1, -1))
        libraryArea.addView(empty, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
        content.addView(libraryArea, Ui.lp(-1, 0, 1f))

        return content
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scanned = app.cache.loadGames()
        rebuild()

        if (app.storage.has3dsStorage()) {
            if (scanned.isEmpty()) {
                scan()
            } else {
                Ui.show(
                    banner,
                    Ui.Kind.INFO,
                    "Showing your saved game list. Use ⋮ > Rescan games to check for changes."
                )
                loadCounts()
                maybeAutoUpdate()
            }
        } else {
            Ui.show(
                banner,
                Ui.Kind.INFO,
                "3DS storage is not configured yet. Open Settings to configure it."
            )
        }
    }

    override fun onResume() {
        super.onResume()

        if (!::adapter.isInitialized) return

        adapter.setShowTechnicalDetails(app.settings.showTechnicalGameDetails)

        if (app.storage.has3dsStorage()) {
            loadCounts()
        }
    }

    private var refreshCheatsAfterScan = false
    private var updatingCheats = false

    fun rescanGames() {
        if (busy) return
        refreshCheatsAfterScan = true
        availableCounts.clear()
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        scan()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        applyFilter()
    }

    fun addGameByTitleId() { addManual() }

    fun refreshCheatDatabase() { refreshDatabase() }

    private fun scan() {
        if (busy) return

        if (!app.storage.has3dsStorage()) {
            Ui.show(
                banner,
                Ui.Kind.WARN,
                "3DS storage is not configured. Open Settings first."
            )
            return
        }

        busy = true
        rebuild()
        progress.visibility = View.GONE

        banner.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                app.scanner.scan { msg ->
                    requireActivity().runOnUiThread {
                        if (::empty.isInitialized && scanned.isEmpty()) {
                            empty.showProgress("Discovering 3DS games", msg)
                        }
                    }
                }
            }

            busy = false
            progress.visibility = View.GONE
            rebuild()

            if (res.error != null) {
                Ui.show(
                    banner,
                    Ui.Kind.ERROR,
                    res.error
                )
            } else {
                scanned = res.games
                app.cache.saveGames(res.games)
                rebuild()

                val w = res.warnings
                val msg =
                    "Found ${res.games.size} game(s)." +
                        if (w.isNotEmpty()) " ${w.size} note(s): ${w.first()}" else ""

                Ui.show(
                    banner,
                    if (w.isEmpty() && res.games.isNotEmpty()) {
                        Ui.Kind.OK
                    } else {
                        Ui.Kind.WARN
                    },
                    msg +
                        if (res.games.isEmpty()) {
                            " Nothing was detected. Add a game manually with its Title ID, or choose a games folder in Settings."
                        } else {
                            ""
                        }
                )

                w.forEach {
                    AppLog.w("Scan", it)
                }
            }

            if (res.error == null && refreshCheatsAfterScan) {
                refreshCheatsAfterScan = false
                refreshDatabase()
            } else {
                refreshCheatsAfterScan = false
                loadCounts()
                maybeAutoUpdate()
            }
        }
    }

    private fun rebuild() {
        val manual = app.cache.loadManual()
            .filter { m -> scanned.none { it.titleId == m.titleId } }

        all = (scanned + manual)
            .sortedBy { it.title.lowercase() }

        applyFilter()

        empty.visibility =
            if (all.isEmpty()) View.VISIBLE else View.GONE

        if (all.isEmpty()) {
            empty.text =
                if (busy) {
                    "Scanning 3DS games… Your library is being checked."
                } else if (app.storage.has3dsStorage()) {
                    "No saved games yet. Use ⋮ > Rescan games to check your library."
                } else {
                    "No games yet — configure the 3DS folders in Settings."
                }
        }
    }

    private fun applyFilter() {
        if (!::adapter.isInitialized) return

        val q = searchQuery.trim()

        val filtered =
            if (q.isEmpty()) {
                all
            } else {
                all.filter {
                    it.title.contains(q, true) ||
                        it.titleId.contains(q.uppercase())
                }
            }

        adapter.submit(filtered)

        empty.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE

        if (filtered.isEmpty()) {
            empty.text =
                if (all.isEmpty() && busy) {
                    "Scanning 3DS games… Your library is being checked."
                } else if (all.isEmpty()) {
                    "No saved games yet. Use ⋮ > Rescan games to check your library."
                } else {
                    "No games match your search."
                }
        }
    }

    private fun loadCounts() {
        val games = all

        viewLifecycleOwner.lifecycleScope.launch {
            val (inst, avail) = withContext(Dispatchers.IO) {
                val i = HashMap<String, Int>()
                val a = HashMap<String, Int>()

                val installer = app.installer()

                for (g in games) {
                    try {
                        installer?.let {
                            i[g.titleId] =
                                it.readExisting(g.titleId).cheats.size
                        }
                    } catch (e: Exception) {
                        AppLog.w(
                            "ThreeDS",
                            "Could not read cheats for ${g.titleId}"
                        )
                    }

                    app.repository.cached(g.titleId)?.let {
                        a[g.titleId] = it.file.cheats.size
                    }
                }

                i to a
            }

            installedCounts.clear()
            installedCounts.putAll(inst)

            availableCounts.clear()
            availableCounts.putAll(avail)
            avail.forEach { (id, count) -> GameCheatStatus.put(requireContext(), "3ds", id, count) }

            adapter.notifyDataSetChanged()
        }
    }

    private fun describe(g: Game, showTechnicalDetails: Boolean): List<String> {
        val line2 = buildString {
            append(g.region ?: "Region unknown")
            append("  •  Version: ${g.version ?: "unverified"}")
        }

        val avail = if (updatingCheats || refreshCheatsAfterScan) {
            "Scanning cheats…"
        } else
            (availableCounts[g.titleId] ?: GameCheatStatus.get(requireContext(), "3ds", g.titleId))?.let {
                "$it cheats available"
            }
                ?: if (app.repository.sourceHas(g.titleId) == false) {
                    "No cheats found in the current source"
                } else {
                    "Cheats not cached yet • tap to check"
                }

        val inst =
            installedCounts[g.titleId]?.let {
                "$it installed"
            } ?: "Installed cheats: unknown"

        val status =
            when {
                inst == "0 installed" &&
                    avail.contains("available") ->
                    "Ready • cheats available"

                avail.contains("No cheats") ->
                    "No cheats currently listed"

                else ->
                    "Tap to manage cheats"
            }

        if (!showTechnicalDetails) {
            val conciseStatus = when {
                updatingCheats || refreshCheatsAfterScan -> "Scanning cheats…"
                avail.contains("No cheats") -> "No cheats found"
                avail.contains("available") -> avail
                else -> "Check cheats"
            }
            return listOf(g.title, conciseStatus)
        }

        return listOf(
            g.title,
            line2,
            "Title ID: ${g.titleId}",
            "$avail  •  $inst",
            status
        )
    }

    private fun openGame(g: Game) {
        startActivity(
            Intent(requireContext(), CheatsActivity::class.java)
                .putExtra("titleId", g.titleId)
                .putExtra("title", g.title)
                .putExtra("version", g.version)
                .putExtra("region", g.region)
        )
    }

    private fun onLongPress(g: Game) {
        if (
            app.cache.loadManual()
                .none { it.titleId == g.titleId }
        ) {
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove ${g.title}?")
            .setMessage(
                "This removes the manually added entry from this list. Cheat files are not touched."
            )
            .setPositiveButton("Remove") { _, _ ->
                app.cache.saveManual(
                    app.cache.loadManual()
                        .filterNot { it.titleId == g.titleId }
                )
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addManual() {
        val ctx = requireContext()

        val box = Ui.vbox(ctx, 20)

        val id = EditText(ctx).apply {
            hint = "Title ID (16 hex digits, e.g. 00040000001B5000)"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }

        val name = EditText(ctx).apply {
            hint = "Game name (optional)"
        }

        val ver = EditText(ctx).apply {
            hint = "Version (optional, e.g. 1.2)"
        }

        listOf(id, name, ver).forEach {
            box.addView(it, Ui.lp())
        }

        val dlg =
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Add game by Title ID")
                .setView(box)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .show()

        dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            .setOnClickListener {
                val tid = TitleId.normalize(
                    id.text.toString()
                )

                if (tid == null) {
                    id.error = "Must be exactly 16 hex digits"
                    return@setOnClickListener
                }

                val vtxt = ver.text
                    .toString()
                    .trim()

                if (
                    vtxt.isNotEmpty() &&
                    com.breezybuilds.cheatstation.util.VersionUtil
                        .parse(vtxt) == null
                ) {
                    ver.error = "Use a format like 1.2"
                    return@setOnClickListener
                }

                val g = Game(
                    name.text.toString()
                        .trim()
                        .ifBlank { "Game $tid" },
                    tid,
                    null,
                    vtxt.ifBlank { null },
                    null,
                    false,
                    "Entered manually"
                )

                app.cache.saveManual(
                    app.cache.loadManual()
                        .filterNot { it.titleId == tid } + g
                )

                rebuild()
                loadCounts()
                dlg.dismiss()
            }
    }

    private fun refreshDatabase() {
        if (busy || updatingCheats) return

        if (all.isEmpty()) {
            Ui.show(
                banner,
                Ui.Kind.WARN,
                "There are no games to update yet."
            )
            return
        }

        busy = true
        updatingCheats = true
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        rebuild()
        progress.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val s = DatabaseUpdater.updateAll(
                app.repository,
                all
            ) { i, n ->
                requireActivity().runOnUiThread {
                    Ui.show(
                        banner,
                        Ui.Kind.INFO,
                        "Updating cheats $i/$n…"
                    )
                }
            }

            busy = false
            updatingCheats = false
            if (::adapter.isInitialized) adapter.notifyDataSetChanged()
            progress.visibility = View.GONE
            rebuild()

            Ui.show(
                banner,
                if (s.failed == 0 && s.message == null) {
                    Ui.Kind.OK
                } else {
                    Ui.Kind.WARN
                },
                s.describe()
            )

            app.settings.lastAutoCheck =
                System.currentTimeMillis()

            loadCounts()
        }
    }

    private fun maybeAutoUpdate() {
        if (
            !app.settings.autoUpdate ||
            busy ||
            all.isEmpty()
        ) {
            return
        }

        if (
            System.currentTimeMillis() -
                app.settings.lastAutoCheck <
            12L * 3600_000
        ) {
            return
        }

        app.settings.lastAutoCheck =
            System.currentTimeMillis()

        viewLifecycleOwner.lifecycleScope.launch {
            val s = DatabaseUpdater.updateAll(
                app.repository,
                all
            )

            if (s.updated > 0) {
                Ui.show(
                    banner,
                    Ui.Kind.INFO,
                    "Cheat database updated (${s.updated} game(s))."
                )
                loadCounts()
            } else if (s.message != null) {
                AppLog.w(
                    "ThreeDS",
                    "Auto update: ${s.message}"
                )
            }
        }
    }
}

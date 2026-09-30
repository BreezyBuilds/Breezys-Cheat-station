package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.model.Game
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Ps2PageFragment : Fragment() {

    private lateinit var banner: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private lateinit var dashboard: TextView
    private lateinit var setupStatus: TextView
    private lateinit var adapter: GameAdapter

    private var games: List<Game> = emptyList()
    private var busy = false

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

        dashboard = Ui.tv(
            ctx,
            "0 games found",
            13f,
            secondary = true
        )
        root.addView(
            dashboard,
            Ui.lp().apply {
                setMargins(
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 8),
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 4)
                )
            }
        )

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

        search = EditText(ctx).apply {
            hint = "🔎 Search games"
            setSingleLine()

            addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun afterTextChanged(
                        s: android.text.Editable?
                    ) {
                        updateAdapter()
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) = Unit
                }
            )
        }

        search.visibility = View.GONE
        root.addView(
            search,
            Ui.lp().apply {
                setMargins(
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 6),
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 4)
                )
            }
        )

        adapter = GameAdapter(
            { game ->
                listOf(
                    game.title,
                    "${game.productCode ?: "Serial unknown"}  •  CRC ${game.version ?: "unknown"}",
                    "ID: ${game.titleId}",
                    "Tap for cheats"
                )
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
        )

        val list = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = this@Ps2PageFragment.adapter
        }

        root.addView(
            list,
            Ui.lp(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        empty = Ui.tv(
            ctx,
            "No PS2 games found.",
            15f,
            secondary = true
        )

        empty.visibility = View.GONE

        root.addView(
            empty,
            Ui.lp().apply {
                setMargins(
                    Ui.dp(ctx, 16),
                    Ui.dp(ctx, 12),
                    Ui.dp(ctx, 16),
                    Ui.dp(ctx, 12)
                )
            }
        )

        return root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        updateSetup()

        if (app.ps2Storage.gamesDoc() != null) {
            scan()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::setupStatus.isInitialized) {
            updateSetup()
        }

        if (
            ::adapter.isInitialized &&
            app.ps2Storage.gamesDoc() != null &&
            games.isEmpty()
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

    private fun filtered(): List<Game> {
        val query =
            search.text?.toString()?.trim().orEmpty()

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
        refreshDashboard()
    }

    private fun updateEmpty() {
        if (!::empty.isInitialized) return

        empty.visibility =
            if (filtered().isEmpty()) View.VISIBLE else View.GONE

        empty.text =
            if (games.isEmpty()) {
                "No PS2 games found.\n\nConfigure your PS2 games folder in Settings."
            } else {
                "No games match your search."
            }
    }

    private fun refreshDashboard() {
        if (!::dashboard.isInitialized) return

        val total = games.size

        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                var count = 0

                for (game in games) {
                    try {
                        app.ps2Repository.cached(game.titleId)?.let {
                            count += it.file.cheats.size
                        }
                    } catch (_: Exception) {
                    }
                }

                count
            }

            if (!isAdded || !::dashboard.isInitialized) {
                return@launch
            }

            dashboard.text =
                "$total games found"
        }
    }

    fun rescanGames() { scan() }

    fun setSearchQuery(query: String) {
        search.setText(query)
        search.setSelection(search.text.length)
    }

    fun openCheatSource() { sourceDialog() }

    private fun scan() {
        if (busy) return

        if (app.ps2Storage.gamesDoc() == null) {
            updateSetup()
            return
        }

        busy = true
        progress.visibility = View.VISIBLE

        Ui.show(
            banner,
            Ui.Kind.INFO,
            "Scanning PS2 games…"
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    app.ps2Scanner.scan { message ->
                        requireActivity().runOnUiThread {
                            if (::banner.isInitialized) {
                                Ui.show(
                                    banner,
                                    Ui.Kind.INFO,
                                    message
                                )
                            }
                        }
                    }
                }

                games = result.games

                updateAdapter()
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

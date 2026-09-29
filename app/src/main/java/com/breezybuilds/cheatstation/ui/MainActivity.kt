package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.data.DatabaseUpdater
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.storage.StorageManager
import com.breezybuilds.cheatstation.util.AppLog
import com.breezybuilds.cheatstation.util.TitleId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var banner: TextView
    private lateinit var setupPanel: LinearLayout
    private lateinit var setupText: TextView
    private lateinit var search: EditText
    private lateinit var progress: LinearProgressIndicator
    private lateinit var empty: TextView
    private lateinit var dashboard: TextView
    private lateinit var databaseStatus: TextView
    private lateinit var storageStatus: TextView
    private lateinit var sourceStatus: TextView
    private lateinit var adapter: GameAdapter

    private var scanned: List<Game> = emptyList()
    private var all: List<Game> = emptyList()
    private val installedCounts = HashMap<String, Int>()
    private val availableCounts = HashMap<String, Int>()
    private var shownRoot: Uri? = null
    private var shownGames: Uri? = null
    private var busy = false

    private val pickRoot = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onRootPicked(uri) }
    private val pickGames = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onGamesPicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val root = Ui.vbox(ctx)
        Ui.edgeToEdge(root)

        val toolbar = MaterialToolbar(ctx).apply {
            title = "Breezy's Cheat Station"
            subtitle = "3DS + PS2 Cheat Manager"
        }
        toolbar.menu.add(Menu.NONE, 1, 1, "Rescan games")
        toolbar.menu.add(Menu.NONE, 2, 2, "Add game by Title ID")
        toolbar.menu.add(Menu.NONE, 3, 3, "Refresh cheat database")
        toolbar.menu.add(Menu.NONE, 4, 4, "Settings")
        toolbar.menu.add(Menu.NONE, 5, 5, "PlayStation 2 / NetherSX2")
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> scan()
                2 -> addManual()
                3 -> refreshDatabase()
                4 -> startActivity(Intent(ctx, SettingsActivity::class.java))
                5 -> startActivity(Intent(ctx, Ps2Activity::class.java))
            }
            true
        }
        root.addView(toolbar, Ui.lp())

        progress = LinearProgressIndicator(ctx).apply { isIndeterminate = true; visibility = View.GONE }
        root.addView(progress, Ui.lp())
        banner = Ui.banner(ctx)
        root.addView(banner, Ui.lp())

        // Compact dashboard: gives the library a proper app-like overview.
        val dashboardCard = MaterialCardView(ctx).apply {
            radius = Ui.dp(ctx, 16).toFloat()
            setCardElevation(Ui.dp(ctx, 2).toFloat())
            layoutParams = Ui.lp().apply {
                setMargins(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 4))
            }
        }
        val dash = Ui.vbox(ctx, 14)
        dashboard = Ui.tv(ctx, "", 16f, bold = true)
        databaseStatus = Ui.tv(ctx, "", 12f, secondary = true)
        storageStatus = Ui.tv(ctx, "", 12f, secondary = true)
        sourceStatus = Ui.tv(ctx, "", 12f, secondary = true)
        dash.addView(dashboard, Ui.lp())
        dash.addView(databaseStatus, Ui.lp())
        dash.addView(storageStatus, Ui.lp())
        dash.addView(sourceStatus, Ui.lp())
        val dashboardButtons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        dashboardButtons.addView(Ui.button(ctx, "Folders") {
            startActivity(Intent(ctx, SettingsActivity::class.java).putExtra("open_section", "folders"))
        }, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(ctx, 4) })
        dashboardButtons.addView(Ui.button(ctx, "Cheat source") {
            startActivity(Intent(ctx, SettingsActivity::class.java).putExtra("open_section", "source"))
        }, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(ctx, 4) })
        dash.addView(dashboardButtons, Ui.lp())
        dashboardCard.addView(dash)
        root.addView(dashboardCard)

        setupPanel = Ui.vbox(ctx, 16).apply { visibility = View.GONE }
        val setupTitle = Ui.tv(ctx, "Welcome to Breezy's Cheat Station", 18f, bold = true)
        setupPanel.addView(setupTitle, Ui.lp())
        setupText = Ui.tv(ctx, "", 15f)
        setupPanel.addView(setupText, Ui.lp())
        setupPanel.addView(Ui.button(ctx, "Select 3DS emulator data folder") { launchPicker() }, Ui.lp().apply { topMargin = Ui.dp(ctx, 12) })
        setupPanel.addView(Ui.button(ctx, "Select games folder (.3DS / .CIA / .CXI)") { pickGames.launch(null) }, Ui.lp().apply { topMargin = Ui.dp(ctx, 8) })
        root.addView(setupPanel, Ui.lp())

        search = EditText(ctx).apply {
            hint = "Search by game name or Title ID"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { applyFilter() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(search, Ui.lp().apply { setMargins(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12), 0) })

        adapter = GameAdapter(::describe, ::openGame, ::onLongPress)
        val list = RecyclerView(ctx).apply { layoutManager = LinearLayoutManager(ctx); this.adapter = this@MainActivity.adapter; clipToPadding = false }
        list.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 16))
        empty = Ui.tv(ctx, "", 15f, secondary = true).apply { setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 24), Ui.dp(ctx, 16), 0) }
        root.addView(empty, Ui.lp())
        root.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        // Show what we already know immediately (also works offline).
        scanned = app.cache.loadGames()
        rebuild()
        if (app.storage.rootUri == null) app.storage.autoDetectExistingGrant()
        shownRoot = app.storage.rootUri
        shownGames = app.storage.gamesUri
        updateSetup()
        refreshDashboard()
        if (app.storage.rootDoc() != null) {
            if (scanned.isEmpty()) scan() else {
                Ui.show(banner, Ui.Kind.INFO, "Showing your saved game list. Use ⋮ > Rescan games to check for changes.")
                loadCounts(); maybeAutoUpdate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (app.storage.rootUri != shownRoot || app.storage.gamesUri != shownGames) {
            val rootChanged = app.storage.rootUri != shownRoot
            shownRoot = app.storage.rootUri
            shownGames = app.storage.gamesUri
            updateSetup()
            if (app.storage.rootDoc() != null && (rootChanged || app.storage.gamesUri != null)) scan()
        } else if (::adapter.isInitialized && all.isNotEmpty()) loadCounts()
    }

    // ---- setup / folder -------------------------------------------------------------------------
    private fun updateSetup() {
        val st = app.storage
        val ok = st.rootDoc() != null
        setupPanel.visibility = if (ok && st.gamesUri != null) View.GONE else View.VISIBLE
        val emulatorLine = if (ok) "✓ 3DS emulator data folder: ${st.describe(st.rootUri)}" else
            if (st.rootUri != null) "⚠ Emulator folder access was lost. Select it again." else "1. Select the 3DS emulator's data/user folder."
        val gamesLine = if (st.gamesUri != null) "✓ Games folder: ${st.describe(st.gamesUri)}" else "2. Select the folder containing your .3DS/.CIA/.CXI files."
        setupText.text = "$emulatorLine\n$gamesLine\n\nThe emulator folder is used for installed titles and cheat files. The games folder is used to find ROM files."
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        if (all.isEmpty()) empty.text = if (ok) "No games found yet. Use ⋮ > Rescan games, or add a game by Title ID." else "No games yet — finish the folder setup above."
    }

    private fun launchPicker() = pickRoot.launch(app.storage.pickerHint())

    private fun onRootPicked(uri: Uri) {
        val err = app.storage.accept(StorageManager.Slot.ROOT, uri)
        if (err == null) { afterRootChange(); return }
        MaterialAlertDialogBuilder(this).setTitle("Emulator folder not recognised").setMessage(err)
            .setPositiveButton("Choose again") { _, _ -> launchPicker() }
            .setNegativeButton("Use anyway") { _, _ -> app.storage.forceRoot(uri); afterRootChange() }
            .show()
    }

    private fun onGamesPicked(uri: Uri) {
        val err = app.storage.accept(StorageManager.Slot.GAMES, uri)
        if (err != null) {
            MaterialAlertDialogBuilder(this).setTitle("Games folder problem").setMessage(err)
                .setPositiveButton("Choose again") { _, _ -> pickGames.launch(null) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        updateSetup()
        if (app.storage.rootDoc() != null) scan()
        else Ui.show(banner, Ui.Kind.OK, "Games folder saved. Now select your 3DS emulator data folder.")
    }

    private fun afterRootChange() {
        shownRoot = app.storage.rootUri
        shownGames = app.storage.gamesUri
        updateSetup()
        refreshDashboard()
        scan()
    }

    // ---- scanning -------------------------------------------------------------------------------
    private fun scan() {
        if (busy) return
        if (app.storage.rootDoc() == null) { updateSetup(); return }
        busy = true
        progress.visibility = View.VISIBLE
        Ui.show(banner, Ui.Kind.INFO, "Scanning for games…")
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { app.scanner.scan { msg -> runOnUiThread { Ui.show(banner, Ui.Kind.INFO, msg) } } }
            busy = false
            progress.visibility = View.GONE
            if (res.error != null) {
                Ui.show(banner, Ui.Kind.ERROR, res.error)
            } else {
                scanned = res.games
                app.cache.saveGames(res.games)
                rebuild()
                val w = res.warnings
                val msg = "Found ${res.games.size} game(s)." + if (w.isNotEmpty()) " ${w.size} note(s): ${w.first()}" else ""
                Ui.show(banner, if (w.isEmpty() && res.games.isNotEmpty()) Ui.Kind.OK else Ui.Kind.WARN, msg +
                    if (res.games.isEmpty()) " Nothing was detected. Add a game manually with its Title ID, or choose a games folder in Settings." else "")
                w.forEach { AppLog.w("Scan", it) }
            }
            updateSetup()
            loadCounts()
            maybeAutoUpdate()
        }
    }

    private fun rebuild() {
        val manual = app.cache.loadManual().filter { m -> scanned.none { it.titleId == m.titleId } }
        all = (scanned + manual).sortedBy { it.title.lowercase() }
        applyFilter()
        updateSetup()
    }

    private fun applyFilter() {
        val q = search.text?.toString()?.trim().orEmpty()
        val filtered = if (q.isEmpty()) all else all.filter { it.title.contains(q, true) || it.titleId.contains(q.uppercase()) }
        adapter.submit(filtered)
    }

    // ---- counts (cache + installed file only, no network) --------------------------------------
    private fun loadCounts() {
        val games = all
        lifecycleScope.launch {
            val (inst, avail) = withContext(Dispatchers.IO) {
                val i = HashMap<String, Int>(); val a = HashMap<String, Int>()
                val installer = app.installer()
                for (g in games) {
                    try { installer?.let { i[g.titleId] = it.readExisting(g.titleId).cheats.size } } catch (e: Exception) { AppLog.w("Main", "Could not read cheats for ${g.titleId}") }
                    app.repository.cached(g.titleId)?.let { a[g.titleId] = it.file.cheats.size }
                }
                i to a
            }
            installedCounts.clear(); installedCounts.putAll(inst)
            availableCounts.clear(); availableCounts.putAll(avail)
            adapter.notifyDataSetChanged()
            refreshDashboard()
        }
    }

    private fun refreshDashboard() {
        val total = all.size
        val cached = availableCounts.values.sum()
        val installed = installedCounts.values.sum()
        dashboard.text = "$total games  •  $cached cheats cached  •  $installed installed"
        val indexTime = app.repository.indexTime()
        databaseStatus.text = if (indexTime != null) {
            "Cheat database: updated ${app.repository.formatTime(indexTime)}"
        } else {
            "Cheat database: not downloaded yet  •  use Refresh cheat database"
        }
        storageStatus.text = "Games folder: ${app.storage.describe(app.storage.gamesUri)}  •  Emulator folder: ${app.storage.describe(app.storage.rootUri)}"
        sourceStatus.text = "Cheat source: ${app.settings.source().displayName}"
    }

    private fun describe(g: Game): List<String> {
        val line2 = buildString {
            append(g.region ?: "Region unknown")
            append("  •  Version: ${g.version ?: "unverified"}")
        }
        val avail = availableCounts[g.titleId]?.let { "$it cheat(s) available" }
            ?: if (app.repository.sourceHas(g.titleId) == false) "No cheats found in the current source"
            else "Cheats not cached yet • tap to check"
        val inst = installedCounts[g.titleId]?.let { "$it installed" } ?: "Installed cheats: unknown"
        val status = when {
            inst == "0 installed" && avail.contains("available") -> "Ready • cheats available"
            avail.contains("No cheats") -> "No cheats currently listed"
            else -> "Tap to manage cheats"
        }
        return listOf(g.title, line2, "Title ID: ${g.titleId}", "$avail  •  $inst", status)
    }

    private fun openGame(g: Game) {
        startActivity(Intent(this, CheatsActivity::class.java)
            .putExtra("titleId", g.titleId).putExtra("title", g.title)
            .putExtra("version", g.version).putExtra("region", g.region))
    }

    private fun onLongPress(g: Game) {
        if (app.cache.loadManual().none { it.titleId == g.titleId }) return
        MaterialAlertDialogBuilder(this).setTitle("Remove ${g.title}?")
            .setMessage("This removes the manually added entry from this list. Cheat files are not touched.")
            .setPositiveButton("Remove") { _, _ -> app.cache.saveManual(app.cache.loadManual().filterNot { it.titleId == g.titleId }); rebuild() }
            .setNegativeButton("Cancel", null).show()
    }

    // ---- manual add -----------------------------------------------------------------------------
    private fun addManual() {
        val ctx = this
        val box = Ui.vbox(ctx, 20)
        val id = EditText(ctx).apply { hint = "Title ID (16 hex digits, e.g. 00040000001B5000)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        val name = EditText(ctx).apply { hint = "Game name (optional)" }
        val ver = EditText(ctx).apply { hint = "Version (optional, e.g. 1.2)" }
        listOf(id, name, ver).forEach { box.addView(it, Ui.lp()) }
        val dlg = MaterialAlertDialogBuilder(ctx).setTitle("Add game by Title ID").setView(box)
            .setPositiveButton("Add", null).setNegativeButton("Cancel", null).show()
        dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val tid = TitleId.normalize(id.text.toString())
            if (tid == null) { id.error = "Must be exactly 16 hex digits"; return@setOnClickListener }
            val vtxt = ver.text.toString().trim()
            if (vtxt.isNotEmpty() && com.breezybuilds.cheatstation.util.VersionUtil.parse(vtxt) == null) { ver.error = "Use a format like 1.2"; return@setOnClickListener }
            val g = Game(name.text.toString().trim().ifBlank { "Game $tid" }, tid, null, vtxt.ifBlank { null }, null, false, "Entered manually")
            app.cache.saveManual(app.cache.loadManual().filterNot { it.titleId == tid } + g)
            rebuild(); loadCounts(); dlg.dismiss()
        }
    }

    // ---- cheat database -------------------------------------------------------------------------
    private fun refreshDatabase() {
        if (busy) return
        if (all.isEmpty()) { Ui.show(banner, Ui.Kind.WARN, "There are no games to update yet."); return }
        busy = true; progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val s = DatabaseUpdater.updateAll(app.repository, all) { i, n -> runOnUiThread { Ui.show(banner, Ui.Kind.INFO, "Updating cheats $i/$n…") } }
            busy = false; progress.visibility = View.GONE
            Ui.show(banner, if (s.failed == 0 && s.message == null) Ui.Kind.OK else Ui.Kind.WARN, s.describe())
            app.settings.lastAutoCheck = System.currentTimeMillis()
            loadCounts()
        }
    }

    private fun maybeAutoUpdate() {
        if (!app.settings.autoUpdate || busy || all.isEmpty()) return
        if (System.currentTimeMillis() - app.settings.lastAutoCheck < 12L * 3600_000) return
        app.settings.lastAutoCheck = System.currentTimeMillis()
        lifecycleScope.launch {
            val s = DatabaseUpdater.updateAll(app.repository, all)
            if (s.updated > 0) { Ui.show(banner, Ui.Kind.INFO, "Cheat database updated (${s.updated} game(s))."); loadCounts() }
            else if (s.message != null) AppLog.w("Main", "Auto update: ${s.message}")
        }
    }
}

/** RecyclerView adapter for the "My Games" list. */
class GameAdapter(
    private val describe: (Game) -> List<String>,
    private val onClick: (Game) -> Unit,
    private val onLong: (Game) -> Unit,
) : RecyclerView.Adapter<GameAdapter.VH>() {
    private var items: List<Game> = emptyList()
    fun submit(list: List<Game>) { items = list; notifyDataSetChanged() }

    class VH(val card: MaterialCardView, val lines: List<TextView>) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val card = MaterialCardView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.setMargins(Ui.dp(ctx, 12), Ui.dp(ctx, 5), Ui.dp(ctx, 12), Ui.dp(ctx, 5))
            }
            isClickable = true; isFocusable = true
            radius = Ui.dp(ctx, 14).toFloat()
        }
        val box = Ui.vbox(ctx, 14)
        val lines = listOf(
            Ui.tv(ctx, "", 17f, bold = true), Ui.tv(ctx, "", 13f, secondary = true),
            Ui.tv(ctx, "", 12f, secondary = true, mono = true), Ui.tv(ctx, "", 13f), Ui.tv(ctx, "", 13f, bold = true),
        )
        lines.forEach { box.addView(it, Ui.lp()) }
        card.addView(box)
        return VH(card, lines)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val g = items[position]
        describe(g).forEachIndexed { i, s -> h.lines[i].text = s }
        h.card.setOnClickListener { onClick(g) }
        h.card.setOnLongClickListener { onLong(g); true }
    }

    override fun getItemCount() = items.size
}

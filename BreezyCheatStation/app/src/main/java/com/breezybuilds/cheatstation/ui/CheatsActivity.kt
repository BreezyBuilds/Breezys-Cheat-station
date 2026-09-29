package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.cheats.CheatInstaller
import com.breezybuilds.cheatstation.cheats.Compatibility
import com.breezybuilds.cheatstation.cheats.InstallResult
import com.breezybuilds.cheatstation.data.CheatResult
import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line in the cheat list: what the source offers and/or what is already in Azahar's cheat file. */
data class CheatRow(val key: String, val name: String, val remote: Cheat?, val installed: Cheat?) {
    val differs: Boolean get() = remote != null && installed != null && remote.normalizedCode != installed.normalizedCode
    val codeCount: Int get() = (remote ?: installed)?.codeLines?.size ?: 0
}

class CheatsActivity : AppCompatActivity() {
    private lateinit var game: Game
    private lateinit var banner: TextView
    private lateinit var compatBanner: TextView
    private lateinit var header: TextView
    private lateinit var sourceInfo: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var adapter: CheatAdapter
    private lateinit var buttons: List<View>

    private var loaded: CheatResult.Loaded? = null
    private var compat: Compatibility? = null
    private var installedFile: CheatFile? = null
    private var rows: List<CheatRow> = emptyList()
    private val selected = LinkedHashSet<String>()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val tid = intent.getStringExtra("titleId") ?: run { finish(); return }
        game = Game(intent.getStringExtra("title") ?: "Game", tid, intent.getStringExtra("region"), intent.getStringExtra("version"), null, false, "")

        val root = Ui.vbox(ctx)
        Ui.edgeToEdge(root)
        val toolbar = MaterialToolbar(ctx).apply { title = "Cheats" }
        toolbar.menu.add(Menu.NONE, 1, 1, "Refresh cheats")
        toolbar.menu.add(Menu.NONE, 2, 2, "Select all")
        toolbar.menu.add(Menu.NONE, 3, 3, "Select none")
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> loadCheats()
                2 -> { selected.clear(); selected.addAll(rows.map { r -> r.key }); adapter.notifyDataSetChanged() }
                3 -> { selected.clear(); adapter.notifyDataSetChanged() }
            }
            true
        }
        root.addView(toolbar, Ui.lp())
        progress = LinearProgressIndicator(ctx).apply { isIndeterminate = true; visibility = View.GONE }
        root.addView(progress, Ui.lp())

        header = Ui.tv(ctx, "", 14f).apply { setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 4)) }
        root.addView(header, Ui.lp())
        sourceInfo = Ui.tv(ctx, "", 12f, secondary = true).apply { setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 6)) }
        root.addView(sourceInfo, Ui.lp())
        compatBanner = Ui.banner(ctx)
        root.addView(compatBanner, Ui.lp())
        banner = Ui.banner(ctx)
        root.addView(banner, Ui.lp())

        adapter = CheatAdapter({ rows }, selected, { key, on -> if (on) selected.add(key) else selected.remove(key) }, ::onEnableToggle)
        val list = RecyclerView(ctx).apply { layoutManager = LinearLayoutManager(ctx); this.adapter = this@CheatsActivity.adapter }
        root.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6)) }
        val b1 = Ui.button(ctx, "Install Selected") { install(rows.filter { it.key in selected }.mapNotNull { it.remote }) }
        val b2 = Ui.button(ctx, "Install All", filled = false) { install(rows.mapNotNull { it.remote }) }
        val b3 = Ui.button(ctx, "Remove Selected", filled = false) { remove() }
        buttons = listOf(b1, b2, b3)
        buttons.forEach { bar.addView(it, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0) }) }
        root.addView(bar, Ui.lp())
        setContentView(root)

        updateHeader()
        // Cached copy first so the screen is useful immediately (and offline), then fresh data.
        app.repository.cached(tid)?.let { loaded = it; compat = Compatibility.check(game.version, it.file.version); refreshInfo() }
        reloadInstalled()
        loadCheats()
    }

    private fun setBusy(b: Boolean) { busy = b; progress.visibility = if (b) View.VISIBLE else View.GONE; buttons.forEach { it.isEnabled = !b } }

    private fun updateHeader() {
        val avail = loaded?.file?.cheats?.size
        val inst = installedFile?.cheats?.size
        header.text = buildString {
            append(game.title).append('\n')
            append("Title ID: ${game.titleId}\n")
            append("Version: ${game.version ?: "unknown"}")
            game.region?.let { append("  •  $it") }
            append('\n')
            append(if (avail != null) "$avail cheat(s) available" else "Available cheats: not loaded")
            append("  •  ")
            append(if (inst != null) "$inst installed" else "installed: unknown")
        }
    }

    private fun refreshInfo() {
        val l = loaded
        val src = app.settings.source().displayName
        sourceInfo.text = when {
            l == null -> "Source: $src"
            l.fromCache -> "Source: $src  •  CACHED data saved ${app.repository.formatTime(l.fetchedAt)} (not checked against the source just now)"
            else -> "Source: $src  •  FRESH data downloaded ${app.repository.formatTime(l.fetchedAt)}"
        }
        val c = compat
        if (c?.message != null) Ui.show(compatBanner, Ui.Kind.WARN, c.message) else Ui.hide(compatBanner)
        rebuildRows()
    }

    private fun rebuildRows() {
        val remote = loaded?.file?.cheats.orEmpty().distinctBy { it.key }
        val inst = installedFile?.cheats.orEmpty()
        val byKey = LinkedHashMap<String, Cheat>().also { m -> inst.forEach { if (!m.containsKey(it.key)) m[it.key] = it } }
        val list = remote.map { CheatRow(it.key, it.name, it, byKey[it.key]) } +
            byKey.values.filter { i -> remote.none { it.key == i.key } }.map { CheatRow(it.key, it.name, null, it) }
        rows = list
        selected.retainAll(list.map { it.key }.toSet())
        adapter.notifyDataSetChanged()
        updateHeader()
    }

    private fun loadCheats() {
        if (busy) return
        setBusy(true)
        Ui.show(banner, Ui.Kind.INFO, "Checking for cheats…")
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { app.repository.load(game.titleId) }
            setBusy(false)
            when (res) {
                is CheatResult.Loaded -> {
                    loaded = res
                    compat = Compatibility.check(game.version, res.file.version)
                    when {
                        res.notice != null -> Ui.show(banner, Ui.Kind.WARN, res.notice)
                        else -> Ui.show(banner, Ui.Kind.OK, "${res.file.cheats.size} cheat(s) found.")
                    }
                }
                is CheatResult.Unavailable -> {
                    loaded = null; compat = null
                    Ui.show(banner, Ui.Kind.ERROR, res.message)
                }
            }
            refreshInfo()
        }
    }

    private fun reloadInstalled() {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                val inst = app.installer() ?: return@withContext null to "The cheats folder is not available. Check Settings > Azahar folder."
                try { inst.readExisting(game.titleId) to null } catch (e: Exception) { AppLog.e("Cheats", "Read existing failed", e); null to "Could not read the existing cheat file: ${e.message}" }
            }
            installedFile = r.first
            if (r.second != null && banner.visibility != View.VISIBLE) Ui.show(banner, Ui.Kind.ERROR, r.second!!)
            rebuildRows()
        }
    }

    // ---- actions --------------------------------------------------------------------------------
    private fun installerOrError(): CheatInstaller? {
        val i = app.installer()
        if (i == null) Ui.show(banner, Ui.Kind.ERROR, "The Azahar cheats folder is not available. Open Settings and select your Azahar folder (or a cheats folder) again.")
        return i
    }

    private fun install(cheats: List<Cheat>) {
        if (busy) return
        if (cheats.isEmpty()) { Toast.makeText(this, "Select at least one cheat first.", Toast.LENGTH_SHORT).show(); return }
        val installer = installerOrError() ?: return
        val c = compat
        if (c != null && c.status != Compatibility.Status.MATCH) {
            MaterialAlertDialogBuilder(this).setTitle("Compatibility warning")
                .setMessage((c.message ?: "Compatibility could not be verified.") + "\n\nInstall anyway?")
                .setPositiveButton("Install anyway") { _, _ -> checkConflicts(installer, cheats) }
                .setNegativeButton("Cancel", null).show()
        } else checkConflicts(installer, cheats)
    }

    private fun checkConflicts(installer: CheatInstaller, cheats: List<Cheat>) {
        setBusy(true)
        lifecycleScope.launch {
            val preview = withContext(Dispatchers.IO) { try { installer.preview(game.titleId, cheats) } catch (e: Exception) { AppLog.e("Cheats", "Preview failed", e); null } }
            setBusy(false)
            if (preview == null) { Ui.show(banner, Ui.Kind.ERROR, "Could not read the current cheat file, so nothing was changed."); return@launch }
            if (preview.conflicts.isEmpty()) { runInstall(installer, cheats, emptySet()); return@launch }
            val names = preview.conflicts.joinToString("\n") { "• ${it.name}" }
            MaterialAlertDialogBuilder(this@CheatsActivity).setTitle("Cheats already exist")
                .setMessage("These cheats are already installed with a different code:\n\n$names\n\nReplace them with the downloaded versions? (A backup is made first.)")
                .setPositiveButton("Replace") { _, _ -> runInstall(installer, cheats, preview.conflicts.map { it.key }.toSet()) }
                .setNeutralButton("Keep existing") { _, _ -> runInstall(installer, cheats, emptySet()) }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun runInstall(installer: CheatInstaller, cheats: List<Cheat>, replace: Set<String>) {
        setBusy(true)
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { installer.install(game.titleId, cheats, replace) }
            setBusy(false)
            showResult(r)
            reloadInstalled()
        }
    }

    private fun remove() {
        if (busy) return
        val names = rows.filter { it.key in selected && it.installed != null }.map { it.name }
        if (names.isEmpty()) { Toast.makeText(this, "Select installed cheats to remove.", Toast.LENGTH_SHORT).show(); return }
        val installer = installerOrError() ?: return
        MaterialAlertDialogBuilder(this).setTitle("Remove ${names.size} cheat(s)?")
            .setMessage(names.joinToString("\n") { "• $it" } + "\n\nA backup of the cheat file is made first.")
            .setPositiveButton("Remove") { _, _ ->
                setBusy(true)
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { installer.remove(game.titleId, names.toSet()) }
                    setBusy(false); showResult(r); reloadInstalled()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun onEnableToggle(row: CheatRow, on: Boolean) {
        val installer = installerOrError() ?: run { adapter.notifyDataSetChanged(); return }
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { installer.setEnabled(game.titleId, row.name, on) }
            if (r is InstallResult.Failure) showResult(r)
            reloadInstalled()
        }
    }

    private fun showResult(r: InstallResult) = when (r) {
        is InstallResult.Success -> Ui.show(banner, Ui.Kind.OK,
            r.message + (r.backupName?.let { "\nBackup saved: $it" } ?: "") + (if (r.notes.isNotEmpty()) "\n" + r.notes.joinToString("\n") else ""))
        is InstallResult.Failure -> Ui.show(banner, Ui.Kind.ERROR, r.message)
    }
}

class CheatAdapter(
    private val rows: () -> List<CheatRow>,
    private val selected: Set<String>,
    private val onSelect: (String, Boolean) -> Unit,
    private val onEnable: (CheatRow, Boolean) -> Unit,
) : RecyclerView.Adapter<CheatAdapter.VH>() {

    class VH(val root: LinearLayout, val check: CheckBox, val sw: MaterialSwitch, val sub: TextView) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = Ui.vbox(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 16), Ui.dp(ctx, 8))
        }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val check = CheckBox(ctx).apply { textSize = 16f }
        val sw = MaterialSwitch(ctx)
        top.addView(check, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(sw, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val sub = Ui.tv(ctx, "", 12f, secondary = true).apply { setPadding(Ui.dp(ctx, 40), 0, 0, 0) }
        root.addView(top, Ui.lp()); root.addView(sub, Ui.lp())
        return VH(root, check, sw, sub)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows()[position]
        h.check.setOnCheckedChangeListener(null)
        h.check.text = r.name
        h.check.isChecked = r.key in selected
        h.check.setOnCheckedChangeListener { _, on -> onSelect(r.key, on) }
        h.sub.text = when {
            r.remote == null -> "Installed (not in the current source) • ${r.codeCount} code line(s)"
            r.installed == null -> "Not installed • ${r.codeCount} code line(s)"
            r.differs -> "Installed, but the code differs from the source • ${r.codeCount} code line(s)"
            else -> "Installed • ${r.codeCount} code line(s)"
        }
        h.sw.setOnCheckedChangeListener(null)
        if (r.installed != null) {
            h.sw.visibility = View.VISIBLE
            h.sw.isChecked = r.installed.enabled
            h.sw.setOnCheckedChangeListener { _, on -> onEnable(r, on) }
        } else h.sw.visibility = View.GONE
    }

    override fun getItemCount() = rows().size
}

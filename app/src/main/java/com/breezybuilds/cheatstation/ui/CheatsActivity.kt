package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.cheats.CheatInstaller
import com.breezybuilds.cheatstation.cheats.PnachCheatInstaller
import com.breezybuilds.cheatstation.cheats.PnachBackupManager
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

/** One line in the cheat list: what the source offers and/or what is already in the emulator's cheat file. */
data class CheatRow(val key: String, val name: String, val remote: Cheat?, val installed: Cheat?) {
    val differs: Boolean get() = remote != null && installed != null && remote.normalizedCode != installed.normalizedCode
    val codeCount: Int get() = (remote ?: installed)?.codeLines?.size ?: 0
}

class CheatsActivity : AppCompatActivity() {
    private lateinit var game: Game
    private var isPs2 = false
    private lateinit var banner: TextView
    private lateinit var compatBanner: TextView
    private lateinit var header: TextView
    private lateinit var sourceInfo: TextView
    private lateinit var search: EditText
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
        isPs2 = intent.getStringExtra("platform") == "ps2"
        game = Game(intent.getStringExtra("title") ?: "Game", tid, intent.getStringExtra("region"), intent.getStringExtra("version"), null, false, if(isPs2) "PS2" else "3DS")

        val root = Ui.vbox(ctx)
        Ui.edgeToEdge(root)
        val toolbar = MaterialToolbar(ctx).apply { title = if (isPs2) "PS2 Cheats" else "Cheats" }
        toolbar.menu.add(Menu.NONE, 1, 1, "Refresh cheats")
        toolbar.menu.add(Menu.NONE, 2, 2, "Select all")
        toolbar.menu.add(Menu.NONE, 3, 3, "Select none")
        toolbar.menu.add(Menu.NONE, 5, 4, "Game information")
        toolbar.menu.add(Menu.NONE, 6, 5, "Install all available")
        val searchItem = toolbar.menu.add(Menu.NONE, 4, 0, "Search cheats").apply {
            setIcon(android.R.drawable.ic_menu_search)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                5 -> showGameInformation()
                6 -> {
                    val available = rows.mapNotNull { it.remote }
                    MaterialAlertDialogBuilder(this).setTitle("Install all available cheats?")
                        .setMessage("Install ${available.size} available cheats for ${game.title}? Existing conflicts will be reviewed before replacement.")
                        .setPositiveButton("Continue") { _, _ -> install(available) }
                        .setNegativeButton("Cancel", null).show()
                }
                4 -> {
                    val showing = search.visibility == View.VISIBLE
                    search.visibility = if (showing) View.GONE else View.VISIBLE
                    if (showing) {
                        search.text?.clear()
                        search.clearFocus()
                    } else {
                        search.requestFocus()
                    }
                }
                1 -> loadCheats()
                2 -> { selected.addAll(visibleRows().map { r -> r.key }); adapter.notifyDataSetChanged() }
                3 -> { selected.clear(); adapter.notifyDataSetChanged() }
            }
            true
        }
        toolbar.setBackgroundColor(0xFF111B2B.toInt())
        toolbar.setSubtitleTextColor(Ui.systemAccent(if (isPs2) "PS2" else "3DS"))
        toolbar.subtitle = if (isPs2) "PLAYSTATION 2" else "NINTENDO 3DS"
        root.addView(toolbar, Ui.lp())
        progress = LinearProgressIndicator(ctx).apply { isIndeterminate = true; visibility = View.GONE }
        progress.setIndicatorColor(Ui.systemAccent(if (isPs2) "PS2" else "3DS"))
        root.addView(progress, Ui.lp())

        header = Ui.tv(ctx, "", 18f, bold = true).apply {
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 4), Ui.dp(ctx, 16), Ui.dp(ctx, 4))
        }
        root.addView(header, Ui.lp())

        search = EditText(ctx).apply {
            hint = "Search cheats"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { adapter.notifyDataSetChanged() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        search.visibility = View.GONE
        root.addView(search, Ui.lp().apply {
            setMargins(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 6))
        })

        compatBanner = Ui.tv(ctx, "", 12f).apply {
            setTextColor(0xFFD9A35D.toInt())
            setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 4))
            visibility = View.GONE
        }
        root.addView(compatBanner, Ui.lp())
        banner = Ui.banner(ctx)
        root.addView(banner, Ui.lp())

        adapter = CheatAdapter(::visibleRows, selected, { key, on -> if (on) selected.add(key) else selected.remove(key) }, ::showCheatDetails)
        val list = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            this.adapter = this@CheatsActivity.adapter
            clipToPadding = false
            setPadding(0, 0, 0, Ui.dp(ctx, 20))
        }
        root.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6)) }
        val b1 = Ui.button(ctx, "Install Selected") { install(rows.filter { it.key in selected }.mapNotNull { it.remote }) }
        val b3 = Ui.button(ctx, "Remove Selected", filled = false) { remove() }
        buttons = listOf(b1, b3)
        buttons.forEach { bar.addView(it, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0) }) }
        root.addView(bar, Ui.lp())
        setContentView(root)

        updateHeader()
        // Cached copy first so the screen is useful immediately (and offline), then fresh data.
        if (isPs2) app.ps2Repository.cached(tid)?.let { loaded = it; compat = null; refreshInfo() }
        else app.repository.cached(tid)?.let { loaded = it; compat = Compatibility.check(game.version, it.file.version); refreshInfo() }
        reloadInstalled()
        loadCheats()
    }

    private fun showGameInformation() {
        val avail = loaded?.file?.cheats?.size
        val inst = installedFile?.cheats?.size
        val source = if (isPs2) app.ps2Settings.source().name else app.settings.source().displayName
        val version = game.version ?: "Unknown"
        val region = game.region ?: "Unknown"
        val cacheInfo = loaded?.let {
            if (it.fromCache) "Cached data" else "Fresh data"
        } ?: "Not loaded"

        val message = buildString {
            append("Game: ").append(game.title).append("\n\n")
            append("Title ID: ").append(game.titleId).append("\n")
            append("System: ").append(if (isPs2) "PlayStation 2" else "Nintendo 3DS").append("\n")
            append("Region: ").append(region).append("\n")
            append("Version: ").append(version).append("\n\n")
            append("Cheats available: ").append(avail ?: "Unknown").append("\n")
            append("Cheats installed: ").append(inst ?: "Unknown").append("\n\n")
            append("Source: ").append(source).append("\n")
            append("Data: ").append(cacheInfo)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Game information")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
    private fun setBusy(b: Boolean) {
        busy = b
        progress.visibility = if (b) View.VISIBLE else View.GONE
        buttons.forEach { it.isEnabled = !b }
    }

    private fun updateHeader() {
        val avail = loaded?.file?.cheats?.size
        val inst = installedFile?.cheats?.size
        header.text = buildString {
            append(game.title).append("\n")
            append(if (avail != null) "$avail ${if (avail == 1) "cheat" else "cheats"} available" else "Cheats not loaded")
            append(" • ")
            append(if (inst != null) "$inst installed" else "Installed: unknown")
        }
    }

    private fun refreshInfo() {
        val c = compat
        if (c?.message != null && c.status != Compatibility.Status.MATCH) {
            compatBanner.text = "⚠ ${c.message}"
            compatBanner.visibility = View.VISIBLE
        } else {
            compatBanner.text = ""
            compatBanner.visibility = View.GONE
        }
        rebuildRows()
    }

    private fun rebuildRows() {
        val remote = loaded?.file?.cheats.orEmpty().distinctBy { it.key }
        val inst = installedFile?.cheats.orEmpty()
        val byKey = LinkedHashMap<String, Cheat>().also { m ->
            inst.forEach { if (!m.containsKey(it.key)) m[it.key] = it }
        }
        val list = remote.map { CheatRow(it.key, it.name, it, byKey[it.key]) } +
            byKey.values.filter { i -> remote.none { it.key == i.key } }
                .map { CheatRow(it.key, it.name, null, it) }
        rows = list
        selected.retainAll(list.map { it.key }.toSet())
        adapter.notifyDataSetChanged()
        updateHeader()
    }

    private fun visibleRows(): List<CheatRow> {
        val q = search.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return rows
        return rows.filter { it.name.contains(q, ignoreCase = true) }
    }

    private fun loadCheats() {
        if (busy) return
        setBusy(true)
        Ui.show(banner, Ui.Kind.INFO, "Checking for cheats…")
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { if (isPs2) app.ps2Repository.load(game.titleId) else app.repository.load(game.titleId) }
            setBusy(false)
            when (res) {
                is CheatResult.Loaded -> {
                    if (isPs2) GameCheatStatus.put(this@CheatsActivity, "ps2", game.titleId, res.file.cheats.size)
                    loaded = res
                    compat = Compatibility.check(game.version, res.file.version)
                    when {
                        res.notice != null -> Ui.show(banner, Ui.Kind.WARN, res.notice)
                        else -> Ui.hide(banner)
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
                if (isPs2) { val inst = app.ps2Installer() ?: return@withContext null to "The PS2 cheats folder is not available. Select the emulator data folder again."; try { inst.readExisting(game.titleId) to null } catch (e: Exception) { AppLog.e("PS2Cheats", "Read existing failed", e); null to "Could not read the existing PNACH file: ${e.message}" } }
                else { val inst = app.installer() ?: return@withContext null to "The cheats folder is not available. Check Settings > emulator data folder."; try { inst.readExisting(game.titleId) to null } catch (e: Exception) { AppLog.e("Cheats", "Read existing failed", e); null to "Could not read the existing cheat file: ${e.message}" } }
            }
            installedFile = r.first
            if (r.second != null && banner.visibility != View.VISIBLE) Ui.show(banner, Ui.Kind.ERROR, r.second!!)
            rebuildRows()
        }
    }

    // ---- actions --------------------------------------------------------------------------------
    private fun installerOrError(): CheatInstaller? {
        if (isPs2) return null
        val i = app.installer()
        if (i == null) Ui.show(banner, Ui.Kind.ERROR, "The emulator cheats folder is not available. Open Settings and select your emulator data folder again.")
        return i
    }

    private fun ps2InstallerOrError(): PnachCheatInstaller? {
        if (!isPs2) return null
        val i = app.ps2Installer()
        if (i == null) Ui.show(banner, Ui.Kind.ERROR, "The PS2 cheats folder is not available. Select the emulator data folder again.")
        return i
    }

    private fun install(cheats: List<Cheat>) {
        if (busy) return
        if (cheats.isEmpty()) { Toast.makeText(this, "Select at least one cheat first.", Toast.LENGTH_SHORT).show(); return }
        if (isPs2) { val installer = ps2InstallerOrError() ?: return; checkPs2Conflicts(installer, cheats); return }
        val installer = installerOrError() ?: return
        val c = compat
        if (c != null && c.status != Compatibility.Status.MATCH) {
            MaterialAlertDialogBuilder(this).setTitle("Compatibility warning")
                .setMessage((c.message ?: "Compatibility could not be verified.") + "\n\nInstall anyway?")
                .setPositiveButton("Install anyway") { _, _ -> checkConflicts(installer, cheats) }
                .setNegativeButton("Cancel", null).show()
        } else checkConflicts(installer, cheats)
    }

    private fun checkPs2Conflicts(installer: PnachCheatInstaller, cheats: List<Cheat>) {
        setBusy(true); lifecycleScope.launch {
            val preview=withContext(Dispatchers.IO){try{installer.preview(game.titleId,cheats)}catch(e:Exception){null}};setBusy(false)
            if(preview==null){Ui.show(banner,Ui.Kind.ERROR,"Could not read the current PNACH file.");return@launch}
            if(preview.conflicts.isEmpty()){runPs2Install(installer,cheats,emptySet());return@launch}
            val names=preview.conflicts.joinToString("\n"){ "• ${it.name}" }
            MaterialAlertDialogBuilder(this@CheatsActivity).setTitle("PS2 cheats already exist").setMessage("These cheats have different installed codes:\n\n$names\n\nReplace them? A backup is made first.").setPositiveButton("Replace"){_,_->runPs2Install(installer,cheats,preview.conflicts.map{it.key}.toSet())}.setNeutralButton("Keep existing"){_,_->runPs2Install(installer,cheats,emptySet())}.setNegativeButton("Cancel",null).show()
        }
    }
    private fun runPs2Install(installer:PnachCheatInstaller,cheats:List<Cheat>,replace:Set<String>){setBusy(true);lifecycleScope.launch{val r=withContext(Dispatchers.IO){installer.install(game.titleId,cheats,replace)};setBusy(false);showResult(r);reloadInstalled()}}

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

    /** Details stay separate from bulk selection, with direct actions rather than nested menus. */
    private fun showCheatDetails(row: CheatRow) {
        if (busy) return
        val current = rows.firstOrNull { it.key == row.key } ?: row
        val installed = current.installed
        val source = current.remote
        val ctx = this
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 22), Ui.dp(ctx, 8), Ui.dp(ctx, 22), Ui.dp(ctx, 8))
        }
        fun label(value: String, size: Float = 14f) {
            content.addView(Ui.tv(ctx, value, size).apply {
                setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6))
            })
        }
        label(buildString {
            append(if (installed != null) "Installed" else "Not installed")
            if (installed != null) append(if (installed.enabled) " · Enabled" else " · Disabled")
            append(if (source == null) " · Local cheat" else " · Database cheat")
        })
        if (current.differs) label("Installed code differs from the online version")
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(current.name)
            .setView(ScrollView(ctx).apply {
                isFillViewport = false
                addView(content)
            })
            .setNegativeButton("Close", null)
            .create()
        // Primary actions appear immediately below status, before technical details.
        fun action(title: String, operation: () -> Unit) {
            content.addView(Ui.button(ctx, title, filled = false) {
                dialog.dismiss()
                operation()
            }, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(ctx, 12)
            })
        }
        if (source != null) action(if (installed == null) "Install cheat" else "Reinstall cheat") {
            install(listOf(source))
        }
        if (installed != null) {
            action(if (installed.enabled) "Disable cheat" else "Enable cheat") {
                onEnableToggle(current, !installed.enabled)
            }
            action("Remove from emulator") {
                removeKeys(setOf(current.key))
            }
        }
        val cheat = installed ?: source
        cheat?.description?.takeIf { it.isNotBlank() }?.let { label(it) }
        val codeText = cheat?.lines?.joinToString("\n").orEmpty()
        if (codeText.isNotBlank()) {
            val code = Ui.tv(ctx, codeText, 12f).apply {
                setTextIsSelectable(true)
                setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 10))
                setBackgroundColor(0xFF182337.toInt())
                visibility = View.GONE
            }
            val copy = Ui.button(ctx, "Copy code", filled = false) {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(current.name, codeText))
                Toast.makeText(ctx, "Cheat code copied", Toast.LENGTH_SHORT).show()
            }.apply { visibility = View.GONE }
            val toggle = Ui.button(ctx, "View cheat code", filled = false) { }
            toggle.setOnClickListener {
                val expanded = code.visibility != View.VISIBLE
                code.visibility = if (expanded) View.VISIBLE else View.GONE
                copy.visibility = if (expanded) View.VISIBLE else View.GONE
                toggle.text = if (expanded) "Hide cheat code" else "View cheat code"
            }
            content.addView(toggle, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 12) })
            content.addView(code, Ui.lp())
            content.addView(copy, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog.show()
    }

    private fun remove() = removeKeys(selected.toSet())

    private fun removeKeys(keys: Set<String>) {
        if (busy) return
        val names = rows.filter { it.key in keys && it.installed != null }.map { it.name }
        if (names.isEmpty()) { Toast.makeText(this, "Select installed cheats to remove.", Toast.LENGTH_SHORT).show(); return }
        if (isPs2) { val installer=ps2InstallerOrError() ?: return; MaterialAlertDialogBuilder(this).setTitle("Remove ${names.size} PS2 cheat(s)?").setMessage(names.joinToString("\n") { "• $it" } + "\n\nA backup of the PNACH file is made first.").setPositiveButton("Remove") { _, _ -> setBusy(true); lifecycleScope.launch { val r=withContext(Dispatchers.IO){installer.remove(game.titleId,names.toSet())}; setBusy(false); showResult(r); reloadInstalled() } }.setNegativeButton("Cancel",null).show(); return }
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
        if (isPs2) { val installer=ps2InstallerOrError() ?: run { adapter.notifyDataSetChanged(); return }; lifecycleScope.launch { val r=withContext(Dispatchers.IO){installer.setEnabled(game.titleId,row.name,on)}; if(r is InstallResult.Failure)showResult(r); reloadInstalled() }; return }
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
    private val onDetails: (CheatRow) -> Unit,
) : RecyclerView.Adapter<CheatAdapter.VH>() {

    class VH(
        val root: LinearLayout,
        val check: CheckBox,
        val name: TextView,
        val badge: TextView,
        val details: TextView,
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = Ui.vbox(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
        }
        val line = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val check = CheckBox(ctx).apply { minWidth = Ui.dp(ctx, 44) }
        val name = Ui.tv(ctx, "", 14f).apply { setPadding(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 5), 0) }
        val badge = Ui.tv(ctx, "", 11f, bold = true).apply {
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 5), Ui.dp(ctx, 6), Ui.dp(ctx, 5))
        }
        val details = Ui.tv(ctx, "›", 25f).apply {
            setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6), 0)
            contentDescription = "Open cheat details"
        }
        line.addView(check, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        line.addView(name, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(badge, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        line.addView(details, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(line, Ui.lp())
        root.addView(View(ctx).apply { setBackgroundColor(0xFF263345.toInt()) }, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 1)))
        return VH(root, check, name, badge, details)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows()[position]
        h.check.setOnCheckedChangeListener(null)
        h.check.isChecked = r.key in selected
        h.check.setOnCheckedChangeListener { _, checked -> onSelect(r.key, checked) }
        h.name.text = r.name
        h.badge.text = if (r.installed != null) "Installed" else "Not installed"
        h.badge.setTextColor(if (r.installed != null) 0xFF66DFA6.toInt() else 0xFFB3BED0.toInt())
        h.details.setTextColor(0xFF94A3B8.toInt())
        h.name.setOnClickListener { onDetails(r) }
        h.badge.setOnClickListener { onDetails(r) }
        h.details.setOnClickListener { onDetails(r) }
    }

    override fun getItemCount() = rows().size
}

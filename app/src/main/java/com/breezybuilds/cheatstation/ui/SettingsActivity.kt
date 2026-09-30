package com.breezybuilds.cheatstation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.breezybuilds.cheatstation.BuildConfigVersion
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.cheats.BackupManager
import com.breezybuilds.cheatstation.data.DatabaseUpdater
import com.breezybuilds.cheatstation.provider.CheatSourceConfig
import com.breezybuilds.cheatstation.provider.RecommendedCheatSources
import com.breezybuilds.cheatstation.storage.StorageManager
import com.breezybuilds.cheatstation.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var content: android.widget.LinearLayout
    private lateinit var banner: TextView

    private val pickRoot = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) accept(StorageManager.Slot.ROOT, u) }
    private val pickCheats = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) accept(StorageManager.Slot.CHEATS, u) }
    private val pickGames = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) accept(StorageManager.Slot.GAMES, u) }
    private val pickPs2Root = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptPs2Root(u) }
    private val pickPs2Games = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptPs2Games(u) }
    private val pickPs2Transfer = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptPs2Transfer(u) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val root = Ui.vbox(ctx)
        Ui.edgeToEdge(root)
        root.addView(MaterialToolbar(ctx).apply { title = "Settings" }, Ui.lp())
        banner = Ui.banner(ctx)
        root.addView(banner, Ui.lp())
        content = Ui.vbox(ctx, 16)
        root.addView(ScrollView(ctx).apply { addView(content) }, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
        when (intent.getStringExtra("open_section")) {
            "source" -> window.decorView.post { chooseRecommendedSource() }
        }
    }

    private fun section(title: String) {
        content.addView(Ui.tv(this, title, 13f, bold = true).apply { setTextColor(0xFFE5533D.toInt()) }, Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 22) })
    }
    private fun line(text: String, secondary: Boolean = false) =
        content.addView(Ui.tv(this, text, 14f, secondary = secondary), Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 4) })
    private fun btn(label: String, filled: Boolean = false, onClick: () -> Unit) =
        content.addView(Ui.button(this, label, filled, onClick), Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 6) })

    private fun render() {
        content.removeAllViews()
        val st = app.storage
        val src = app.settings.source()

        section("3DS EMULATOR STORAGE")
        line("Emulator data folder: " + st.describe(st.rootUri))
        line("Cheats folder: " + if (st.cheatsUri != null) st.describe(st.cheatsUri) else "automatic (<data folder>/cheats)", true)
        line("Games folder: " + st.describe(st.gamesUri), true)
        btn("Change emulator data folder", true) { pickRoot.launch(st.pickerHint()) }
        btn("Select a different cheats folder…") { pickCheats.launch(st.rootUri) }
        btn("Select a games folder (CIA/3DS/CXI)…") { pickGames.launch(null) }
        if (st.cheatsUri != null) btn("Use automatic cheats folder") { st.clear(StorageManager.Slot.CHEATS); render() }
        if (st.gamesUri != null) btn("Forget games folder") { st.clear(StorageManager.Slot.GAMES); render() }

        section("PLAYSTATION 2 STORAGE")
        val ps2 = app.ps2Storage
        val detected = ps2.detectEmulator()
        line("Emulator: " + (detected?.name ?: "Not detected"))
        line("Emulator data: " + (ps2.manualPath ?: detected?.path ?: "Not configured"), true)
        line("Games folder: " + (ps2.gamesUri?.let { ps2.describe(it) } ?: "Not configured"), true)
        line("Transfer folder: " + (ps2.transferUri?.let { ps2.describe(it) } ?: "Not configured"), true)
        btn("Auto-detect NetherSX2", true) { render() }
        btn("Select emulator data folder") { pickPs2Root.launch(null) }
        btn("Set emulator path manually") { ps2ManualPathDialog() }
        btn("Select PS2 games folder") { pickPs2Games.launch(null) }
        btn("Select transfer folder") { pickPs2Transfer.launch(null) }
        section("CHEAT SOURCE")
        line("Current source: ${src.displayName}", false)
        line("Branch ${src.branch}, folder \"${src.basePath}\", files ${src.fileNamePattern}", true)
        line("Recommended sources are built in. FlagBrew / Sharkive is selected by default.", true)
        btn("Browse recommended sources…", true) { chooseRecommendedSource() }
        btn("Edit custom GitHub source…") { editSource() }
        btn("Use default Sharkive source") { app.settings.resetSource(); render(); note(Ui.Kind.OK, "Sharkive is now the active cheat source.") }

        section("CHEAT DATABASE")
        line("Cached cheat data: ${app.cache.cheatDataSize() / 1024} KB", true)
        btn("Refresh cheat database now", true) { refreshNow() }
        btn("Clear cached cheat data") {
            MaterialAlertDialogBuilder(this).setTitle("Clear cached cheat data?")
                .setMessage("Downloaded cheat lists are removed from this app. Cheats already installed in the emulator are not touched.")
                .setPositiveButton("Clear") { _, _ -> val n = app.cache.clearCheatData(); render(); note(Ui.Kind.OK, "Cleared ${n / 1024} KB of cached data.") }
                .setNegativeButton("Cancel", null).show()
        }
        content.addView(MaterialSwitch(this).apply {
            text = "Automatically check for cheat updates"
            isChecked = app.settings.autoUpdate
            setOnCheckedChangeListener { _, on -> app.settings.autoUpdate = on }
        }, Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        section("BACKUPS")
        line("A backup is created automatically before any cheat file is changed. They are stored in the \"backups\" folder inside your cheats folder.", true)
        btn("Manage backups…") { manageBackups() }

        section("DEBUGGING")
        content.addView(MaterialSwitch(this).apply {
            text = "Verbose debug log"
            isChecked = app.settings.debugLog
            setOnCheckedChangeListener { _, on -> app.settings.debugLog = on; AppLog.verbose = on }
        }, Ui.lp())
        btn("View debug log") { viewLog() }

        section("ABOUT")
        line("Breezy's Cheat Station ${BuildConfigVersion.name(this)}")
        line("A companion utility that finds your 3DS games and installs compatible cheat files for them. It works alongside supported 3DS emulators rather than belonging to any one emulator project. Cheat codes come from third-party sources; use them at your own risk.", true)
    }

    private fun note(kind: Ui.Kind, msg: String) = Ui.show(banner, kind, msg)

    private fun accept(slot: StorageManager.Slot, uri: Uri) {
        val err = app.storage.accept(slot, uri)
        if (err == null) { render(); note(Ui.Kind.OK, "Folder saved."); return }
        MaterialAlertDialogBuilder(this).setTitle("Folder problem").setMessage(err)
            .setPositiveButton("Use anyway") { _, _ -> if (slot == StorageManager.Slot.ROOT) app.storage.forceRoot(uri); render() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun acceptPs2Root(uri: Uri) {
        val err = app.ps2Storage.acceptRoot(uri)
        if (err == null) { render(); note(Ui.Kind.OK, "PS2 emulator folder saved."); return }
        MaterialAlertDialogBuilder(this).setTitle("NetherSX2 folder cannot be used").setMessage(err)
            .setPositiveButton("Choose again") { _, _ -> pickPs2Root.launch(null) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun acceptPs2Games(uri: Uri) {
        val err = app.ps2Storage.acceptGames(uri)
        if (err == null) { render(); note(Ui.Kind.OK, "PS2 games folder saved."); return }
        MaterialAlertDialogBuilder(this).setTitle("Games folder problem").setMessage(err)
            .setPositiveButton("Choose again") { _, _ -> pickPs2Games.launch(null) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun acceptPs2Transfer(uri: Uri) {
        val err = app.ps2Storage.acceptTransfer(uri)
        if (err == null) { render(); note(Ui.Kind.OK, "PS2 transfer folder saved."); return }
        MaterialAlertDialogBuilder(this).setTitle("Transfer folder problem").setMessage(err)
            .setPositiveButton("Choose again") { _, _ -> pickPs2Transfer.launch(null) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun ps2ManualPathDialog() {
        val input = EditText(this).apply {
            hint = "/storage/emulated/0/Android/data/xyz.aethersx2.android/files"
            setSingleLine()
            setText(app.ps2Storage.manualPath ?: "")
        }
        val box = Ui.vbox(this, 12)
        box.addView(input, Ui.lp())
        MaterialAlertDialogBuilder(this)
            .setTitle("PS2 emulator path")
            .setMessage("Enter the NetherSX2/AetherSX2 data path manually if Android does not allow the folder picker to access it.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val err = app.ps2Storage.saveManualPath(input.text.toString())
                if (err == null) { render(); note(Ui.Kind.OK, "PS2 emulator path saved.") }
                else note(Ui.Kind.ERROR, err)
            }
            .setNeutralButton("Clear") { _, _ ->
                app.ps2Storage.clearManualPath()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseRecommendedSource() {
        val sources = RecommendedCheatSources.all
        val current = app.settings.source().id
        var selected = sources.indexOfFirst { it.config.id == current }.coerceAtLeast(0)
        val labels = sources.mapIndexed { index, it ->
            val marker = if (it.config.id == current) "✓ " else ""
            marker + it.name + "\n" + it.description
        }.toTypedArray()
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle("Recommended cheat sources")
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setPositiveButton("Use selected") { _, _ ->
                app.settings.saveSource(sources[selected].config)
                render()
                note(Ui.Kind.OK, "Using ${sources[selected].name}. Refresh the cheat database to download from it.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editSource() {
        val ctx = this
        val cur = app.settings.source()
        val box = Ui.vbox(ctx, 20)
        fun field(hint: String, value: String, pw: Boolean = false) = EditText(ctx).apply {
            this.hint = hint; setText(value); setSingleLine()
            if (pw) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }.also { box.addView(it, Ui.lp()) }
        val owner = field("GitHub owner", cur.owner)
        val repo = field("Repository", cur.repo)
        val branch = field("Branch (HEAD = default branch)", cur.branch)
        val path = field("Folder inside the repository", cur.basePath)
        val pattern = field("File name pattern, e.g. {TITLEID}.txt", cur.fileNamePattern)
        val token = field("GitHub token (optional, raises rate limits)", cur.token.orEmpty(), true)
        val dlg = MaterialAlertDialogBuilder(ctx).setTitle("Cheat source").setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).show()
        dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val c = CheatSourceConfig(owner.text.toString().trim(), repo.text.toString().trim(), branch.text.toString().trim(),
                path.text.toString().trim(), pattern.text.toString().trim(), token.text.toString().trim().ifBlank { null })
            val err = c.validate()
            if (err != null) { pattern.error = err; return@setOnClickListener }
            app.settings.saveSource(c)
            dlg.dismiss(); render(); note(Ui.Kind.OK, "Cheat source saved. Use \"Refresh cheat database now\" to download from it.")
        }
    }

    private fun refreshNow() {
        val games = (app.cache.loadGames() + app.cache.loadManual()).distinctBy { it.titleId }
        if (games.isEmpty()) { note(Ui.Kind.WARN, "No games known yet. Scan for games on the main screen first."); return }
        note(Ui.Kind.INFO, "Updating…")
        lifecycleScope.launch {
            val s = DatabaseUpdater.updateAll(app.repository, games) { i, n -> runOnUiThread { note(Ui.Kind.INFO, "Updating $i/$n…") } }
            app.settings.lastAutoCheck = System.currentTimeMillis()
            render()
            note(if (s.failed == 0 && s.message == null) Ui.Kind.OK else Ui.Kind.WARN, s.describe())
        }
    }

    // ---- backups --------------------------------------------------------------------------------
    private fun manageBackups() {
        val bm = app.backups() ?: run { note(Ui.Kind.ERROR, "The cheats folder is not available. Select your emulator data folder first."); return }
        lifecycleScope.launch {
            val names = withContext(Dispatchers.IO) { bm.list() }
            if (names.isEmpty()) { note(Ui.Kind.INFO, "There are no backups yet."); return@launch }
            val labels = names.map { n ->
                val m = Regex("^([0-9A-F]{16})_cheats_backup_(\\d{4})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2})").find(n)
                if (m == null) n else { val g = m.groupValues; "${g[1]}\n${g[2]}-${g[3]}-${g[4]} ${g[5]}:${g[6]}:${g[7]}" }
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@SettingsActivity).setTitle("${names.size} backup(s)")
                .setItems(labels) { _, i -> backupActions(bm, names[i]) }
                .setNeutralButton("Delete all") { _, _ -> confirmDeleteAll(bm, names) }
                .setNegativeButton("Close", null).show()
        }
    }

    private fun backupActions(bm: BackupManager, name: String) {
        MaterialAlertDialogBuilder(this).setTitle("Backup").setMessage(name)
            .setPositiveButton("Restore") { _, _ ->
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { try { "Restored the cheats for ${bm.restore(name)}." to true } catch (e: Exception) { AppLog.e("Backup", "Restore failed", e); "Restore failed: ${e.message}" to false } }
                    note(if (r.second) Ui.Kind.OK else Ui.Kind.ERROR, r.first)
                }
            }
            .setNeutralButton("Delete") { _, _ -> lifecycleScope.launch { val ok = withContext(Dispatchers.IO) { bm.delete(name) }; note(if (ok) Ui.Kind.OK else Ui.Kind.ERROR, if (ok) "Backup deleted." else "Could not delete the backup.") } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteAll(bm: BackupManager, names: List<String>) {
        MaterialAlertDialogBuilder(this).setTitle("Delete all ${names.size} backups?").setMessage("This cannot be undone.")
            .setPositiveButton("Delete all") { _, _ ->
                lifecycleScope.launch {
                    val n = withContext(Dispatchers.IO) { names.count { bm.delete(it) } }
                    note(Ui.Kind.OK, "Deleted $n backup(s).")
                }
            }.setNegativeButton("Cancel", null).show()
    }

    // ---- log ------------------------------------------------------------------------------------
    private fun viewLog() {
        val tv = Ui.tv(this, AppLog.dump().ifBlank { "(log is empty)" }, 11f, mono = true).apply { setTextIsSelectable(true); setPadding(Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 8), Ui.dp(this@SettingsActivity, 16), 0) }
        MaterialAlertDialogBuilder(this).setTitle("Debug log").setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Copy") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Breezy's Cheat Station log", AppLog.dump()))
                note(Ui.Kind.OK, "Log copied to the clipboard.")
            }
            .setNeutralButton("Clear") { _, _ -> AppLog.clear() }
            .setNegativeButton("Close", null).show()
    }
}

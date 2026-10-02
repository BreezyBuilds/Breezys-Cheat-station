package com.breezybuilds.cheatstation.ui

import com.breezybuilds.cheatstation.App

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
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
import com.breezybuilds.cheatstation.storage.toStatus
import com.breezybuilds.cheatstation.storage.DolphinStorage
import com.breezybuilds.cheatstation.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var content: android.widget.LinearLayout
    private lateinit var banner: TextView
    private lateinit var toolbar: MaterialToolbar

    private enum class Page {
        HOME, SYSTEMS, THREE_DS, PS2, WII, GAMECUBE, CHEATS, BACKUPS, ADVANCED, ABOUT
    }

    private var page = Page.HOME

    private val pickRoot = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) accept(StorageManager.Slot.ROOT, u) }
    private val pickCheats = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) accept(StorageManager.Slot.CHEATS, u) }
    private val pickGames = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) accept(StorageManager.Slot.GAMES, u) }
    private val pickPs2Root = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptPs2Root(u) }
    private val pickPs2Games = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptPs2Games(u) }
    private val pickPs2Transfer = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptPs2Transfer(u) }
    private val pickDolphinUser = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptDolphinUser(u) }
    private val pickWiiGames = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptWiiGames(u) }
    private val pickGameCubeGames = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) acceptGameCubeGames(u) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val root = Ui.vbox(ctx)
        Ui.edgeToEdge(root)

        toolbar = MaterialToolbar(ctx).apply {
            title = "Settings"
            setNavigationOnClickListener { goBack() }
        }
        root.addView(toolbar, Ui.lp())

        banner = Ui.banner(ctx)
        root.addView(banner, Ui.lp())

        content = Ui.vbox(ctx, 16)
        root.addView(
            ScrollView(ctx).apply {
                addView(content)
                isFillViewport = true
            },
            Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        render()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        when (intent.getStringExtra("open_section")) {
            "source" -> {
                page = Page.CHEATS
                window.decorView.post {
                    render()
                    chooseRecommendedSource()
                }
            }
        }
    }

    private fun navigate(target: Page) {
        page = target
        render()
    }

    private fun goBack() {
        page = when (page) {
            Page.HOME -> { finish(); return }
            Page.SYSTEMS, Page.CHEATS, Page.BACKUPS, Page.ADVANCED, Page.ABOUT -> Page.HOME
            Page.THREE_DS, Page.PS2, Page.WII, Page.GAMECUBE -> Page.SYSTEMS
        }
        render()
    }

    private fun pageTitle(): String = when (page) {
        Page.HOME -> "Settings"
        Page.SYSTEMS -> "Systems"
        Page.THREE_DS -> "3DS"
        Page.PS2 -> "PlayStation 2"
        Page.WII -> "Wii"
        Page.GAMECUBE -> "GameCube"
        Page.CHEATS -> "Cheats & Sources"
        Page.BACKUPS -> "Backups"
        Page.ADVANCED -> "Advanced"
        Page.ABOUT -> "About"
    }

    private fun line(text: String, secondary: Boolean = false) =
        content.addView(Ui.tv(this, text, 14f, secondary = secondary), Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 4) })
    private fun btn(label: String, filled: Boolean = false, onClick: () -> Unit) =
        content.addView(Ui.button(this, label, filled, onClick), Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 6) })

    private fun chooseDolphinRepositories() {
        val settings = (application as App).dolphinSettings
        val sources = settings.allSources()
        val selected = settings.sources().map { it.id }.toMutableSet()
        val labels = sources.map { it.name }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Dolphin cheat repositories")
            .setMultiChoiceItems(labels, sources.map { it.id in selected }.toBooleanArray()) { _, which, checked ->
                if (checked) selected += sources[which].id else selected -= sources[which].id
            }
            .setPositiveButton("Save") { _, _ ->
                settings.saveSources(sources.filter { it.id in selected })
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addDolphinRepository() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SettingsActivity, 24), Ui.dp(this@SettingsActivity, 8), Ui.dp(this@SettingsActivity, 24), 0)
        }

        val url = android.widget.EditText(this).apply {
            hint = "GitHub repository URL"
        }

        val branch = android.widget.EditText(this).apply {
            hint = "Branch (optional)"
        }

        val path = android.widget.EditText(this).apply {
            hint = "Cheat folder (optional)"
        }

        layout.addView(url)
        layout.addView(branch)
        layout.addView(path)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add GitHub repository")
            .setMessage("Add a GitHub repository containing Dolphin-compatible .ini cheat files.")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val repoUrl = url.text.toString().trim()
                val repoBranch = branch.text.toString().trim().ifBlank { "master" }
                val repoPath = path.text.toString().trim().trim('/')

                val match = Regex("github\\.com/([^/]+)/([^/#]+)").find(repoUrl)

                if (match == null) {
                    note(Ui.Kind.ERROR, "Enter a valid GitHub repository URL.")
                    return@setPositiveButton
                }

                val owner = match.groupValues[1]
                val repo = match.groupValues[2].removeSuffix(".git")

                val source = com.breezybuilds.cheatstation.provider.DolphinCheatSource(
                    name = "$owner/$repo",
                    description = "Custom GitHub Dolphin cheat repository.",
                    owner = owner,
                    repo = repo,
                    branch = repoBranch,
                    path = repoPath,
                    custom = true
                )

                app.dolphinSettings.addCustomSource(source)
                render()
                note(Ui.Kind.OK, "Added ${source.name}.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun choose3dsEmulator() {
        val emulators =
            com.breezybuilds.cheatstation.emulator.EmulatorDetector
                .detect3dsEmulators(this)

        if (emulators.isEmpty()) {
            Ui.show(
                banner,
                Ui.Kind.INFO,
                "No supported 3DS emulator was detected."
            )
            return
        }

        val selected = app.storage.selected3dsEmulatorPackage
        val checked = emulators
            .map { it.packageName }
            .indexOf(selected)
            .coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("3DS emulator")
            .setSingleChoiceItems(
                emulators.map {
                    "${it.name}  •  ${it.packageName}"
                }.toTypedArray(),
                checked
            ) { dialog, which ->
                val emulator = emulators[which]

                app.storage.setSelected3dsEmulator(
                    emulator.packageName
                )

                render()
                Ui.show(
                    banner,
                    Ui.Kind.OK,
                    "${emulator.name} selected."
                )

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun autoDetect3ds() {
        val st = app.storage
        val detected =
            com.breezybuilds.cheatstation.emulator.EmulatorDetector
                .detect3dsEmulator(this)

        if (detected == null) {
            Ui.show(
                banner,
                Ui.Kind.INFO,
                "No supported 3DS emulator was detected. Select your emulator manually."
            )
            return
        }

        // Select the detected emulator before looking for storage.
        st.setSelected3dsEmulator(detected.packageName)

        // Reuse storage already associated with this exact emulator.
        if (st.autoDetectExistingGrant() != null) {
            Ui.show(
                banner,
                Ui.Kind.OK,
                "✓ ${detected.name} detected and its existing folder access was reused."
            )
            render()
            return
        }

        // Try direct filesystem discovery for this selected emulator.
        val detectedFolder = st.autoDetect3dsFolder()
        if (detectedFolder != null) {
            Ui.show(
                banner,
                Ui.Kind.OK,
                "✓ ${detected.name} detected: ${detectedFolder.path}"
            )
            render()
            return
        }

        Ui.show(
            banner,
            Ui.Kind.INFO,
            "${detected.name} detected. Select its data folder to give Breezy's Cheat Station access."
        )

        pickRoot.launch(st.pickerHint())
    }

    private fun card(title: String, subtitle: String, onClick: () -> Unit) {
        content.addView(
            Ui.settingsCard(this, title, subtitle, onClick),
            Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 10) }
        )
    }

    private fun subsection(title: String) {
        content.addView(
            Ui.tv(this, title, 12f, bold = true).apply {
                setPadding(Ui.dp(this@SettingsActivity, 2), Ui.dp(this@SettingsActivity, 18), 0, Ui.dp(this@SettingsActivity, 2))
            },
            Ui.lp()
        )
    }

    private fun render() {
        content.removeAllViews()
        toolbar.title = pageTitle()
        toolbar.navigationIcon = if (page == Page.HOME) null else androidx.appcompat.content.res.AppCompatResources.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        when (page) {
            Page.HOME -> renderHome()
            Page.SYSTEMS -> renderSystems()
            Page.THREE_DS -> render3dsSettings()
            Page.PS2 -> renderPs2Settings()
            Page.WII -> renderDolphinSystemSettings(DolphinSystem.WII)
            Page.GAMECUBE -> renderDolphinSystemSettings(DolphinSystem.GAMECUBE)
            Page.CHEATS -> renderCheatSettings()
            Page.BACKUPS -> renderBackupSettings()
            Page.ADVANCED -> renderAdvancedSettings()
            Page.ABOUT -> renderAbout()
        }
    }

    private fun renderHome() {
        content.addView(Ui.tv(this, "Configure how Breezy's Cheat Station works", 15f, secondary = true), Ui.lp())
        subsection("SYSTEMS")
        card("Systems", "Emulators, game folders and system-specific storage") { navigate(Page.SYSTEMS) }

        subsection("APP")
        card("Cheats & Sources", "Cheat source, database and Dolphin repositories") { navigate(Page.CHEATS) }
        card("Backups", "Manage automatic cheat backups") { navigate(Page.BACKUPS) }
        card("Advanced", "Updates, debugging and diagnostics") { navigate(Page.ADVANCED) }
        card("About", "Version and application information") { navigate(Page.ABOUT) }
    }

    private fun renderSystems() {
        val st = app.storage

        // 3DS station bay
        val detected3ds =
            com.breezybuilds.cheatstation.emulator.EmulatorDetector
                .detect3dsEmulators(this)

        val selected3ds =
            detected3ds.firstOrNull {
                it.packageName == st.selected3dsEmulatorPackage
            }

        val threeDsCard = Ui.statusPanel(
            this,
            "3DS",
            st.threeDsStorageState().toStatus(),
            selected3ds?.name
        )

        threeDsCard.setOnClickListener {
            navigate(Page.THREE_DS)
        }

        content.addView(threeDsCard, Ui.lp())

        // PlayStation 2 station bay
        val ps2 = app.ps2Storage.detectEmulator()

        val ps2Card = Ui.statusPanel(
            this,
            "PS2",
            app.ps2Storage.ps2StorageState().toStatus(),
            ps2?.name
        )

        ps2Card.setOnClickListener {
            navigate(Page.PS2)
        }

        content.addView(
            ps2Card,
            Ui.lp().apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
        )

        // Dolphin station bays
        val dolphin = app.dolphinStorage.detectDolphin()

        val wiiState = when {
            dolphin == null ->
                com.breezybuilds.cheatstation.storage.StorageState.NOT_CONFIGURED

            app.dolphinStorage.wiiGamesUri != null ->
                com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE

            else ->
                com.breezybuilds.cheatstation.storage.StorageState.DETECTED
        }

        val wiiStatus = com.breezybuilds.cheatstation.storage.StorageStatus(
            state = wiiState,
            title = when (wiiState) {
                com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE -> "READY"
                com.breezybuilds.cheatstation.storage.StorageState.DETECTED -> "SETUP NEEDED"
                else -> "NOT DETECTED"
            },
            detail = when (wiiState) {
                com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE ->
                    "Dolphin detected and Wii games folder is connected."

                com.breezybuilds.cheatstation.storage.StorageState.DETECTED ->
                    "Dolphin detected; connect a Wii games folder."

                else ->
                    "Dolphin emulator was not detected."
            }
        )

        val wiiCard = Ui.statusPanel(
            this,
            "WII",
            wiiStatus,
            dolphin?.name
        )

        wiiCard.setOnClickListener {
            navigate(Page.WII)
        }

        content.addView(
            wiiCard,
            Ui.lp().apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
        )

        val gameCubeState = when {
            dolphin == null ->
                com.breezybuilds.cheatstation.storage.StorageState.NOT_CONFIGURED

            app.dolphinStorage.gameCubeGamesUri != null ->
                com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE

            else ->
                com.breezybuilds.cheatstation.storage.StorageState.DETECTED
        }

        val gameCubeStatus = com.breezybuilds.cheatstation.storage.StorageStatus(
            state = gameCubeState,
            title = when (gameCubeState) {
                com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE -> "READY"
                com.breezybuilds.cheatstation.storage.StorageState.DETECTED -> "SETUP NEEDED"
                else -> "NOT DETECTED"
            },
            detail = when (gameCubeState) {
                com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE ->
                    "Dolphin detected and GameCube games folder is connected."

                com.breezybuilds.cheatstation.storage.StorageState.DETECTED ->
                    "Dolphin detected; connect a GameCube games folder."

                else ->
                    "Dolphin emulator was not detected."
            }
        )

        val gameCubeCard = Ui.statusPanel(
            this,
            "GAMECUBE",
            gameCubeStatus,
            dolphin?.name
        )

        gameCubeCard.setOnClickListener {
            navigate(Page.GAMECUBE)
        }

        content.addView(
            gameCubeCard,
            Ui.lp().apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
        )
    }

    private fun render3dsSettings() {
        val st = app.storage
        val detected = com.breezybuilds.cheatstation.emulator.EmulatorDetector.detect3dsEmulators(this)
        val selected = detected.firstOrNull { it.packageName == st.selected3dsEmulatorPackage }
        val status = st.threeDsStorageState().toStatus()

        content.addView(
            Ui.statusPanel(
                this,
                "3DS",
                status,
                selected?.name
            ),
            Ui.lp()
        )

        subsection("EMULATOR")
        card("Emulator", selected?.name ?: "Not selected", ::choose3dsEmulator)
        if (selected != null) line("Package: ${selected.packageName}", true)

        subsection("STORAGE")
        line("Emulator data: ${st.describe3dsRoot()}")
        line("Games folder: ${st.describe(st.gamesUri)}", true)
        line(
            "Cheats folder: ${
                if (st.cheatsUri != null) st.describe(st.cheatsUri)
                else "Automatic (data folder/cheats)"
            }",
            true
        )

        btn("🔍 Auto-detect 3DS emulator", true) { autoDetect3ds() }
        btn("Change emulator data folder") { pickRoot.launch(st.pickerHint()) }

        if (selected != null && st.selected3dsRootUri() != null) {
            btn("Forget emulator data folder") {
                st.selected3dsEmulatorPackage?.let { st.clearEmulatorRoot(it) }
                render()
            }
        }

        btn("Select games folder") { pickGames.launch(null) }
        btn("Select cheats folder") { pickCheats.launch(st.rootUri) }

        if (st.cheatsUri != null) {
            btn("Use automatic cheats folder") {
                st.clear(StorageManager.Slot.CHEATS)
                render()
            }
        }

        if (st.gamesUri != null) {
            btn("Forget games folder") {
                st.clear(StorageManager.Slot.GAMES)
                render()
            }
        }

        subsection("ACTIONS")
        btn("Choose a different emulator") { choose3dsEmulator() }
    }

    private fun renderPs2Settings() {
        val ps2 = app.ps2Storage
        val detected = ps2.detectEmulator()
        val status = ps2.ps2StorageState().toStatus()

        content.addView(
            Ui.statusPanel(
                this,
                "PS2",
                status,
                detected?.name
            ),
            Ui.lp()
        )

        subsection("EMULATOR")
        card("Emulator", detected?.name ?: "Not detected") { render() }
        line(
            "Data path: ${ps2.manualPath ?: detected?.path ?: "Not configured"}",
            true
        )

        subsection("STORAGE")
        line(
            "Games folder: ${
                ps2.gamesUri?.let { ps2.describe(it) } ?: "Not configured"
            }"
        )
        line(
            "Transfer folder: ${
                ps2.transferUri?.let { ps2.describe(it) } ?: "Not configured"
            }",
            true
        )

        btn("Auto-detect emulator", true) { render() }
        btn("Select emulator data folder") { pickPs2Root.launch(null) }
        btn("Set emulator path manually") { ps2ManualPathDialog() }
        btn("Select PS2 games folder") { pickPs2Games.launch(null) }
        btn("Select transfer folder") { pickPs2Transfer.launch(null) }
    }

    private enum class DolphinSystem { WII, GAMECUBE }

    private fun renderDolphinSystemSettings(system: DolphinSystem) {
        val dolphin = app.dolphinStorage
        val detected = dolphin.detectDolphin()
        val gamesUri = if (system == DolphinSystem.WII) dolphin.wiiGamesUri else dolphin.gameCubeGamesUri
        val label = if (system == DolphinSystem.WII) "Wii games folder" else "GameCube games folder"

        subsection("EMULATOR")
        card("Emulator", detected?.name ?: "Dolphin not detected") { render() }
        val access = when {
            dolphin.directGameSettingsStore() != null -> "✓ Automatic access available"
            dolphin.dolphinUserDoc() != null -> "✓ Folder access configured"
            detected != null -> "⚠ Android is restricting Dolphin's Android/data folder"
            else -> "Not configured"
        }
        line("Cheat storage: $access", true)

        subsection("STORAGE")
        line("$label: ${dolphin.describe(gamesUri)}")
        btn("Select $label", true) {
            if (system == DolphinSystem.WII) pickWiiGames.launch(gamesUri) else pickGameCubeGames.launch(gamesUri)
        }
        if (gamesUri != null) {
            btn("Forget $label") {
                if (system == DolphinSystem.WII) dolphin.clearWiiGames() else dolphin.clearGameCubeGames()
                render()
            }
        }
        btn("Configure Dolphin cheat storage") { pickDolphinUser.launch(dolphin.dolphinUserUri) }
        if (dolphin.dolphinUserUri != null) btn("Forget Dolphin folder") { dolphin.clearDolphinUser(); render() }
    }

    private fun renderCheatSettings() {
        val src = app.settings.source()
        subsection("3DS / GENERAL CHEAT SOURCE")
        card("Current source", src.displayName, ::chooseRecommendedSource)
        line("${src.branch} • ${src.basePath} • ${src.fileNamePattern}", true)
        btn("Browse recommended sources", true) { chooseRecommendedSource() }
        btn("Edit custom GitHub source") { editSource() }
        btn("Use default Sharkive source") { app.settings.resetSource(); render(); note(Ui.Kind.OK, "Sharkive is now the active cheat source.") }

        subsection("CHEAT DATABASE")
        line("Cached cheat data: ${app.cache.cheatDataSize() / 1024} KB", true)
        btn("Refresh cheat database now", true) { refreshNow() }
        btn("Clear cached cheat data") {
            MaterialAlertDialogBuilder(this).setTitle("Clear cached cheat data?")
                .setMessage("Downloaded cheat lists are removed from this app. Cheats already installed in the emulator are not touched.")
                .setPositiveButton("Clear") { _, _ -> val n = app.cache.clearCheatData(); render(); note(Ui.Kind.OK, "Cleared ${n / 1024} KB of cached data.") }
                .setNegativeButton("Cancel", null).show()
        }
        subsection("DOLPHIN CHEAT REPOSITORIES")
        line("Enabled repositories: ${app.dolphinSettings.sources().size}", true)
        btn("Choose cheat repositories", true) { chooseDolphinRepositories() }
        btn("Add GitHub repository") { addDolphinRepository() }
    }

    private fun renderBackupSettings() {
        subsection("BACKUP & RESTORE")
        line("A backup is created automatically before any cheat file is changed. Backups are stored inside the configured cheats folder.", true)
        btn("Manage backups", true) { manageBackups() }
    }

    private fun renderAdvancedSettings() {
        subsection("UPDATES")
        content.addView(MaterialSwitch(this).apply {
            text = "Automatically check for cheat updates"
            isChecked = app.settings.autoUpdate
            setOnCheckedChangeListener { _, on -> app.settings.autoUpdate = on }
        }, Ui.lp())

        subsection("DEBUGGING")
        content.addView(MaterialSwitch(this).apply {
            text = "Verbose debug log"
            isChecked = app.settings.debugLog
            setOnCheckedChangeListener { _, on -> app.settings.debugLog = on; AppLog.verbose = on }
        }, Ui.lp())
        btn("View debug log") { viewLog() }
    }

    private fun renderAbout() {
        subsection("BREEZY'S CHEAT STATION")
        line("Version ${BuildConfigVersion.name(this)}")
        line("A companion utility that finds your games and installs compatible cheat files for supported emulators. It works alongside supported emulator projects rather than belonging to any one emulator project.", true)
        line("Cheat codes come from third-party sources; use them at your own risk.", true)
    }

    private fun note(kind: Ui.Kind, msg: String) = Ui.show(banner, kind, msg)

    private fun accept(slot: StorageManager.Slot, uri: Uri) {
        val err = app.storage.accept(slot, uri)

        if (err == null) {
            if (slot == StorageManager.Slot.ROOT) {
                app.storage.selected3dsEmulatorPackage?.let { packageName ->
                    app.storage.saveEmulatorRootUri(packageName, uri)
                }
            }

            render()
            note(Ui.Kind.OK, "Folder saved.")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Folder problem")
            .setMessage(err)
            .setPositiveButton("Use anyway") { _, _ ->
                if (slot == StorageManager.Slot.ROOT) {
                    app.storage.forceRoot(uri)

                    app.storage.selected3dsEmulatorPackage?.let { packageName ->
                        app.storage.saveEmulatorRootUri(packageName, uri)
                    }
                }

                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun acceptDolphinUser(uri: Uri) {
        val err = app.dolphinStorage.acceptDolphinUser(uri)
        if (err == null) { render(); note(Ui.Kind.OK, "Dolphin data folder saved.") }
        else MaterialAlertDialogBuilder(this).setTitle("Folder problem").setMessage(err).setPositiveButton("OK", null).show()
    }

    private fun acceptWiiGames(uri: Uri) {
        val err = app.dolphinStorage.acceptWiiGames(uri)
        if (err == null) { render(); note(Ui.Kind.OK, "Wii games folder saved.") }
        else MaterialAlertDialogBuilder(this).setTitle("Folder problem").setMessage(err).setPositiveButton("OK", null).show()
    }

    private fun acceptGameCubeGames(uri: Uri) {
        val err = app.dolphinStorage.acceptGameCubeGames(uri)
        if (err == null) { render(); note(Ui.Kind.OK, "GameCube games folder saved.") }
        else MaterialAlertDialogBuilder(this).setTitle("Folder problem").setMessage(err).setPositiveButton("OK", null).show()
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

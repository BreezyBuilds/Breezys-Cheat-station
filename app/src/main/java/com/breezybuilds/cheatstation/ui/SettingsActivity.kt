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
import com.breezybuilds.cheatstation.provider.Ps2CheatSources
import com.breezybuilds.cheatstation.provider.Ps2Settings
import com.breezybuilds.cheatstation.emulator.UniversalRoms
import com.breezybuilds.cheatstation.provider.RecommendedCheatSources
import com.breezybuilds.cheatstation.storage.StorageManager
import com.breezybuilds.cheatstation.storage.toStatus
import com.breezybuilds.cheatstation.storage.DolphinStorage
import com.breezybuilds.cheatstation.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : RetroThemedActivity() {
    private lateinit var content: android.widget.LinearLayout
    private lateinit var banner: TextView
    private lateinit var toolbar: MaterialToolbar

    private enum class Page {
        HOME, APPEARANCE, SYSTEMS, THREE_DS, PS2, WII, GAMECUBE, CHEATS, CHEAT_SYSTEM, BACKUPS, ADVANCED, ABOUT
    }

    private var page = Page.HOME
    private var cheatSystem = "3DS"
    private var themeTransitionInProgress = false

    // Theme changes are handled in this screen, not a second time by the base activity.

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("retro_settings_page", page.name)
        outState.putString("retro_cheat_system", cheatSystem)
        super.onSaveInstanceState(outState)
    }

    /** Identical identity labels across the four system-specific source pages. */
    private fun cheatSystemLabel(id: String): String = when (id) {
        "3DS" -> "▣  Nintendo 3DS"
        "PS2" -> "◈  PlayStation 2"
        "Wii" -> "◉  Nintendo Wii"
        else -> "◆  Nintendo GameCube"
    }


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
        savedInstanceState?.getString("retro_settings_page")?.let { savedPage ->
            page = Page.entries.firstOrNull { it.name == savedPage } ?: Page.HOME
        }
        cheatSystem = savedInstanceState?.getString("retro_cheat_system") ?: "3DS"
        val ctx = this
        val root = Ui.vbox(ctx).apply { setBackgroundColor(Ui.Appearance.background(ctx)) }
        Ui.edgeToEdge(root)

        toolbar = MaterialToolbar(ctx).apply {
            title = "Settings"
            setBackgroundColor(Ui.Appearance.background(ctx))
            setTitleTextColor(Ui.Appearance.foreground(ctx))
            setNavigationIconTint(Ui.Appearance.foreground(ctx))
            setNavigationOnClickListener { goBack() }
        }
        root.addView(toolbar, Ui.lp())

        banner = Ui.banner(ctx)
        root.addView(banner, Ui.lp().apply { setMargins(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0) })

        content = Ui.vbox(ctx, 8).apply {
            setPadding(
                Ui.dp(ctx, 12),
                Ui.dp(ctx, 8),
                Ui.dp(ctx, 12),
                Ui.dp(ctx, 24)
            )
            clipToPadding = false
        }
        root.addView(
            ScrollView(ctx).apply {
                addView(content)
                isFillViewport = true
            },
            Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        window.statusBarColor = Ui.Appearance.background(this)
        window.navigationBarColor = Ui.Appearance.background(this)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = Ui.Appearance.theme(this@SettingsActivity) == "light"
            isAppearanceLightNavigationBars = Ui.Appearance.theme(this@SettingsActivity) == "light"
        }
        render()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        if (savedInstanceState == null) when (intent.getStringExtra("open_section")) {
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
            Page.APPEARANCE, Page.SYSTEMS, Page.CHEATS, Page.BACKUPS, Page.ADVANCED, Page.ABOUT -> Page.HOME
            Page.THREE_DS, Page.PS2, Page.WII, Page.GAMECUBE -> Page.SYSTEMS
            Page.CHEAT_SYSTEM -> Page.CHEATS
        }
        render()
    }

    private fun pageTitle(): String = when (page) {
        Page.HOME -> "Settings"
        Page.APPEARANCE -> "Appearance & Themes"
        Page.SYSTEMS -> "Systems"
        Page.THREE_DS -> "3DS"
        Page.PS2 -> "PlayStation 2"
        Page.WII -> "Wii"
        Page.GAMECUBE -> "GameCube"
        Page.CHEATS -> "Cheats & Sources"
        Page.CHEAT_SYSTEM -> "$cheatSystem cheat sources"
        Page.BACKUPS -> "Backups"
        Page.ADVANCED -> "Advanced"
        Page.ABOUT -> "About"
    }

    private fun line(text: String, secondary: Boolean = false) =
        content.addView(
            Ui.tv(this, text, if (secondary) 13f else 14f, secondary = secondary).apply {
                setPadding(
                    Ui.dp(this@SettingsActivity, 2),
                    Ui.dp(this@SettingsActivity, 2),
                    Ui.dp(this@SettingsActivity, 2),
                    Ui.dp(this@SettingsActivity, 2)
                )
            },
            Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 3) }
        )

    private fun btn(label: String, filled: Boolean = false, onClick: () -> Unit) =
        content.addView(
            Ui.button(this, label, filled, onClick),
            Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 5) }
        )

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
            .setNeutralButton("Use all") { _, _ ->
                if (sources.isNotEmpty()) { settings.saveSources(sources); render() }
            }
            .setPositiveButton("Save selected") { _, _ ->
                if (selected.isEmpty()) note(Ui.Kind.ERROR, "Select at least one repository.")
                else { settings.saveSources(sources.filter { it.id in selected }); render() }
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
            render()
            if (st.threeDsStorageState() == com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE) {
                Ui.show(banner, Ui.Kind.OK, "${detected.name} data folder is accessible.")
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Grant ${detected.name} folder access")
                    .setMessage("Retro Loader found ${detectedFolder.path}, but has not verified write access. Select this folder in Android's picker to enable cheat installation.")
                    .setPositiveButton("Grant access") { _, _ -> pickRoot.launch(st.pickerHint()) }
                    .setNegativeButton("Later", null)
                    .show()
            }
            return
        }

        Ui.show(
            banner,
            Ui.Kind.INFO,
            "${detected.name} detected. Select its data folder to give Breezy's Retro Loader access."
        )

        pickRoot.launch(st.pickerHint())
    }

    private fun card(title: String, subtitle: String, onClick: () -> Unit) {
        content.addView(
            Ui.settingsCard(this, title, subtitle, onClick),
            Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 1) }
        )
    }

    private fun subsection(title: String) {
        content.addView(
            Ui.tv(this, title, 11f, bold = true).apply {
                letterSpacing = 0.08f
                setTextColor(Ui.Appearance.action(this@SettingsActivity))
                setPadding(
                    Ui.dp(this@SettingsActivity, 2),
                    Ui.dp(this@SettingsActivity, 14),
                    0,
                    Ui.dp(this@SettingsActivity, 3)
                )
            },
            Ui.lp()
        )
    }

    private fun render() {
        content.removeAllViews()
        toolbar.title = pageTitle()
        toolbar.setBackgroundColor(Ui.Appearance.background(this))
        toolbar.setTitleTextColor(Ui.Appearance.foreground(this))
        toolbar.setNavigationIconTint(Ui.Appearance.foreground(this))
        toolbar.navigationIcon = if (page == Page.HOME) null else androidx.appcompat.content.res.AppCompatResources.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        when (page) {
            Page.HOME -> renderHome()
            Page.APPEARANCE -> renderAppearance()
            Page.SYSTEMS -> renderSystems()
            Page.THREE_DS -> render3dsSettings()
            Page.PS2 -> renderPs2Settings()
            Page.WII -> renderDolphinSystemSettings(DolphinSystem.WII)
            Page.GAMECUBE -> renderDolphinSystemSettings(DolphinSystem.GAMECUBE)
            Page.CHEATS -> renderCheatSettings()
            Page.CHEAT_SYSTEM -> renderSystemCheatSettings()
            Page.BACKUPS -> renderBackupSettings()
            Page.ADVANCED -> renderAdvancedSettings()
            Page.ABOUT -> renderAbout()
        }
    }

    private fun renderHome() {
        content.addView(Ui.tv(this, "Emulators, cheats and app preferences", 13f, secondary = true), Ui.lp())
        card("Installed emulators", "Automatic detection and saved choices for each system") {
            startActivity(android.content.Intent(this, EmulatorStationActivity::class.java))
        }
        subsection("RETRO LOADER")
        card("Compatible Emulators", "Choose systems, detect installed emulators and open trusted download sources") {
            startActivity(android.content.Intent(this, RetroSetupActivity::class.java).putExtra("from_settings", true))
        }
        subsection("SYSTEMS")
        card("Systems", "Emulators, game folders and system-specific storage") { navigate(Page.SYSTEMS) }

        subsection("APP")
        card("Appearance & Themes", "OLED Black, Grey Light, Navy and console colours") { navigate(Page.APPEARANCE) }
        card("Cheats & Sources", "Repositories and cheat databases by system") { navigate(Page.CHEATS) }
        card("Backups", "Manage automatic cheat backups") { navigate(Page.BACKUPS) }
        card("Advanced", "Updates, debugging and diagnostics") { navigate(Page.ADVANCED) }
        card("About", "Version and application information") { navigate(Page.ABOUT) }
    }

    private fun renderAppearance() {
        subsection("MAIN APP THEME")
        listOf("navy" to "Navy", "oled" to "OLED Black", "light" to "Grey Light").forEach { (id, name) ->
            content.addView(Ui.selectionRow(this, name, when (id) { "navy" -> "Deep navy surfaces and cool blue accents"; "oled" -> "True black with subtle contrast"; else -> "Soft grey surfaces and dark text" }, Ui.Appearance.theme(this) == id, onClick = {
                if (!themeTransitionInProgress && Ui.Appearance.theme(this) != id) {
                    themeTransitionInProgress = true
                    Ui.Appearance.setTheme(this, id)
                    recreate()
                }
            }), Ui.lp())
        }
        subsection("ICON PACK")
        listOf(
            "outline" to ("Precision Outline" to "Fine, minimal console line drawings"),
            "duotone" to ("Soft Duotone" to "Colourful hardware with depth and highlights"),
            "pixel" to ("Retro Pixel" to "Detailed 8–16-bit pixel illustrations")
        ).forEach { (id, labels) ->
            content.addView(Ui.selectionRow(this, labels.first, labels.second, Ui.Appearance.iconPack(this) == id, onClick = {
                Ui.Appearance.setIconPack(this, id)
                render()
            }, iconPreview = id), Ui.lp())
        }
        subsection("INTERACTIONS")
        content.addView(Ui.selectionRow(this, "Haptic Feedback", "Gentle feedback when selecting an option", Ui.Appearance.haptics(this), onClick = {
            Ui.Appearance.setHaptics(this, !Ui.Appearance.haptics(this))
            render()
        }), Ui.lp())
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

        Ui.interact(this, threeDsCard) { navigate(Page.THREE_DS) }

        content.addView(threeDsCard, Ui.lp())

        // PlayStation 2 station bay
        val ps2 = app.ps2Storage.detectEmulator()

        val ps2Card = Ui.statusPanel(
            this,
            "PS2",
            app.ps2Storage.ps2StorageState().toStatus(),
            ps2?.name
        )

        Ui.interact(this, ps2Card) { navigate(Page.PS2) }

        content.addView(
            ps2Card,
            Ui.lp().apply {
                topMargin = Ui.dp(this@SettingsActivity, 2)
            }
        )

        // Use exactly the same Dolphin readiness criteria as the individual system pages.
        val dolphinStorage = app.dolphinStorage
        val dolphin = dolphinStorage.detectDolphin()
        val dolphinState = when {
            dolphinStorage.directGameSettingsStore() != null || dolphinStorage.dolphinUserDoc() != null ->
                com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE
            dolphin != null -> com.breezybuilds.cheatstation.storage.StorageState.PERMISSION_REQUIRED
            else -> com.breezybuilds.cheatstation.storage.StorageState.NOT_CONFIGURED
        }
        listOf("Wii" to Page.WII, "GameCube" to Page.GAMECUBE).forEach { (name, destination) ->
            val card = Ui.statusPanel(this, name, dolphinState.toStatus(), dolphin?.name)
            Ui.interact(card.context, card) { navigate(destination) }
            content.addView(card, Ui.lp().apply { topMargin = Ui.dp(this@SettingsActivity, 2) })

        }
    }

    /** Shared navigation and management rows across every supported platform. */
    private fun settingRow(title: String, value: String, onClick: () -> Unit) =
        card(title, value, onClick)

    private fun manage(title: String, vararg options: Pair<String, () -> Unit>) {
        val names = options.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(names) { _, index -> options[index].second() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmSharedDolphinReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Forget shared Dolphin folder?")
            .setMessage("This folder is shared by Wii and GameCube. Both systems may lose cheat storage access until you configure it again. Existing cheat files will not be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Forget folder") { _, _ ->
                app.dolphinStorage.clearDolphinUser()
                render()
            }
            .show()
    }

    private fun systemHeader(name: String, emulator: String?, status: com.breezybuilds.cheatstation.storage.StorageStatus) {
        content.addView(Ui.statusPanel(this, name, status, emulator), Ui.lp())
    }

    private fun render3dsSettings() {
        val st = app.storage
        val detected = com.breezybuilds.cheatstation.emulator.EmulatorDetector.detect3dsEmulators(this)
        val selected = detected.firstOrNull { it.packageName == st.selected3dsEmulatorPackage }
        systemHeader("3DS", selected?.name, st.threeDsStorageState().toStatus())
        subsection("EMULATOR")
        settingRow("Emulator", selected?.name ?: "Not selected") { choose3dsEmulator() }
        subsection("STORAGE")
        settingRow("Emulator data folder", st.describe3dsRoot()) {
            val actions = mutableListOf<Pair<String, () -> Unit>>(
                "Auto-detect emulator" to { autoDetect3ds() },
                "Change data folder" to { pickRoot.launch(st.pickerHint()) }
            )
            if (selected != null && st.selected3dsRootUri() != null) {
                actions.add("Forget data folder" to {
                    st.selected3dsEmulatorPackage?.let { st.clearEmulatorRoot(it) }
                    render()
                })
            }
            manage("Emulator data folder", *actions.toTypedArray())
        }
        settingRow("Games folder", if (st.gamesUri != null) st.describe(st.gamesUri) else com.breezybuilds.cheatstation.emulator.UniversalRoms.gamesFolderLabel(this, "3DS")) {
            val actions = mutableListOf<Pair<String, () -> Unit>>(
                "Select games folder" to { pickGames.launch(null) }
            )
            if (st.gamesUri != null) actions.add("Forget games folder" to {
                st.clear(StorageManager.Slot.GAMES)
                render()
            })
            manage("Games folder", *actions.toTypedArray())
        }
        settingRow("Cheats folder", st.cheatsUri?.let { st.describe(it) } ?: "Automatic (data folder/cheats)") {
            val actions = mutableListOf<Pair<String, () -> Unit>>(
                "Select cheats folder" to { pickCheats.launch(st.rootUri) }
            )
            if (st.cheatsUri != null) actions.add("Use automatic cheats folder" to {
                st.clear(StorageManager.Slot.CHEATS)
                render()
            })
            manage("Cheats folder", *actions.toTypedArray())
        }
        subsection("ADVANCED")
        settingRow("Additional options", "Detection and emulator selection") {
            manage("3DS advanced options",
                "Auto-detect 3DS emulator" to { autoDetect3ds() },
                "Choose a different emulator" to { choose3dsEmulator() })
        }
    }

    private fun renderPs2Settings() {
        val ps2 = app.ps2Storage
        val detected = ps2.detectEmulator()
        systemHeader("PS2", detected?.name, ps2.ps2StorageState().toStatus())
        subsection("EMULATOR")
        settingRow("Emulator", detected?.name ?: "Not detected") {
            manage("PS2 emulator", "Auto-detect emulator" to { render() },
                "Select emulator data folder" to { pickPs2Root.launch(null) },
                "Set emulator path manually" to { ps2ManualPathDialog() })
        }
        subsection("STORAGE")
        settingRow("Emulator data path", ps2.manualPath ?: detected?.path ?: "Not configured") {
            manage("Emulator data path",
                "Select emulator data folder" to { pickPs2Root.launch(null) },
                "Set emulator path manually" to { ps2ManualPathDialog() })
        }
        settingRow("Games folder", ps2.gamesUri?.let { ps2.describe(it) } ?: com.breezybuilds.cheatstation.emulator.UniversalRoms.gamesFolderLabel(this, "PS2")) {
            pickPs2Games.launch(null)
        }
        settingRow("Transfer folder", ps2.transferUri?.let { ps2.describe(it) } ?: "Not configured") {
            pickPs2Transfer.launch(null)
        }
        subsection("ADVANCED")
        settingRow("Additional options", "Emulator detection and paths") {
            manage("PS2 advanced options",
                "Auto-detect emulator" to { render() },
                "Select emulator data folder" to { pickPs2Root.launch(null) },
                "Set emulator path manually" to { ps2ManualPathDialog() })
        }
    }

    private enum class DolphinSystem { WII, GAMECUBE }

    private fun renderDolphinSystemSettings(system: DolphinSystem) {
        val dolphin = app.dolphinStorage
        val detected = dolphin.detectDolphin()
        val gamesUri = if (system == DolphinSystem.WII) dolphin.wiiGamesUri else dolphin.gameCubeGamesUri
        val name = if (system == DolphinSystem.WII) "Wii" else "GameCube"
        val access = when {
            dolphin.directGameSettingsStore() != null -> "Automatic access available"
            dolphin.dolphinUserDoc() != null -> "Folder access configured"
            detected != null -> "Android/data access restricted"
            else -> "Not configured"
        }
        systemHeader(name, detected?.name, (when {
            dolphin.directGameSettingsStore() != null || dolphin.dolphinUserDoc() != null -> com.breezybuilds.cheatstation.storage.StorageState.ACCESSIBLE
            detected != null -> com.breezybuilds.cheatstation.storage.StorageState.PERMISSION_REQUIRED
            else -> com.breezybuilds.cheatstation.storage.StorageState.NOT_CONFIGURED
        }).toStatus())
        subsection("EMULATOR")
        settingRow("Emulator", detected?.name ?: "Dolphin not detected") {
            manage("Dolphin emulator", "Configure cheat storage" to { pickDolphinUser.launch(dolphin.dolphinUserUri) })
        }
        subsection("STORAGE")
        settingRow("Games folder", if (gamesUri != null) dolphin.describe(gamesUri) else com.breezybuilds.cheatstation.emulator.UniversalRoms.gamesFolderLabel(this, name)) {
            val choose: () -> Unit = {
                if (system == DolphinSystem.WII) pickWiiGames.launch(gamesUri)
                else pickGameCubeGames.launch(gamesUri)
            }
            val actions = mutableListOf<Pair<String, () -> Unit>>("Select games folder" to choose)
            if (gamesUri != null) actions.add("Forget games folder" to {
                if (system == DolphinSystem.WII) dolphin.clearWiiGames()
                else dolphin.clearGameCubeGames()
                render()
            })
            manage("$name games folder", *actions.toTypedArray())
        }
        settingRow("Cheat storage", access) {
            val actions = mutableListOf<Pair<String, () -> Unit>>(
                "Configure Dolphin cheat storage" to { pickDolphinUser.launch(dolphin.dolphinUserUri) }
            )
            if (dolphin.dolphinUserUri != null) actions.add("Forget shared Dolphin folder" to {
                confirmSharedDolphinReset()
            })
            manage("Dolphin cheat storage", *actions.toTypedArray())
        }
        subsection("ADVANCED")
        settingRow("Additional options", "Dolphin storage access") {
            val actions = mutableListOf<Pair<String, () -> Unit>>(
                "Configure Dolphin cheat storage" to { pickDolphinUser.launch(dolphin.dolphinUserUri) }
            )
            if (dolphin.dolphinUserUri != null) actions.add("Forget shared Dolphin folder" to {
                confirmSharedDolphinReset()
            })
            manage("$name advanced options", *actions.toTypedArray())
        }
    }

    private fun renderCheatSettings() {
        line("Manage cheat discovery across all four systems. Installed cheats are never modified by source settings.", true)
        subsection("SYSTEM SOURCES")
        listOf("3DS", "PS2", "Wii", "GameCube").forEach { id ->
            val enabled = when (id) {
                "3DS" -> app.settings.sources().size
                "PS2" -> Ps2Settings(this).sources().size
                else -> app.dolphinSettings.sources().size
            }
            val total = when (id) {
                "3DS" -> app.settings.allSources().size
                "PS2" -> Ps2Settings(this).allSources().size
                else -> app.dolphinSettings.allSources().size
            }
            val state = if (enabled == 0) "Needs setup" else "$enabled of $total sources enabled"
            val shared = if (id == "Wii" || id == "GameCube") " · Dolphin sources shared" else ""
            card(cheatSystemLabel(id), state + shared) {
                cheatSystem = id
                navigate(Page.CHEAT_SYSTEM)
            }
        }
        subsection("DATABASE & UPDATES")
        line("Cached cheat data: ${app.cache.cheatDataSize() / 1024} KB", true)
        line("Open a system above to refresh its cheats using its selected repositories.", true)
        card("Clear downloaded cheat cache", "Remove cached searches without touching installed emulator cheats") {
            MaterialAlertDialogBuilder(this).setTitle("Clear cached cheat data?")
                .setMessage("Downloaded cheat lists are removed from this app. Installed cheats are not touched.")
                .setPositiveButton("Clear") { _, _ ->
                    val n = app.cache.clearCheatData()
                    render()
                    note(Ui.Kind.OK, "Cleared ${n / 1024} KB of cached data.")
                }.setNegativeButton("Cancel", null).show()
        }
    }

    /** One source-management flow, shared between Wii and GameCube where appropriate. */
    private fun renderSystemCheatSettings() {
        val shared = cheatSystem == "Wii" || cheatSystem == "GameCube"
        val enabled = when (cheatSystem) {
            "3DS" -> app.settings.sources().size
            "PS2" -> Ps2Settings(this).sources().size
            else -> app.dolphinSettings.sources().size
        }
        val total = when (cheatSystem) {
            "3DS" -> app.settings.allSources().size
            "PS2" -> Ps2Settings(this).allSources().size
            else -> app.dolphinSettings.allSources().size
        }
        line(cheatSystemLabel(cheatSystem), true)
        line(if (shared) "Wii and GameCube share Dolphin repositories. Changes here apply to both systems."
             else "Select repositories to search for game-specific cheats.", true)
        subsection("SOURCE STATUS")
        line(if (enabled == 0) "No repositories enabled — select at least one to discover cheats."
             else "$enabled of $total repositories enabled. Availability and cheat coverage are separate checks.", true)
        card("Choose repositories", "Use all, recommended or a custom selection") { chooseUniversalRepositories() }
        card("Browse source catalogue", "View each repository, its file format and enabled state") { showCheatRepositoryCatalogue() }
        subsection("SOURCE TOOLS")
        line("Repository health checks test GitHub access only; they do not validate file formats or game coverage.", true)
        card("Check live availability", "Test enabled GitHub repositories") { checkRepositoryAvailability() }
        card("Check configuration", "Audit repository addresses and selections") { showRepositoryConfigurationAudit() }
        card("Add custom repository", when (cheatSystem) {
            "3DS" -> "Title-ID text files compatible with the 3DS parser"
            "PS2" -> "SERIAL_CRC.pnach files compatible with the PS2 parser"
            else -> "Dolphin GameSettings INI files"
        }) {
            when (cheatSystem) {
                "3DS" -> editSource()
                "PS2" -> addPs2Repository()
                else -> addDolphinRepository()
            }
        }
        subsection("CHEAT DATABASE")
        card("Refresh cheat database", "Check ${cheatSystem} games against enabled repositories") {
            refreshSystemCheats(cheatSystem)
        }
        line("Cached cheat data: ${app.cache.cheatDataSize() / 1024} KB", true)
        if (cheatSystem == "PS2") {
            line("Some sources contain widescreen, FPS and compatibility patches rather than gameplay cheats.", true)
        }
        line("A reachable GitHub repository does not guarantee compatible cheats for every game. Source settings never change installed cheat files.", true)
    }

    /**
     * Browsable source catalogue. Avoids a single long dialog of repository URLs;
     * users can inspect each source's format, branch, and enabled state.
     */
    private fun showCheatRepositoryCatalogue() {
        data class Entry(
            val name: String,
            val description: String,
            val owner: String,
            val repo: String,
            val branch: String,
            val folder: String,
            val format: String,
            val enabled: Boolean
        )
        val entries: List<Entry> = when (cheatSystem) {
            "3DS" -> {
                val enabled = app.settings.sources().map { it.id }.toSet()
                app.settings.allSources().map { config ->
                    val builtIn = RecommendedCheatSources.all.firstOrNull { it.config.id == config.id }
                    Entry(
                        builtIn?.name ?: "Custom 3DS source",
                        builtIn?.description ?: "User-configured Title-ID cheat source",
                        config.owner, config.repo, config.branch, config.basePath,
                        "3DS Title-ID text files", config.id in enabled
                    )
                }
            }
            "PS2" -> {
                val settings = Ps2Settings(this)
                val enabled = settings.sources().map { it.id }.toSet()
                settings.allSources().map { config ->
                    Entry(
                        config.name, config.description, config.owner, config.repo,
                        config.branch, config.path, "PS2 SERIAL_CRC.pnach files",
                        config.id in enabled
                    )
                }
            }
            else -> {
                val enabled = app.dolphinSettings.sources().map { it.id }.toSet()
                app.dolphinSettings.allSources().map { config ->
                    Entry(
                        config.name, config.description, config.owner, config.repo,
                        config.branch, config.path, "Dolphin GameSettings INI files",
                        config.id in enabled
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            note(Ui.Kind.ERROR, "No repositories are configured for $cheatSystem.")
            return
        }
        val names = entries.map {
            "${if (it.enabled) "● Enabled" else "○ Disabled"} · ${it.name}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("$cheatSystem · Repository catalogue")
            .setItems(names) { _, index ->
                val source = entries[index]
                val details = buildString {
                    append(source.description)
                    append("\n\nStatus: ")
                    append(if (source.enabled) "Enabled" else "Disabled")
                    append("\nFormat: ")
                    append(source.format)
                    append("\nGitHub: ")
                    append(source.owner)
                    append("/")
                    append(source.repo)
                    append("\nBranch: ")
                    append(source.branch)
                    append("\nFolder: ")
                    append(source.folder.ifBlank { "(repository root)" })
                    append("\n\nRepository availability and compatible cheat coverage must be checked separately.")
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle(source.name)
                    .setMessage(details)
                    .setPositiveButton("Done", null)
                    .setNeutralButton("Back to catalogue") { _, _ ->
                        showCheatRepositoryCatalogue()
                    }
                    .show()
            }
            .setPositiveButton("Close", null)
            .setNeutralButton("Manage selection") { _, _ ->
                chooseUniversalRepositories()
            }
            .show()
    }

    /** Local configuration audit only: does not claim that a remote repository is online. */
    private fun showRepositoryConfigurationAudit() {
        data class AuditSource(val id: String, val name: String, val owner: String, val repo: String, val path: String)
        val catalogue: List<AuditSource>
        val enabled: Set<String>
        when (cheatSystem) {
            "3DS" -> {
                catalogue = app.settings.allSources().map {
                    AuditSource(it.id, it.displayName, it.owner, it.repo, it.basePath)
                }
                enabled = app.settings.sources().map { it.id }.toSet()
            }
            "PS2" -> {
                catalogue = Ps2Settings(this).allSources().map {
                    AuditSource(it.id, it.name, it.owner, it.repo, it.path)
                }
                enabled = Ps2Settings(this).sources().map { it.id }.toSet()
            }
            else -> {
                catalogue = app.dolphinSettings.allSources().map {
                    AuditSource(it.id, it.name, it.owner, it.repo, it.path)
                }
                enabled = app.dolphinSettings.sources().map { it.id }.toSet()
            }
        }
        val issues = mutableListOf<String>()
        if (enabled.isEmpty()) issues.add("No repositories are enabled.")
        val duplicateIds = catalogue.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) issues.add("Duplicate source IDs: ${duplicateIds.joinToString()}")
        val unknown = enabled - catalogue.map { it.id }.toSet()
        if (unknown.isNotEmpty()) issues.add("Selected sources missing from built-in catalogue: ${unknown.joinToString()}. These may be custom sources.")
        catalogue.filter { it.id in enabled }.forEach { source ->
            if (source.owner.isBlank() || source.repo.isBlank() ||
                source.owner.contains('/') || source.repo.contains('/')) {
                issues.add("${source.name}: invalid GitHub owner/repository configuration.")
            }
            if (source.path.startsWith("/") || source.path.contains("..")) {
                issues.add("${source.name}: check repository subfolder path.")
            }
        }
        val summary = "${enabled.size} selected · ${catalogue.size} catalogued\n\n" +
            if (issues.isEmpty()) "No local configuration problems detected." else
                issues.joinToString("\n\n", prefix = "Configuration notices:\n")
        MaterialAlertDialogBuilder(this)
            .setTitle("$cheatSystem · Source configuration")
            .setMessage(summary + "\n\nThis is an offline settings audit. It does not test GitHub availability or verify that cheat files exist.")
            .setPositiveButton("Close", null)
            .show()
    }

    /** Network check deliberately does not imply the repository contains usable cheat files. */
    private fun checkRepositoryAvailability() {
        data class RemoteSource(val name: String, val owner: String, val repo: String, val branch: String)
        val enabled: List<RemoteSource> = when (cheatSystem) {
            "3DS" -> app.settings.sources().map { RemoteSource(it.displayName, it.owner, it.repo, it.branch) }
            "PS2" -> Ps2Settings(this).sources().map { RemoteSource(it.name, it.owner, it.repo, it.branch) }
            else -> app.dolphinSettings.sources().map { RemoteSource(it.name, it.owner, it.repo, it.branch) }
        }
        if (enabled.isEmpty()) {
            note(Ui.Kind.ERROR, "Select at least one repository first.")
            return
        }
        note(Ui.Kind.INFO, "Checking ${enabled.size} repositories…")
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                enabled.map { source ->
                    val url = "https://api.github.com/repos/${source.owner}/${source.repo}/branches/${source.branch}"
                    val result = try {
                        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection)
                        connection.connectTimeout = 6000
                        connection.readTimeout = 6000
                        connection.setRequestProperty("Accept", "application/vnd.github+json")
                        connection.setRequestProperty("User-Agent", "BreezyRetroLoader")
                        try {
                            when (connection.responseCode) {
                                200 -> "Reachable"
                                404 -> "Not found (repository or branch)"
                                403, 429 -> "Rate-limited / access restricted"
                                else -> "HTTP ${connection.responseCode}"
                            }
                        } finally { connection.disconnect() }
                    } catch (e: Exception) { "Connection failed: ${e.javaClass.simpleName}" }
                    "${source.name}: $result"
                }.joinToString("\n\n")
            }
            if (!isFinishing && !isDestroyed) {
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("$cheatSystem · Live repository availability")
                    .setMessage(report + "\n\nReachable means GitHub responded; it does not guarantee compatible cheat files or coverage for a particular game.")
                    .setPositiveButton("Close", null).show()
            }
        }
    }

    private fun chooseUniversalRepositories() {
        val labels: List<String>
        val ids: List<String>
        val selected: MutableSet<String>
        val recommended: Set<String>
        when (cheatSystem) {
            "3DS" -> {
                val all = app.settings.allSources()
                labels = all.map { "${it.displayName}\n${it.basePath}/{TITLEID}.txt" }
                ids = all.map { it.id }
                selected = app.settings.sources().map { it.id }.toMutableSet()
                recommended = setOf(RecommendedCheatSources.all.first().config.id)
            }
            "PS2" -> {
                val all = Ps2Settings(this).allSources()
                labels = all.map { "${it.name}\n${it.description}" }
                ids = all.map { it.id }
                selected = Ps2Settings(this).sources().map { it.id }.toMutableSet()
                recommended = all.filter { it.name == "PCSX2 Cheats & Patches" || it.name == "PCSX2 Cheats Collection" }.map { it.id }.toSet()
            }
            else -> {
                val all = app.dolphinSettings.allSources()
                labels = all.map { "${it.name}\n${it.description}" }
                ids = all.map { it.id }
                selected = app.dolphinSettings.sources().map { it.id }.toMutableSet()
                recommended = com.breezybuilds.cheatstation.provider.DolphinCheatSources.builtIn.map { it.id }.toSet()
            }
        }
        fun save(chosen: Set<String>) {
            if (chosen.isEmpty()) { note(Ui.Kind.ERROR, "Select at least one repository."); return }
            when (cheatSystem) {
                "3DS" -> app.settings.saveSources(app.settings.allSources().filter { it.id in chosen })
                "PS2" -> Ps2Settings(this).saveSources(Ps2Settings(this).allSources().filter { it.id in chosen })
                else -> app.dolphinSettings.saveSources(app.dolphinSettings.allSources().filter { it.id in chosen })
            }
            render()
        }
        val options = arrayOf("Use all compatible repositories", "Use recommended repositories", "Choose repositories manually")
        MaterialAlertDialogBuilder(this).setTitle("$cheatSystem · Repository mode")
            .setItems(options) { _, mode ->
                when (mode) {
                    0 -> {
                        if (cheatSystem == "PS2") {
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Include PS2 enhancement patches?")
                                .setMessage("Use all includes gameplay cheats and repositories containing widescreen, FPS and compatibility patches. These aren't all ordinary cheat codes. Codes are not automatically enabled.")
                                .setPositiveButton("Use all") { _, _ -> save(ids.toSet()) }
                                .setNegativeButton("Cancel", null).show()
                        } else save(ids.toSet())
                    }
                    1 -> save(recommended)
                    else -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("$cheatSystem · Select repositories")
                            .setMultiChoiceItems(labels.toTypedArray(), ids.map { it in selected }.toBooleanArray()) { _, index, checked ->
                                if (checked) selected.add(ids[index]) else selected.remove(ids[index])
                            }
                            .setPositiveButton("Save selection") { _, _ -> save(selected) }
                            .setNegativeButton("Cancel", null).show()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
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

        subsection("GAME LIST")
        content.addView(MaterialSwitch(this).apply {
            text = "Show technical game details"
            isChecked = app.settings.showTechnicalGameDetails
            setOnCheckedChangeListener { _, on ->
                app.settings.showTechnicalGameDetails = on
            }
        }, Ui.lp())

        subsection("DEBUGGING")
        content.addView(MaterialSwitch(this).apply {
            text = "Verbose debug log"
            isChecked = app.settings.debugLog
            setOnCheckedChangeListener { _, on -> app.settings.debugLog = on; AppLog.verbose = on }
        }, Ui.lp())
        btn("View debug log") { viewLog() }
        btn("View last crash report") { viewLastCrash() }
    }

    private fun renderAbout() {
        subsection("BREEZY'S RETRO LOADER")
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
            .setTitle("Folder access not verified")
            .setMessage(err)
            .setPositiveButton("Choose again") { _, _ ->
                when (slot) {
                    StorageManager.Slot.ROOT -> pickRoot.launch(app.storage.pickerHint())
                    StorageManager.Slot.GAMES -> pickGames.launch(null)
                    StorageManager.Slot.CHEATS -> pickCheats.launch(null)
                }
            }
            .setNegativeButton("Later", null)
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
        val selected = app.settings.sources().map { it.id }.toMutableSet()
        MaterialAlertDialogBuilder(this)
            .setTitle("3DS repositories · select multiple")
            .setMultiChoiceItems(sources.map { "${it.name}\n${it.description}" }.toTypedArray(),
                sources.map { it.config.id in selected }.toBooleanArray()) { _, which, checked ->
                if (checked) selected += sources[which].config.id else selected -= sources[which].config.id
            }
            .setNeutralButton("Use all") { _, _ -> app.settings.saveSources(sources.map { it.config }); render() }
            .setPositiveButton("Save selected") { _, _ ->
                if (selected.isEmpty()) note(Ui.Kind.ERROR, "Select at least one repository.")
                else { app.settings.saveSources(sources.filter { it.config.id in selected }.map { it.config }); render() }
            }
            .setNegativeButton("Cancel", null).show()
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
            app.settings.addCustomSource(c)
            dlg.dismiss(); render(); note(Ui.Kind.OK, "Custom 3DS source saved and enabled.")
        }
    }

    /** Custom PS2 repositories must use direct SERIAL_CRC.pnach files in the configured folder. */
    private fun addPs2Repository() {
        val box = Ui.vbox(this, 20)
        fun field(hint: String, value: String = "") = EditText(this).apply {
            this.hint = hint; setText(value); setSingleLine()
        }.also { box.addView(it, Ui.lp()) }
        val name = field("Display name")
        val owner = field("GitHub owner")
        val repo = field("GitHub repository")
        val branch = field("Branch", "main")
        val path = field("PNACH folder", "cheats")
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add PS2 PNACH repository")
            .setMessage("Files must be named SERIAL_CRC.pnach directly inside the specified folder. Other layouts are not supported by this provider.")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Add", null).setNegativeButton("Cancel", null).show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val source = com.breezybuilds.cheatstation.provider.Ps2CheatSource(
                name.text.toString().trim(), "Custom SERIAL_CRC PNACH repository",
                owner.text.toString().trim(), repo.text.toString().trim(),
                branch.text.toString().trim(), path.text.toString().trim().trim('/'))
            val validPart = Regex("^[A-Za-z0-9_.-]+$")
            if (source.name.isBlank() || !validPart.matches(source.owner) ||
                !validPart.matches(source.repo) ||
                !Regex("^[A-Za-z0-9_./-]+$").matches(source.branch) || source.branch.contains("..") ||
                source.path.contains("..") || !Regex("^[A-Za-z0-9_./ -]*$").matches(source.path)) {
                note(Ui.Kind.ERROR, "Enter a valid name, GitHub repository, branch and folder.")
                return@setOnClickListener
            }
            Ps2Settings(this).addCustomSource(source)
            dialog.dismiss(); render(); note(Ui.Kind.OK, "Custom PS2 repository saved and enabled.")
        }
    }

    /** Refresh each system independently; do not silently treat a failed request as a successful refresh. */
    private fun refreshSystemCheats(system: String) {
        if (system == "3DS") { refreshNow(); return }
        val cacheKey = when (system) {
            "PS2" -> "PS2"
            "Wii" -> "WII"
            "GameCube" -> "GAMECUBE"
            else -> return
        }
        val games = app.cache.loadSystemGames(cacheKey).distinctBy { it.titleId }
        if (games.isEmpty()) {
            note(Ui.Kind.WARN, "No $system games have been scanned. Scan your game library first.")
            return
        }
        val selected = if (system == "PS2") Ps2Settings(this).sources().size
                       else app.dolphinSettings.sources().size
        if (selected == 0) {
            note(Ui.Kind.WARN, "Enable at least one $system repository before refreshing.")
            return
        }
        note(Ui.Kind.INFO, "Refreshing $system cheats for ${games.size} games…")
        lifecycleScope.launch {
            var found = 0
            var cached = 0
            var missing = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                val dolphin = if (system == "PS2") null else
                    com.breezybuilds.cheatstation.provider.DolphinCheatProvider({ app.dolphinSettings.sources() })
                games.forEachIndexed { index, game ->
                    try {
                        if (system == "PS2") {
                            when (val result = app.ps2Repository.load(game.titleId)) {
                                is com.breezybuilds.cheatstation.data.CheatResult.Loaded -> {
                                    if (result.fromCache) cached++ else found++
                                }
                                is com.breezybuilds.cheatstation.data.CheatResult.Unavailable -> {
                                    if (result.kind == com.breezybuilds.cheatstation.provider.ProviderError.NOT_FOUND ||
                                        result.kind == null && result.message.startsWith("No PS2 cheats")) missing++
                                    else failed++
                                }
                            }
                        } else {
                            val count = dolphin!!.fetch(game.titleId).cheats.size
                            com.breezybuilds.cheatstation.ui.GameCheatStatus.put(this@SettingsActivity, cacheKey, game.titleId, count)
                            if (count > 0) found++ else missing++
                        }
                    } catch (e: com.breezybuilds.cheatstation.provider.ProviderException) {
                        if (e.kind == com.breezybuilds.cheatstation.provider.ProviderError.NOT_FOUND) {
                            missing++
                            if (system != "PS2") com.breezybuilds.cheatstation.ui.GameCheatStatus.put(this@SettingsActivity, cacheKey, game.titleId, 0)
                        } else failed++
                    } catch (e: Exception) {
                        failed++
                        AppLog.e("CheatRefresh", "$system: ${game.titleId} refresh failed", e)
                    }
                    if ((index + 1) % 5 == 0 || index + 1 == games.size) {
                        withContext(Dispatchers.Main) {
                            note(Ui.Kind.INFO, "$system: checked ${index + 1}/${games.size} games…")
                        }
                    }
                }
            }
            note(if (failed == 0) Ui.Kind.OK else Ui.Kind.WARN,
                "$system refresh complete: $found fresh results, $cached cached results, $missing not found, $failed failed. Checked ${games.size} games.")
        }
    }

    private fun refreshNow() {
        if (app.settings.sources().isEmpty()) {
            note(Ui.Kind.WARN, "Enable at least one 3DS repository before refreshing.")
            return
        }
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

    /** An app-owned crash record remains readable after Android restarts the process. */
    private fun viewLastCrash() {
        val crashFile = java.io.File(filesDir, "retro_loader_last_crash.txt")
        val report = runCatching { crashFile.takeIf { it.exists() }?.readText() }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "No recorded Java crash."
        val reportView = Ui.tv(this, report, 11f, mono = true).apply {
            setTextIsSelectable(true)
            setPadding(Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 8),
                Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Last crash report")
            .setView(ScrollView(this).apply { addView(reportView) })
            .setPositiveButton("Copy") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Retro Loader crash report", report))
                note(Ui.Kind.OK, "Crash report copied.")
            }
            .setNeutralButton("Clear") { _, _ -> crashFile.delete() }
            .setNegativeButton("Close", null)
            .show()
    }

    // ---- log ------------------------------------------------------------------------------------
    private fun viewLog() {
        val tv = Ui.tv(this, AppLog.dump().ifBlank { "(log is empty)" }, 11f, mono = true).apply { setTextIsSelectable(true); setPadding(Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 8), Ui.dp(this@SettingsActivity, 16), 0) }
        MaterialAlertDialogBuilder(this).setTitle("Debug log").setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Copy") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Breezy's Retro Loader log", AppLog.dump()))
                note(Ui.Kind.OK, "Log copied to the clipboard.")
            }
            .setNeutralButton("Clear") { _, _ -> AppLog.clear() }
            .setNegativeButton("Close", null).show()
    }
}

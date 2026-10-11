package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.app.Activity
import android.os.Build
import com.breezybuilds.cheatstation.emulator.UniversalRoms
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ProgressBar
import java.util.concurrent.Executors
import androidx.appcompat.app.AppCompatActivity
import com.breezybuilds.cheatstation.emulator.RetroCatalog
import com.breezybuilds.cheatstation.emulator.RetroSystemRegistry
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Two-step setup. All downloads require an explicit user action. */
class RetroSetupActivity : AppCompatActivity() {
    companion object {
        const val PREFS = "retro_loader_setup"
        const val FINISHED = "finished"
        const val SYSTEMS = "systems"
        val ALL = RetroSystemRegistry.available.map { it.id }
    }
    private val selected = linkedSetOf<String>()
    private val expandedManufacturers = linkedSetOf("Nintendo", "Sony")
    private var step = 0
    private val romPickerCode = 4817
    private val dataPickerCode = 4818
    private var fromSettings = false
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private var scanGeneration = 0
    private var pendingScanMode: UniversalRoms.ScanMode = UniversalRoms.ScanMode.SMART
    private var scanConfirmed = false
    private var pendingFinishAfterScan = false
    private var scanRunning = false
    private var scanSucceeded = false
    private var cachedRoot: Uri? = null
    private var cachedMode: UniversalRoms.ScanMode? = null
    private var cachedSystems: Set<String>? = null
    private var cachedResult: UniversalRoms.Result? = null
    private lateinit var body: LinearLayout
    private lateinit var footer: LinearLayout
    private val ink = 0xFFE9EDF5.toInt()
    private val muted = 0xFFACB9CD.toInt()
    private val bg = 0xFF0A1220.toInt()
    private val line = 0xFF29384E.toInt()
    private fun d(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromSettings = intent.getBooleanExtra("from_settings", false)
        selected.addAll(getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(SYSTEMS, ALL.toSet()) ?: ALL.toSet())
        step = savedInstanceState?.getInt("step") ?: 0
        pendingScanMode = savedInstanceState?.getString("pending_scan_mode")?.let { runCatching { UniversalRoms.ScanMode.valueOf(it) }.getOrNull() } ?: UniversalRoms.scanMode(this)
        scanConfirmed = savedInstanceState?.getBoolean("scan_confirmed") ?: false
        savedInstanceState?.getStringArrayList("expanded_manufacturers")?.let { expandedManufacturers.clear(); expandedManufacturers.addAll(it) }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(24), d(20), d(28))
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(10), d(20), d(14))
            setBackgroundColor(bg)
        }
        outer.addView(footer, LinearLayout.LayoutParams(-1, -2))
        setContentView(outer)
        showStep()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == dataPickerCode && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val storage = com.breezybuilds.cheatstation.storage.StorageManager(this)
            try {
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
                val validation = storage.validateRoot(doc)
                if (!validation.first || doc?.canWrite() != true) {
                    MaterialAlertDialogBuilder(this).setMessage(if (!validation.first) validation.second else "The selected folder is not writable.").setPositiveButton("OK", null).show()
                    return
                }
                val pkg = storage.selected3dsEmulatorPackage ?: com.breezybuilds.cheatstation.emulator.EmulatorDetector.detect3dsEmulator(this)?.packageName
                if (pkg != null) {
                    storage.setSelected3dsEmulator(pkg)
                    storage.saveEmulatorRootUri(pkg, uri)
                }
                showStep()
            } catch (_: Exception) {
                MaterialAlertDialogBuilder(this).setMessage("Android could not grant access to that emulator folder. Some Android/data folders are blocked by the system. You can skip this step and use a supported emulator export or transfer location.").setPositiveButton("OK", null).show()
            }
            return
        }
        if (requestCode == romPickerCode && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                UniversalRoms.save(this, uri)
            } catch (_: SecurityException) {
                MaterialAlertDialogBuilder(this).setMessage("Folder access could not be saved. Please choose the folder again.").setPositiveButton("OK", null).show()
                return
            }
            cachedRoot = null
            cachedResult = null
            scanConfirmed = false
            showStep()
        }
    }
    private fun pickRoms() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, romPickerCode)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        outState.putString("pending_scan_mode", pendingScanMode.name)
        outState.putBoolean("scan_confirmed", scanConfirmed)
        outState.putStringArrayList("expanded_manufacturers", ArrayList(expandedManufacturers))
        super.onSaveInstanceState(outState)
    }
    private fun text(value: String, size: Float, bold: Boolean = false, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun shape(fill: Int, stroke: Int = line) = GradientDrawable().apply {
        cornerRadius = d(15).toFloat(); setColor(fill); setStroke(d(1), stroke)
    }
    private fun addText(value: String, size: Float, bold: Boolean = false, color: Int = ink, top: Int = 0) {
        body.addView(text(value, size, bold, color), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(top) })
    }
    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, d(1)))
    }
    private fun action(label: String, onClick: () -> Unit) {
        val button = Ui.button(this, label, onClick = onClick).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            setTextColor(0xFF2459A6.toInt())
        }
        footer.addView(button, LinearLayout.LayoutParams(-1, d(48)).apply { topMargin = d(7) })
    }
    private fun showStep() {
        scanGeneration++
        pendingFinishAfterScan = false
        scanRunning = false
        body.removeAllViews(); footer.removeAllViews()
        addText("BREEZY'S RETRO LOADER  ·  ${step + 1} OF 3", 11f, true, 0xFF93B2DA.toInt())
        addText(when(step) { 0 -> "Choose your systems"; 1 -> "Your emulators"; else -> "Find your games" }, 25f, true, top = 12)
        addText(when(step) { 0 -> "Select the consoles you'd like to use. You can change these later."; 1 -> "We've checked for compatible emulators already on your device."; else -> "Select one ROMs folder. We'll look inside its subfolders for your games." }, 14f, color = muted, top = 7)
        when(step) { 0 -> showSystems(); 1 -> showEmulators(); else -> showRoms() }
    }
    private fun showSystems() {
        val columns = if (resources.configuration.screenWidthDp >= 520) 2 else 1
        RetroSystemRegistry.manufacturers.forEach { manufacturer ->
            val systems = RetroSystemRegistry.available.filter { it.manufacturer == manufacturer }
            val expanded = manufacturer in expandedManufacturers
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(d(14), d(12), d(14), d(12))
                background = shape(0xFF17273B.toInt())
                isClickable = true
                isFocusable = true
                contentDescription = "$manufacturer, ${if (expanded) "expanded" else "collapsed"}"
            }
            header.addView(text(manufacturer, 16f, true), LinearLayout.LayoutParams(0, -2, 1f))
            val selectedCount = systems.count { it.id in selected }
            header.addView(text(if (systems.isEmpty()) "Coming later" else "$selectedCount / ${systems.size} selected", 12f, color = muted))
            header.addView(text(if (expanded) "  ▴" else "  ▾", 20f, color = 0xFFB4CEEF.toInt()))
            header.setOnClickListener {
                if (expanded) expandedManufacturers.remove(manufacturer) else expandedManufacturers.add(manufacturer)
                showStep()
            }
            group.addView(header)
            if (expanded) {
                if (systems.isEmpty()) {
                    group.addView(text("More systems will appear here as emulator and cheat support is added.", 12f, color = muted),
                        LinearLayout.LayoutParams(-1, -2).apply { setMargins(d(14), d(12), d(14), d(6)) })
                } else {
                    val grid = GridLayout(this).apply { columnCount = columns; useDefaultMargins = false }
                    systems.forEachIndexed { index, system ->
                        val chosen = system.id in selected
                        val tile = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(d(14), d(9), d(12), d(9))
                            background = shape(if (chosen) 0xFF203650.toInt() else 0xFF142033.toInt(),
                                if (chosen) 0xFF668FC3.toInt() else line)
                            isClickable = true
                            isFocusable = true
                            contentDescription = "${system.title}, ${if (chosen) "selected" else "not selected"}"
                        }
                        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                        head.addView(text(system.symbol, 19f, color = 0xFFB4CEEF.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
                        head.addView(text(if (chosen) "✓" else "○", 18f, color = if (chosen) 0xFF8BD0B1.toInt() else muted))
                        tile.addView(head)
                        tile.addView(text(system.title, 14f, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(4) })
                        tile.setOnClickListener {
                            if (system.id in selected) selected.remove(system.id) else selected.add(system.id)
                            showStep()
                        }
                        grid.addView(tile, GridLayout.LayoutParams().apply {
                            width = 0
                            height = d(76)
                            columnSpec = GridLayout.spec(index % columns, 1f)
                            rowSpec = GridLayout.spec(index / columns)
                            setMargins(d(3), d(4), d(3), d(4))
                        })
                    }
                    group.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6) })
                }
            }
            body.addView(group, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
        }
        val count = RetroCatalog.selectedEmulators(selected).size
        addText("${selected.size} systems selected  ·  $count emulator${if (count == 1) "" else "s"} needed", 12f, color = muted, top = 12)
        if ("Wii" in selected && "GameCube" in selected)
            addText("Wii and GameCube share Dolphin.", 12f, color = muted, top = 4)
        action("Continue") {
            if (selected.isEmpty()) MaterialAlertDialogBuilder(this).setMessage("Choose at least one system, or skip setup.").setPositiveButton("OK", null).show()
            else { step = 1; showStep() }
        }
        footer.addView(text("Skip setup", 13f, color = muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, d(9), 0, d(3))
            isClickable = true
            isFocusable = true
            setOnClickListener { finishSetup() }
        })
    }
    private fun showEmulators() {
        val storage = com.breezybuilds.cheatstation.storage.StorageManager(this)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val systems = listOf("PS2" to "PlayStation 2", "3DS" to "Nintendo 3DS", "Wii" to "Wii", "GameCube" to "GameCube")
            .filter { it.first in selected }
        systems.forEach { (system, label) ->
            val options: List<Pair<String, String>> = when (system) {
                "3DS" -> com.breezybuilds.cheatstation.emulator.EmulatorDetector.detect3dsEmulators(this)
                    .map { it.packageName to it.name }
                "PS2" -> RetroCatalog.entries.first { it.id == "nethersx2" }.packages
                    .filter { com.breezybuilds.cheatstation.emulator.EmulatorDetector.packageInstalled(this, it) }
                    .map { pkg ->
                        val name = runCatching {
                            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                        }.getOrDefault("PS2 emulator")
                        pkg to name
                    }
                else -> {
                    val dolphin = RetroCatalog.entries.first { it.id == "dolphin" }
                    if (RetroCatalog.installed(this, dolphin)) listOf("dolphin" to "Dolphin") else emptyList()
                }
            }
            val saved = if (system == "3DS") storage.selected3dsEmulatorPackage
                else prefs.getString("preferred_emulator_$system", null)
            val chosen = options.firstOrNull { it.first == saved } ?: options.firstOrNull()
            if (system == "3DS" && chosen != null && storage.selected3dsEmulatorPackage != chosen.first)
                storage.setSelected3dsEmulator(chosen.first)
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(14), d(12), d(14), d(12))
                background = shape(0xFF142237.toInt())
            }
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(label, 16f, true))
            labels.addView(text(if (chosen == null) "Setup needed · No emulator detected"
                else "Setup · Using ${chosen.second}", 12f, color = muted))
            header.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(text("⌄", 22f, color = muted))
            panel.addView(header)
            val choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            choices.addView(text(if (options.size > 1) "Change your emulator" else "Your emulator", 13f, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })
            options.forEach { (pkg, name) ->
                choices.addView(text("${if (pkg == chosen?.first) "◉" else "○"}  $name · Installed", 14f,
                    color = if (pkg == chosen?.first) 0xFF8CD2AD.toInt() else ink).apply {
                    setPadding(d(8), d(10), d(8), d(10))
                    setOnClickListener {
                        if (system == "3DS") storage.setSelected3dsEmulator(pkg)
                        else prefs.edit().putString("preferred_emulator_$system", pkg).apply()
                        showStep()
                    }
                })
            }
            if (options.isEmpty()) {
                val entryId = when (system) { "3DS" -> "azahar"; "PS2" -> "nethersx2"; else -> "dolphin" }
                RetroCatalog.entries.firstOrNull { it.id == entryId }?.let { entry ->
                    choices.addView(text("View compatible emulators ›", 13f).apply {
                        setPadding(d(8), d(12), d(8), d(12))
                        setOnClickListener { showDetails(entry) }
                    })
                }
            }
            panel.addView(choices)
            header.setOnClickListener { choices.visibility = if (choices.visibility == View.GONE) View.VISIBLE else View.GONE }
            body.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
        }
        if ("3DS" in selected) {
            val installed = com.breezybuilds.cheatstation.emulator.EmulatorDetector.detect3dsEmulators(this)
            val chosenName = installed.firstOrNull { it.packageName == storage.selected3dsEmulatorPackage }?.name ?: "3DS emulator"
            if (!storage.has3dsStorage()) {
                addText("$chosenName · Data access required", 12f, color = muted, top = 12)
                body.addView(Ui.button(this, "Grant 3DS data access", onClick = {
                    MaterialAlertDialogBuilder(this).setTitle("Allow 3DS emulator data access")
                        .setMessage("Select the emulator user folder containing sdmc, nand or cheats.")
                        .setPositiveButton("Choose folder") { _, _ ->
                            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }, dataPickerCode)
                        }.setNegativeButton("Later", null).show()
                }), LinearLayout.LayoutParams(-1, d(48)).apply { topMargin = d(10) })
            }
        }
        action("Continue to ROMs folder") { step = 2; showStep() }
        footer.addView(text("Refresh detection  ·  Change systems", 13f, color = muted).apply {
            gravity = Gravity.CENTER; setPadding(0, d(10), 0, d(6)); isClickable = true
            setOnClickListener { MaterialAlertDialogBuilder(this@RetroSetupActivity).setItems(arrayOf("Refresh detection", "Change systems")) { _, which ->
                if (which == 1) step = 0
                showStep()
            }.show() }
        })
    }
    private fun showRoms() {
        val root = UniversalRoms.uri(this)
        addText(if (root == null) "No ROMs folder selected" else "ROMs folder selected", 17f, true, top = 24)
        addText(if (root == null) "Choose a folder containing your 3DS, PS2, Wii and GameCube games." else root.lastPathSegment.orEmpty(), 12f, color = muted, top = 7)
        val choose = Ui.button(this, if (root == null) "Choose ROMs folder" else "Change ROMs folder", onClick = { pickRoms() })
        body.addView(choose, LinearLayout.LayoutParams(-1, d(48)).apply { topMargin = d(16) })
        if (root != null) {
            addText("Scanning mode", 17f, true, top = 20)
            val scanMode = pendingScanMode
            listOf(
                UniversalRoms.ScanMode.SMART to ("Smart Scan ROMs" to "Scan only recognised console folders. Better for large libraries."),
                UniversalRoms.ScanMode.ALL to ("Scan All Files" to "Search all subfolders. Better for smaller or unorganised libraries.")
            ).forEach { (mode, labels) ->
                val row = text("${if (scanMode == mode) "◉" else "○"}  ${labels.first}\n     ${labels.second}", 14f,
                    color = if (scanMode == mode) ink else muted).apply {
                    setPadding(d(14), d(12), d(14), d(12))
                    background = shape(if (scanMode == mode) 0xFF203650.toInt() else 0xFF142033.toInt())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (pendingScanMode != mode) {
                            pendingScanMode = mode
                            scanConfirmed = false
                            cachedResult = null
                            showStep()
                        }
                    }
                }
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })
            }
            if (!scanConfirmed) {
                addText("Your scan will not begin until you confirm your choice.", 12f, color = muted, top = 14)
                body.addView(Ui.button(this, "Start Scan", onClick = {
                    UniversalRoms.saveScanMode(this, pendingScanMode)
                    scanConfirmed = true
                    cachedResult = null
                    showStep()
                }), LinearLayout.LayoutParams(-1, d(48)).apply { topMargin = d(12) })
            } else {
            addText("Preparing your library", 19f, true, top = 20)
            addText("Finding games in your selected ROM folders.", 12f, color = muted, top = 6)
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(16), d(14), d(16), d(14))
                background = shape(0xFF101F32.toInt())
            }
            body.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14) })
            val progressRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val status = text("Locating console folders…", 14f, true)
            progressRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            val folderCount = text("Discovering", 12f, color = muted)
            progressRow.addView(folderCount)
            panel.addView(progressRow)
            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
                progressTintList = android.content.res.ColorStateList.valueOf(0xFF6D9EE6.toInt())
            }
            panel.addView(progressBar, LinearLayout.LayoutParams(-1, d(7)).apply { topMargin = d(12) })
            val explanation = text("Locating folders · total not known yet", 12f, color = muted)
            panel.addView(explanation, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(7) })
            val filePanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(16), d(14), d(16), d(14))
                background = shape(0xFF101F32.toInt())
            }
            body.addView(filePanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })
            filePanel.visibility = View.GONE
            val fileHeader = text("Scanning game files · Waiting for folders", 14f, true)
            filePanel.addView(fileHeader)
            val fileProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
                progressTintList = android.content.res.ColorStateList.valueOf(0xFF6D9EE6.toInt())
            }
            filePanel.addView(fileProgress, LinearLayout.LayoutParams(-1, d(7)).apply { topMargin = d(12) })
            val fileDetails = text("Waiting to scan", 12f, color = muted)
            filePanel.addView(fileDetails, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(7) })
            val systemPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(0xFF101F32.toInt())
            }
            body.addView(systemPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })
            systemPanel.visibility = View.GONE
            val systemNames = listOf("3DS", "PS2", "Wii", "GameCube").filter { it in selected }
            val systemLabels = mapOf("3DS" to "Nintendo 3DS", "PS2" to "PlayStation 2", "Wii" to "Wii", "GameCube" to "GameCube")
            val stateLabels = linkedMapOf<String, Pair<TextView, TextView>>()
            systemNames.forEachIndexed { index, system ->
                if (index > 0) divider(systemPanel)
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(d(14), d(10), d(14), d(10))
                }
                val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val name = text("◷  ${systemLabels[system]}", 14f, true)
                header.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                val state = text("Pending", 12f, color = muted)
                header.addView(state)
                item.addView(header)
                val details = text("Waiting to scan", 12f, color = muted)
                item.addView(details, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = d(22); topMargin = d(3) })
                systemPanel.addView(item)
                stateLabels[system] = state to details
            }
            val completion = text("Preparing your game library…\nDiscovery will be saved before you continue.", 12f, color = muted).apply {
                gravity = Gravity.CENTER
                setPadding(d(12), d(12), d(12), d(12))
                background = shape(0xFF1B2A3E.toInt())
            }
            body.addView(completion, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(12) })
            completion.isClickable = false
            completion.isFocusable = false
            fun updateSystems(counts: Map<String, Int>, current: String?, completed: Set<String>, finished: Boolean = false) {
                systemNames.forEach { system ->
                    val (state, details) = stateLabels.getValue(system)
                    val count = counts[system] ?: 0
                    val done = finished || system in completed
                    val active = !done && system == current
                    state.text = when { done -> "Complete"; active -> "Scanning"; else -> "Pending" }
                    state.setTextColor(when { done -> 0xFF8BD0B1.toInt(); active -> 0xFF79A9EE.toInt(); else -> muted })
                    details.text = when {
                        done -> if (count == 0) "No games found" else "$count games found"
                        active -> if (count == 0) "Checking files…" else "$count games found so far"
                        else -> "Waiting to scan"
                    }
                }
            }
            fun render(result: UniversalRoms.Result) {
                scanRunning = false
                scanSucceeded = !result.truncated
                progressBar.isIndeterminate = false
                progressBar.progress = if (scanSucceeded) 100 else progressBar.progress
                status.text = if (scanSucceeded) "Discovery complete" else "Scan interrupted"
                folderCount.text = if (scanSucceeded) "Complete" else "Incomplete"
                explanation.text = if (scanSucceeded) "Console folder discovery complete" else "Scan interrupted"
                filePanel.visibility = View.VISIBLE
                systemPanel.visibility = View.VISIBLE
                fileHeader.text = if (scanSucceeded) "Game file discovery complete" else "Game file scan interrupted"
                fileProgress.isIndeterminate = false
                fileProgress.progress = if (scanSucceeded) 100 else 0
                fileDetails.text = "${result.scanned} items checked · ${if (scanSucceeded) "Discovery saved" else "Retry required"}"
                updateSystems(result.counts, null, emptySet(), scanSucceeded)
                completion.background = shape(if (scanSucceeded) 0xFFFFFFFF.toInt() else 0xFF1B2A3E.toInt())
                completion.setTextColor(if (scanSucceeded) 0xFF2459A6.toInt() else muted)
                completion.text = if (scanSucceeded) "✓ Your game library is ready\nTap here to open your game library" else "Scan incomplete · Tap here to retry"
                completion.isClickable = true
                completion.isFocusable = true
                completion.setOnClickListener {
                    if (scanSucceeded) finishSetup() else { cachedResult = null; showStep() }
                }
            }
            if (cachedRoot == root && cachedMode == scanMode && cachedSystems == selected.toSet() && cachedResult != null) render(cachedResult!!)
            else {
                scanRunning = true
                scanSucceeded = false
                val generation = scanGeneration
                scanExecutor.execute {
                    try {
                        var latestCounts: Map<String, Int> = emptyMap()
                        val result = UniversalRoms.preview(applicationContext, root, mode = scanMode, selectedSystems = selected.toSet(), progress = { _, counts ->
                            latestCounts = counts
                        }, detailProgress = { detail ->
                            val countsSnapshot = latestCounts
                            runOnUiThread {
                                if (generation == scanGeneration && step == 2) {
                                    val total = detail.foldersTotal
                                    val done = detail.foldersDone
                                    val discovering = detail.discovering
                                    status.text = if (discovering) "Locating console folders…" else "Console folders located"
                                    folderCount.text = if (discovering) "Discovering" else if (total != null) "$total located" else "Located"
                                    progressBar.isIndeterminate = discovering
                                    if (!discovering) progressBar.progress = 100
                                    explanation.text = if (discovering) "Searching for recognised console folders…" else "${total ?: 0} folders located · Scanning files below"
                                    filePanel.visibility = if (discovering) View.GONE else View.VISIBLE
                                    systemPanel.visibility = if (discovering) View.GONE else View.VISIBLE
                                    fileHeader.text = if (discovering) "Scanning game files · Waiting for folders" else "Scanning game files"
                                    fileDetails.text = if (discovering) "Waiting to scan" else "$done of ${total ?: done} folders processed · Checking files"
                                    fileProgress.isIndeterminate = discovering || total == null || total == 0
                                    if (!fileProgress.isIndeterminate && total != null && total > 0) fileProgress.progress = (done * 100 / total).coerceIn(0, 100)
                                    updateSystems(countsSnapshot, detail.currentSystem, detail.completedSystems)
                                }
                            }
                        })
                        runOnUiThread {
                            if (generation == scanGeneration && step == 2) {
                                cachedRoot = root; cachedMode = scanMode; cachedSystems = selected.toSet(); cachedResult = result; render(result)
                                if (pendingFinishAfterScan && scanSucceeded) finishSetup()
                                else if (pendingFinishAfterScan) { pendingFinishAfterScan = false; status.text = "Scan interrupted. Retry before finishing setup." }
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread { if (generation == scanGeneration) {
                            scanRunning = false
                            scanSucceeded = false
                            pendingFinishAfterScan = false
                            progressBar.isIndeterminate = false
                            status.text = "Could not scan folder"
                            explanation.text = "Check folder access and retry"
                        } }
                    }
                }
            }
        }
            }
        addText("ROM folder access and emulator cheat storage are configured separately in Settings.", 12f, color = muted, top = 16)
        if (root == null || !scanConfirmed) {
            addText("Choose a ROMs folder and press Start Scan before finishing setup.", 12f, color = muted, top = 12)
        }
        action("Back to emulators") { step = 1; showStep() }
    }
    private fun showDetails(entry: RetroCatalog.Entry) {
        val detected = RetroCatalog.detectedName(this, entry)
        MaterialAlertDialogBuilder(this)
            .setTitle(if (entry.id == "azahar" && detected != null) detected else entry.title)
            .setMessage("${entry.systems}\n\n${if (detected != null) "Installed on this device" else "Not detected on this device"}\n\n${if (detected != null) "This compatible emulator is installed. No alternative emulator is required." else entry.note}\n\nStorage configuration is managed separately in Settings.")
            .setPositiveButton("Official downloads") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.releases))) }
            .setNegativeButton("Close", null)
            .show()
    }
    override fun onDestroy() {
        scanGeneration++
        scanExecutor.shutdownNow()
        super.onDestroy()
    }
    private fun finishSetup() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(SYSTEMS, selected.toSet()).putBoolean(FINISHED, true).apply()
        if (!fromSettings) startActivity(Intent(this, WelcomeActivity::class.java).putExtra("skip_retro_setup", true))
        finish()
    }
}

package com.breezybuilds.cheatstation.emulator

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Shared ROM folder discovery. Existing per-system game paths remain independent. */
object UniversalRoms {
    private const val PREFS = "retro_loader_roms"
    private const val ROOT = "root_uri"
    private const val SCAN_MODE = "scan_mode"
    enum class ScanMode { SMART, ALL }
    fun scanMode(context: Context): ScanMode = if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SCAN_MODE, "SMART") == "ALL") ScanMode.ALL else ScanMode.SMART
    fun saveScanMode(context: Context, mode: ScanMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SCAN_MODE, mode.name).apply()
    }
    fun uri(context: Context): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(ROOT, null)?.let(Uri::parse)
    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ROOT, uri.toString()).apply()
    }
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ROOT).apply()
    }
    /** Use the existing persisted parent grant; do not manufacture child tree grants. */
    fun systemFolder(context: Context, system: String): DocumentFile? {
        val root = uri(context) ?: return null
        if (context.contentResolver.persistedUriPermissions.none { it.uri == root && it.isReadPermission }) return null
        val tree = DocumentFile.fromTreeUri(context, root) ?: return null
        val names = when (system) {
            "3DS" -> setOf("3ds", "n3ds", "nintendo3ds")
            "PS2" -> setOf("ps2", "playstation2")
            "Wii" -> setOf("wii", "nintendowii")
            "GameCube" -> setOf("gc", "gcn", "ngc", "gamecube", "nintendogamecube")
            else -> emptySet()
        }
        val queue = ArrayDeque<Pair<DocumentFile, Int>>()
        queue.add(tree to 0)
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            if (normalized(dir.name.orEmpty()) in names) return dir
            if (depth < 3) {
                try { dir.listFiles().filter { it.isDirectory }.forEach { queue.add(it to depth + 1) } }
                catch (_: Exception) { }
            }
        }
        return null
    }
    /** The same effective game directory used by scanners and Settings. */
    fun gamesFolderLabel(context: Context, system: String): String =
        if (uri(context) != null) "Automatic · from universal ROMs (verify in library)" else "Not set"

    data class DiscoveredRom(val system: String, val name: String, val uri: String)
    private fun discoveryFile(context: Context) = File(context.filesDir, "retro_discovered_roms.json")
    fun loadDiscovered(context: Context, system: String): List<DiscoveredRom> {
        return try {
        val obj = JSONObject(discoveryFile(context).readText())
        if (obj.optString("root") != uri(context)?.toString() || obj.optString("mode") != scanMode(context).name) return emptyList()
        val a = obj.getJSONArray("games")
        (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("system") == system }
            .map { DiscoveredRom(it.getString("system"), it.getString("name"), it.getString("uri")) }
        } catch (_: Exception) { emptyList() }
    }
    private fun saveDiscovered(context: Context, root: Uri, mode: ScanMode, systems: Set<String>, items: List<DiscoveredRom>) {
        val file = discoveryFile(context)
        val existing = try {
            val obj = JSONObject(file.readText())
            if (obj.optString("root") == root.toString()) {
                val arr = obj.getJSONArray("games")
                (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                    DiscoveredRom(it.getString("system"), it.getString("name"), it.getString("uri"))
                }.filterNot { it.system in systems }
            } else emptyList()
        } catch (_: Exception) { emptyList() }
        val array = JSONArray()
        (existing + items).distinctBy { it.uri }.forEach {
            array.put(JSONObject().put("system", it.system).put("name", it.name).put("uri", it.uri))
        }
        val json = JSONObject().put("root", root.toString()).put("mode", mode.name).put("games", array)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) { file.writeText(json.toString()); temp.delete() }
    }
    data class Result(val counts: Map<String, Int>, val ambiguous: Int, val scanned: Int, val truncated: Boolean)
    private fun normalized(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun systemHint(name: String): String? = when (normalized(name)) {
        "3ds", "n3ds", "nintendo3ds", "citra", "azahar", "lime3ds" -> "3DS"
        "ps2", "playstation2", "pcsx2", "aethersx2", "nethersx2" -> "PS2"
        "wii", "nintendowii", "rvl" -> "Wii"
        "gc", "gcn", "ngc", "gamecube", "nintendogamecube" -> "GameCube"
        else -> null
    }
    private fun classify(name: String, hint: String?): String? {
        val ext = name.lowercase().substringAfterLast('.', "")
        return when (ext) {
            "3ds", "cia", "cxi", "cci" -> "3DS"
            "wbfs", "wia" -> "Wii"
            "gcm" -> "GameCube"
            "rvz", "gcz" -> hint?.takeIf { it == "Wii" || it == "GameCube" }
            "iso", "bin", "chd", "cso", "gz", "zso", "elf" -> hint?.takeIf { it == "PS2" || it == "Wii" || it == "GameCube" }
            else -> ""
        }
    }
    data class ScanProgress(val foldersDone: Int, val foldersTotal: Int?, val currentSystem: String?, val completedSystems: Set<String>, val discovering: Boolean)
    /** Worker-thread scan. SMART never descends into unrelated folders. */
    fun preview(context: Context, root: Uri, limit: Int = Int.MAX_VALUE,
                progress: (Int, Map<String, Int>) -> Unit = { _, _ -> },
                mode: ScanMode = scanMode(context),
                selectedSystems: Set<String> = setOf("3DS", "PS2", "Wii", "GameCube"),
                detailProgress: (ScanProgress) -> Unit = {}): Result {
        val counts = linkedMapOf("3DS" to 0, "PS2" to 0, "Wii" to 0, "GameCube" to 0)
        val discovered = mutableListOf<DiscoveredRom>()
        var ambiguous = 0
        var visited = 0
        val tree = DocumentFile.fromTreeUri(context, root) ?: return Result(counts, 0, 0, false)
        progress(0, counts.toMap())
        val prioritized = ArrayDeque<Pair<DocumentFile, String?>>()
        val fallback = ArrayDeque<Pair<DocumentFile, String?>>()
        val rootHint = systemHint(tree.name.orEmpty())
        var foldersDone = 0
        var foldersTotal: Int? = null
        val completedSystems = mutableSetOf<String>()
        detailProgress(ScanProgress(0, null, null, emptySet(), true))
        if (mode == ScanMode.ALL || rootHint in selectedSystems) fallback.add(tree to rootHint)
        else {
            // Only look for console folders in the first three directory levels.
            // Directory discovery does not inspect unrelated file contents.
            val search = ArrayDeque<Pair<DocumentFile, Int>>()
            search.add(tree to 0)
            while (search.isNotEmpty() && !Thread.currentThread().isInterrupted) {
                val (dir, depth) = search.removeFirst()
                val children = try { dir.listFiles() } catch (_: Exception) { emptyArray() }
                for (child in children) {
                    if (!child.isDirectory) continue
                    val hint = systemHint(child.name.orEmpty())
                    if (hint in selectedSystems) prioritized.add(child to hint)
                    else if (depth < 2) search.add(child to depth + 1)
                }
            }
            progress(0, counts.toMap())
        }
        // The total is only known for the initial folder set. Nested folders can be discovered during scanning.
        var foldersQueued = prioritized.size + fallback.size
        foldersTotal = foldersQueued
        detailProgress(ScanProgress(0, foldersTotal, null, emptySet(), false))
        while ((prioritized.isNotEmpty() || fallback.isNotEmpty()) && visited < limit && !Thread.currentThread().isInterrupted) {
            val (dir, hint) = if (prioritized.isNotEmpty()) prioritized.removeLast() else fallback.removeFirst()
            detailProgress(ScanProgress(foldersDone, foldersTotal, hint, completedSystems.toSet(), false))
            val children = try { dir.listFiles() } catch (_: Exception) { emptyArray() }
            for (file in children) {
                if (Thread.currentThread().isInterrupted || visited >= limit) break
                visited++
                val name = file.name.orEmpty()
                if (file.isDirectory) {
                    val known = systemHint(name)
                    val nextHint = known ?: hint
                    if (mode == ScanMode.ALL) {
                        if (known != null || hint != null) prioritized.add(file to nextHint)
                        else fallback.add(file to null)
                        foldersQueued++
                    } else if (nextHint in selectedSystems) { prioritized.add(file to nextHint); foldersQueued++ }
                    foldersTotal = foldersQueued
                } else if (file.isFile) {
                    val system = classify(name, hint)
                    when {
                        system == null && mode == ScanMode.ALL -> ambiguous++
                        system in selectedSystems -> {
                            counts[system!!] = counts.getValue(system) + 1
                            discovered.add(DiscoveredRom(system, name, file.uri.toString()))
                        }
                    }
                }
                if (visited % 100 == 0) progress(visited, counts.toMap())
            }
            foldersDone++
            if (hint != null && prioritized.none { it.second == hint } && fallback.none { it.second == hint }) completedSystems.add(hint)
            detailProgress(ScanProgress(foldersDone, foldersTotal, hint, completedSystems.toSet(), false))
        }
        progress(visited, counts.toMap())
        if (!Thread.currentThread().isInterrupted && visited < limit) {
            try { saveDiscovered(context, root, mode, selectedSystems, discovered) } catch (_: Exception) { }
        }
        return Result(counts, ambiguous, visited, visited >= limit && (prioritized.isNotEmpty() || fallback.isNotEmpty()))
    }
}

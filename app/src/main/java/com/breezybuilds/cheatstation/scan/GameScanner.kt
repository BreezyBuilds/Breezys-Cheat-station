package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.storage.StorageManager
import com.breezybuilds.cheatstation.util.AppLog
import com.breezybuilds.cheatstation.util.TitleId

data class ScanResult(val games: List<Game>, val warnings: List<String>, val error: String? = null)

/**
 * Finds games in the Azahar data folder:
 *  1. Installed titles: sdmc/Nintendo 3DS/<id0>/<id1>/title/<high>/<low>/content (Title ID from the folder names,
 *     version from the TMD, name/region from the game's icon data when readable).
 *  2. CIA / 3DS / CXI files found in the Azahar folder (top level) and in an optional "games folder".
 * Call from a background thread.
 */
class GameScanner(private val ctx: Context, private val storage: StorageManager, private val ids: TitleIdResolver) {

    private class Installed(val tid: String, var baseTmd: Int? = null, var meta: TitleMeta? = null)

    private val gameHighs = setOf("00040000", "00040002")   // application, demo
    private val updateHigh = "0004000E"
    private val fileExt = setOf("cia", "3ds", "cci", "cxi", "app")
    private val skipDirs = setOf("sdmc", "nand", "cheats", "shaders", "cache", "log", "states", "sysdata", "load", "dump", "screenshots", "config")

    fun scan(progress: (String) -> Unit = {}): ScanResult {
        val root = storage.rootDoc() ?: return ScanResult(emptyList(), emptyList(), "No Azahar folder is selected (or access was lost).")
        val warnings = mutableListOf<String>()
        val installed = LinkedHashMap<String, Installed>()
        val updates = HashMap<String, Int>()

        progress("Looking for installed games…")
        try { scanInstalled(root, installed, updates, warnings, progress) }
        catch (e: Exception) { AppLog.e("Scan", "Installed-title scan failed", e); warnings += "Could not fully read the installed titles folder." }

        val hits = ArrayList<Pair<DocumentFile, Int>>()
        progress("Looking for CIA / 3DS / CXI files…")
        try { collectFiles(root, 0, hits) } catch (e: Exception) { AppLog.w("Scan", "root file scan failed") }
        storage.gamesUri?.takeIf { storage.hasPermission(it) }?.let { u ->
            DocumentFile.fromTreeUri(ctx, u)?.let { try { collectFiles(it, 3, hits) } catch (e: Exception) { warnings += "Could not read the games folder." } }
        }

        val games = LinkedHashMap<String, Game>()
        for ((tid, inst) in installed) {
            val m = inst.meta
            val updTmd = updates["00040000" + TitleId.low(tid)]
            games[tid] = Game(
                title = m?.name ?: "Unknown game",
                titleId = tid,
                region = m?.region,
                version = VersionResolver.resolve(inst.baseTmd, updTmd),
                productCode = m?.productCode,
                installed = true,
                source = "Installed title",
            )
        }

        var n = 0
        for ((doc, _) in hits.take(500)) {
            progress("Reading ${doc.name} (${++n}/${minOf(hits.size, 500)})")
            val meta = ids.fromFile(doc)
            val (tid, fromName) = ids.resolve(meta, doc.name)
            if (tid == null) { warnings += "Could not read a Title ID from ${doc.name}."; continue }
            if (fromName) warnings += "${doc.name}: Title ID taken from the file name (header could not be read)."
            if (TitleId.high(tid) == updateHigh) {
                meta?.tmdVersion?.let { updates["00040000" + TitleId.low(tid)] = it }
                continue
            }
            if (TitleId.high(tid) !in gameHighs) continue
            val old = games[tid]
            val fileVersion = VersionResolver.resolve(null, updates["00040000" + TitleId.low(tid)], meta?.tmdVersion)
            games[tid] = if (old == null) Game(
                title = meta?.name ?: doc.name?.substringBeforeLast('.') ?: "Unknown game",
                titleId = tid, region = meta?.region, version = fileVersion, productCode = meta?.productCode,
                installed = false, source = "File: ${doc.name}",
            ) else old.copy(
                title = if (old.title == "Unknown game") meta?.name ?: old.title else old.title,
                region = old.region ?: meta?.region,
                version = old.version ?: fileVersion,
                productCode = old.productCode ?: meta?.productCode,
            )
        }
        AppLog.i("Scan", "Found ${games.size} games (${installed.size} installed)")
        return ScanResult(games.values.sortedBy { it.title.lowercase() }, warnings)
    }

    private fun dirs(d: DocumentFile) = d.listFiles().filter { it.isDirectory }

    private fun scanInstalled(root: DocumentFile, out: MutableMap<String, Installed>, updates: MutableMap<String, Int>, warnings: MutableList<String>, progress: (String) -> Unit) {
        val nintendo = root.findFile("sdmc")?.findFile("Nintendo 3DS") ?: run {
            AppLog.i("Scan", "No sdmc/Nintendo 3DS folder (no installed titles)")
            return
        }
        for (id0 in dirs(nintendo)) for (id1 in dirs(id0)) {
            val title = id1.findFile("title") ?: continue
            for (highDir in dirs(title)) {
                val high = highDir.name.orEmpty().uppercase()
                if (high !in gameHighs && high != updateHigh) continue
                for (lowDir in dirs(highDir)) {
                    val tid = TitleId.fromParts(high, lowDir.name.orEmpty()) ?: continue
                    val content = lowDir.findFile("content") ?: continue
                    val files = content.listFiles()
                    val tmdVersion = files.filter { it.name.orEmpty().endsWith(".tmd", true) }
                        .mapNotNull { ids.fromTmd(it)?.second }.maxOrNull()
                    if (high == updateHigh) {
                        tmdVersion?.let { v -> updates.merge("00040000" + TitleId.low(tid), v) { a, b -> maxOf(a, b) } }
                        continue
                    }
                    progress("Reading $tid…")
                    var meta: TitleMeta? = null
                    for (app in files.filter { it.name.orEmpty().endsWith(".app", true) }.sortedBy { it.name }.take(2)) {
                        meta = ids.fromFile(app)
                        if (meta != null) break
                    }
                    val inst = out.getOrPut(tid) { Installed(tid) }
                    inst.baseTmd = tmdVersion ?: inst.baseTmd
                    inst.meta = meta ?: inst.meta
                    if (meta == null) AppLog.d("Scan", "No readable NCCH for $tid (encrypted or missing)")
                }
            }
        }
    }

    private fun collectFiles(dir: DocumentFile, depth: Int, out: MutableList<Pair<DocumentFile, Int>>) {
        for (f in dir.listFiles()) {
            if (out.size >= 500) return
            val name = f.name.orEmpty()
            if (f.isDirectory) {
                if (depth > 0 && name.lowercase() !in skipDirs) collectFiles(f, depth - 1, out)
            } else if (name.substringAfterLast('.', "").lowercase() in fileExt) {
                out += f to depth
            }
        }
    }
}

package com.breezybuilds.cheatstation.data

import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.util.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CachedCheats(val text: String, val fetchedAt: Long)
class CachedIndex(val ids: Set<String>, val fetchedAt: Long)

/** Small JSON-file cache (games, manual games, downloaded cheat files, source index). No database needed. */
class CacheStore(private val dir: File) {
    init { dir.mkdirs() }

    private fun key(sourceId: String) = Integer.toHexString(sourceId.hashCode())
    private fun cheatsDir() = File(dir, "cheats").also { it.mkdirs() }
    private fun read(f: File): JSONObject? = try { if (f.exists()) JSONObject(f.readText()) else null } catch (e: Exception) { AppLog.w("Cache", "Corrupt cache file ${f.name}"); f.delete(); null }
    private fun write(f: File, o: JSONObject) { try { f.parentFile?.mkdirs(); f.writeText(o.toString()) } catch (e: Exception) { AppLog.e("Cache", "Write failed for ${f.name}", e) } }

    private fun gameToJson(g: Game) = JSONObject().put("title", g.title).put("titleId", g.titleId)
        .put("region", g.region ?: JSONObject.NULL).put("version", g.version ?: JSONObject.NULL)
        .put("productCode", g.productCode ?: JSONObject.NULL).put("installed", g.installed).put("source", g.source)

    private fun gameFromJson(o: JSONObject) = Game(
        title = o.optString("title", "Unknown game"), titleId = o.getString("titleId"),
        region = o.optString("region").ifBlank { null }.takeIf { !o.isNull("region") },
        version = o.optString("version").ifBlank { null }.takeIf { !o.isNull("version") },
        productCode = o.optString("productCode").ifBlank { null }.takeIf { !o.isNull("productCode") },
        installed = o.optBoolean("installed"), source = o.optString("source"),
    )

    private fun saveList(name: String, games: List<Game>) =
        write(File(dir, name), JSONObject().put("games", JSONArray().also { a -> games.forEach { a.put(gameToJson(it)) } }))

    private fun loadList(name: String): List<Game> {
        val arr = read(File(dir, name))?.optJSONArray("games") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { try { gameFromJson(arr.getJSONObject(it)) } catch (e: Exception) { null } }
    }

    fun saveGames(g: List<Game>) = saveList("games.json", g)
    fun loadGames(): List<Game> = loadList("games.json")
    fun saveManual(g: List<Game>) = saveList("manual_games.json", g)
    fun loadManual(): List<Game> = loadList("manual_games.json")

    fun saveCheats(sourceId: String, titleId: String, text: String, fetchedAt: Long) =
        write(File(cheatsDir(), "${titleId}_${key(sourceId)}.json"), JSONObject().put("text", text).put("fetchedAt", fetchedAt))

    fun loadCheats(sourceId: String, titleId: String): CachedCheats? =
        read(File(cheatsDir(), "${titleId}_${key(sourceId)}.json"))?.let { CachedCheats(it.optString("text"), it.optLong("fetchedAt")) }

    fun saveIndex(sourceId: String, ids: Set<String>, fetchedAt: Long) =
        write(File(dir, "index_${key(sourceId)}.json"), JSONObject().put("ids", JSONArray(ids.toList())).put("fetchedAt", fetchedAt))

    fun loadIndex(sourceId: String): CachedIndex? = read(File(dir, "index_${key(sourceId)}.json"))?.let { o ->
        val a = o.optJSONArray("ids") ?: return null
        CachedIndex((0 until a.length()).map { a.getString(it) }.toSet(), o.optLong("fetchedAt"))
    }

    /** Removes downloaded cheat data and indexes (not the game list). Returns bytes freed. */
    fun clearCheatData(): Long {
        var n = 0L
        cheatsDir().listFiles()?.forEach { n += it.length(); it.delete() }
        dir.listFiles()?.filter { it.name.startsWith("index_") }?.forEach { n += it.length(); it.delete() }
        return n
    }

    fun cheatDataSize(): Long = (cheatsDir().listFiles()?.sumOf { it.length() } ?: 0L) +
        (dir.listFiles()?.filter { it.name.startsWith("index_") }?.sumOf { it.length() } ?: 0L)
}

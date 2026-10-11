package com.breezybuilds.cheatstation.provider

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class Ps2Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("breezy_ps2_settings", Context.MODE_PRIVATE)

    fun allSources(): List<Ps2CheatSource> = (Ps2CheatSources.all + customSources()).distinctBy { it.id }

    fun source(): Ps2CheatSource {
        val d = Ps2CheatSources.default
        val id = p.getString("source", d.id)
        return allSources().firstOrNull { it.id == id } ?: d
    }

    fun sources(): List<Ps2CheatSource> {
        val ids = p.getStringSet("enabled_ps2_sources", null)
        return if (ids == null) listOf(source()) else allSources().filter { it.id in ids }.ifEmpty { listOf(source()) }
    }

    fun saveSources(sources: List<Ps2CheatSource>) {
        require(sources.isNotEmpty())
        p.edit { putStringSet("enabled_ps2_sources", sources.map { it.id }.toSet()) }
        saveSource(sources.first())
    }

    fun saveSource(source: Ps2CheatSource) = p.edit { putString("source", source.id) }

    fun addCustomSource(source: Ps2CheatSource) {
        val custom = customSources().filterNot { it.id == source.id } + source
        val data = JSONArray()
        custom.forEach { item ->
            data.put(JSONObject().apply {
                put("name", item.name); put("description", item.description)
                put("owner", item.owner); put("repo", item.repo)
                put("branch", item.branch); put("path", item.path)
            })
        }
        val selected = sources().map { it.id }.toMutableSet()
        selected.add(source.id)
        p.edit {
            putString("custom_ps2_sources", data.toString())
            putStringSet("enabled_ps2_sources", selected)
        }
    }

    private fun customSources(): List<Ps2CheatSource> = runCatching {
        val data = JSONArray(p.getString("custom_ps2_sources", "[]"))
        (0 until data.length()).map { index ->
            val obj = data.getJSONObject(index)
            Ps2CheatSource(obj.getString("name"), obj.optString("description", "Custom PNACH source"),
                obj.getString("owner"), obj.getString("repo"),
                obj.optString("branch", "main"), obj.optString("path", "cheats"))
        }
    }.getOrDefault(emptyList())
}

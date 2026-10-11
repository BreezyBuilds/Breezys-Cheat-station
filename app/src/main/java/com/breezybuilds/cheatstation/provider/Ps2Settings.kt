package com.breezybuilds.cheatstation.provider

import android.content.Context
import androidx.core.content.edit

class Ps2Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("breezy_ps2_settings", Context.MODE_PRIVATE)
    fun source(): Ps2CheatSource {
        val d = Ps2CheatSources.default
        val id = p.getString("source", d.id)
        return Ps2CheatSources.all.firstOrNull { it.id == id } ?: d
    }
    fun sources(): List<Ps2CheatSource> {
        val ids = p.getStringSet("enabled_ps2_sources", null)
        return if (ids == null) listOf(source()) else Ps2CheatSources.all.filter { it.id in ids }.ifEmpty { listOf(source()) }
    }
    fun saveSources(sources: List<Ps2CheatSource>) {
        require(sources.isNotEmpty())
        p.edit { putStringSet("enabled_ps2_sources", sources.map { it.id }.toSet()) }
        saveSource(sources.first())
    }
    fun saveSource(source: Ps2CheatSource) = p.edit { putString("source", source.id) }
}

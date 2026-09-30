package com.breezybuilds.cheatstation.provider

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class DolphinSettings(ctx: Context) {

    private val p =
        ctx.getSharedPreferences(
            "breezy_dolphin_settings",
            Context.MODE_PRIVATE
        )

    fun sources(): List<DolphinCheatSource> {
        val builtIn = DolphinCheatSources.builtIn
        val custom = loadCustomSources()
        val all = builtIn + custom

        val saved = p.getStringSet(
            "sources",
            all.map { it.id }.toSet()
        ) ?: all.map { it.id }.toSet()

        val selected = all.filter {
            it.id in saved
        }

        return if (selected.isEmpty()) {
            listOf(DolphinCheatSources.default)
        } else {
            selected
        }
    }

    fun allSources(): List<DolphinCheatSource> {
        return DolphinCheatSources.builtIn + loadCustomSources()
    }

    fun saveSources(
        sources: List<DolphinCheatSource>
    ) {
        p.edit {
            putStringSet(
                "sources",
                sources.map { it.id }.toSet()
            )
        }
    }

    fun addCustomSource(
        source: DolphinCheatSource
    ) {
        val custom = loadCustomSources().toMutableList()

        custom.removeAll { it.id == source.id }
        custom += source.copy(custom = true)

        saveCustomSources(custom)

        val enabled = p.getStringSet(
            "sources",
            emptySet()
        )?.toMutableSet()
            ?: mutableSetOf()

        enabled += source.id

        p.edit {
            putStringSet("sources", enabled)
        }
    }

    fun removeCustomSource(
        source: DolphinCheatSource
    ) {
        val custom = loadCustomSources()
            .filter { it.id != source.id }

        saveCustomSources(custom)

        val enabled = p.getStringSet(
            "sources",
            emptySet()
        )?.toMutableSet()
            ?: mutableSetOf()

        enabled -= source.id

        p.edit {
            putStringSet("sources", enabled)
        }
    }

    private fun loadCustomSources(): List<DolphinCheatSource> {
        val raw = p.getString(
            "custom_sources",
            null
        ) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)

            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)

                    add(
                        DolphinCheatSource(
                            name = obj.getString("name"),
                            description = obj.optString(
                                "description",
                                "Custom Dolphin cheat repository."
                            ),
                            owner = obj.getString("owner"),
                            repo = obj.getString("repo"),
                            branch = obj.optString(
                                "branch",
                                "master"
                            ),
                            path = obj.optString(
                                "path",
                                ""
                            ),
                            custom = true
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomSources(
        sources: List<DolphinCheatSource>
    ) {
        val array = JSONArray()

        sources.forEach { source ->
            array.put(
                JSONObject().apply {
                    put("name", source.name)
                    put("description", source.description)
                    put("owner", source.owner)
                    put("repo", source.repo)
                    put("branch", source.branch)
                    put("path", source.path)
                }
            )
        }

        p.edit {
            putString(
                "custom_sources",
                array.toString()
            )
        }
    }
}

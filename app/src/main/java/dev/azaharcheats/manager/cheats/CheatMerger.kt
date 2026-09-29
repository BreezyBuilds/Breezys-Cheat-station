package dev.azaharcheats.manager.cheats

import dev.azaharcheats.manager.model.Cheat
import dev.azaharcheats.manager.model.CheatFile

data class MergeResult(
    val file: CheatFile,
    val added: List<String>,
    val identical: List<String>,
    val replaced: List<String>,
    /** Incoming cheats whose name exists with a *different* code and that were not replaced. */
    val conflicts: List<Cheat>,
    val notes: List<String>,
)

/** Pure logic for duplicate handling and editing an existing cheat file without touching unrelated entries. */
object CheatMerger {

    private fun dedupeIncoming(incoming: List<Cheat>, notes: MutableList<String>): List<Cheat> {
        val out = LinkedHashMap<String, Cheat>()
        for (c in incoming) {
            val prev = out[c.key]
            if (prev == null) out[c.key] = c
            else notes += if (prev.normalizedCode == c.normalizedCode) "Duplicate \"${c.name}\" in the source was ignored."
            else "\"${c.name}\" appears twice in the source with different codes; the first one is used."
        }
        return out.values.toList()
    }

    /** Which incoming cheats would collide with a different existing code. */
    fun conflicts(existing: CheatFile, incoming: List<Cheat>): List<Cheat> = merge(existing, incoming).conflicts

    fun merge(existing: CheatFile, incoming: List<Cheat>, replaceKeys: Set<String> = emptySet(), enableNew: Boolean = true): MergeResult {
        val notes = mutableListOf<String>()
        val list = existing.cheats.toMutableList()
        val added = mutableListOf<String>(); val identical = mutableListOf<String>()
        val replaced = mutableListOf<String>(); val conflicts = mutableListOf<Cheat>()
        val repl = replaceKeys.map { it.trim().lowercase() }.toSet()

        for (c in dedupeIncoming(incoming, notes)) {
            val idx = list.indexOfFirst { it.key == c.key }
            when {
                idx < 0 -> { list += c.copy(enabled = enableNew, installed = true); added += c.name }
                list[idx].normalizedCode == c.normalizedCode -> identical += c.name
                c.key in repl -> { list[idx] = list[idx].copy(lines = c.lines, description = c.description, installed = true); replaced += c.name }
                else -> conflicts += c
            }
        }
        return MergeResult(existing.copy(cheats = list), added, identical, replaced, conflicts, notes)
    }

    fun remove(existing: CheatFile, names: Set<String>): CheatFile {
        val keys = names.map { it.trim().lowercase() }.toSet()
        return existing.copy(cheats = existing.cheats.filterNot { it.key in keys })
    }

    fun setEnabled(existing: CheatFile, name: String, enabled: Boolean): CheatFile {
        val k = name.trim().lowercase()
        return existing.copy(cheats = existing.cheats.map { if (it.key == k) it.copy(enabled = enabled) else it })
    }
}

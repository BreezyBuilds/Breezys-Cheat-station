package com.breezybuilds.cheatstation.data

import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile

/** Combines compatible parsed cheat entries without enabling conflicting codes automatically. */
internal object MultiSourceCheatMerger {
    fun merge(titleId: String, inputs: List<Pair<String, CheatFile>>): CheatFile {
        val seen = HashSet<String>()
        val usedNames = HashSet<String>()
        val combined = mutableListOf<Cheat>()
        val warnings = mutableListOf<String>()
        for ((source, file) in inputs) {
            warnings.addAll(file.warnings.map { "$source: $it" })
            for (cheat in file.cheats) {
                val fingerprint = cheat.normalizedCode
                if (fingerprint.isBlank() || !seen.add(fingerprint)) continue
                var name = cheat.name
                if (!usedNames.add(name.trim().lowercase())) {
                    name = "${cheat.name} ($source)"
                    var counter = 2
                    while (!usedNames.add(name.trim().lowercase())) {
                        name = "${cheat.name} ($source #$counter)"
                        counter++
                    }
                }
                combined.add(cheat.copy(name = name, enabled = false,
                    description = listOfNotNull("Source: $source", cheat.description).joinToString(" · ")))
            }
        }
        val first = inputs.firstOrNull()?.second
        return CheatFile(titleId = titleId, version = first?.version, title = first?.title,
            cheats = combined, warnings = warnings)
    }
}

package com.breezybuilds.cheatstation.cheats.dolphin

import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile

object DolphinCheatParser {

    private val codeLine = Regex(
        "^[0-9A-Fa-f]{8}(\\s+[0-9A-Fa-f]{8})+$"
    )

    private val patchLine = Regex(
        "^[0-9A-Fa-f]{8}(\\s+[0-9A-Fa-f]{8})*$"
    )

    fun parse(
        text: String,
        titleId: String
    ): CheatFile {
        val cheats = mutableListOf<Cheat>()
        val warnings = mutableListOf<String>()

        var section = ""
        var name: String? = null
        var enabled = false
        var body = mutableListOf<String>()

        fun isCheatSection(): Boolean {
            return section == "actionreplay" ||
                section == "gecko" ||
                section == "onframe" ||
                section == "patch"
        }

        fun finish() {
            val n = name ?: return

            val code = body.filter { line ->
                if (
                    section == "onframe" ||
                    section == "patch"
                ) {
                    patchLine.matches(line.trim())
                } else {
                    codeLine.matches(line.trim())
                }
            }

            if (code.isEmpty()) {
                warnings +=
                    "Cheat \"$n\" has no valid Dolphin code lines."
            } else {
                cheats += Cheat(
                    name = n,
                    lines = code,
                    description = section
                        .replaceFirstChar {
                            it.uppercase()
                        },
                    enabled = enabled
                )
            }

            name = null
            enabled = false
            body = mutableListOf()
        }

        text.removePrefix("\uFEFF")
            .split(Regex("\\r\\n|\\n|\\r"))
            .forEachIndexed { index, raw ->
                val line = raw.trim()

                if (line.isEmpty()) {
                    return@forEachIndexed
                }

                if (
                    line.startsWith("[") &&
                    line.endsWith("]")
                ) {
                    finish()

                    section = line
                        .removePrefix("[")
                        .removeSuffix("]")
                        .trim()
                        .lowercase()

                    return@forEachIndexed
                }

                if (!isCheatSection()) {
                    return@forEachIndexed
                }

                val cheatHeader = when {
                    line.startsWith("*$") -> {
                        enabled = true
                        line.removePrefix("*$").trim()
                    }

                    line.startsWith("$") -> {
                        line.removePrefix("$").trim()
                    }

                    else -> null
                }

                if (cheatHeader != null) {
                    finish()

                    name = cheatHeader.ifBlank {
                        "Unnamed ${section.replaceFirstChar { it.uppercase() }} patch"
                    }

                    return@forEachIndexed
                }

                if (name == null) {
                    return@forEachIndexed
                }

                val valid = if (
                    section == "onframe" ||
                    section == "patch"
                ) {
                    patchLine.matches(line)
                } else {
                    codeLine.matches(line)
                }

                if (valid) {
                    body += line
                } else if (
                    !line.startsWith("#") &&
                    !line.startsWith(";")
                ) {
                    warnings +=
                        "Line ${index + 1}: invalid Dolphin code ignored."
                }
            }

        finish()

        return CheatFile(
            titleId = titleId,
            version = null,
            title = null,
            cheats = cheats,
            preamble = emptyList(),
            warnings = warnings
        )
    }

    fun serialize(file: CheatFile): String {
        val sb = StringBuilder()

        sb.append("[ActionReplay]\n\n")

        file.cheats.forEach { cheat ->
            sb.append(
                if (cheat.enabled) "*$" else "$"
            )
            sb.append(cheat.name.trim())
            sb.append('\n')

            cheat.codeLines.forEach { line ->
                sb.append(line.trim())
                sb.append('\n')
            }

            sb.append('\n')
        }

        return sb.toString()
    }
}

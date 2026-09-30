package com.breezybuilds.cheatstation.cheats

import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile

object PnachParser {

    private val HEADER =
        Regex("^\\[\\s*(.+?)\\s*]$")

    private val PATCH =
        Regex("^patch\\s*=\\s*(.+)$", RegexOption.IGNORE_CASE)

    private val DESCRIPTION =
        Regex("^description\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)

    private val AUTHOR =
        Regex("^author\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)

    private val COMMENT =
        Regex("^comment\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)

    private val GAME_TITLE =
        Regex("^gametitle\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)

    fun parse(
        text: String,
        key: String? = null,
        preserveInvalid: Boolean = false
    ): CheatFile {

        val cheats = mutableListOf<Cheat>()
        val preamble = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        var name: String? = null
        var enabled = false
        var body = mutableListOf<String>()
        var description: String? = null
        var author: String? = null

        fun finish() {

            val currentName = name ?: return

            val validPatches = body.filter {
                PATCH.matches(stripInlineComment(it))
            }

            if (validPatches.isNotEmpty()) {

                val cleanName =
                    currentName
                        .removePrefix("Cheats\\")
                        .removePrefix("Cheats/")
                        .trim()

                val fullDescription = buildString {

                    if (!description.isNullOrBlank()) {
                        append(description!!.trim())
                    }

                    if (!author.isNullOrBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("Author: ")
                        append(author!!.trim())
                    }
                }.ifBlank { null }

                cheats += Cheat(
                    cleanName,
                    validPatches,
                    fullDescription,
                    enabled
                )

            } else if (!preserveInvalid) {

                warnings +=
                    "Cheat $currentName had no patch lines."
            }

            name = null
            body = mutableListOf()
            description = null
            author = null
            enabled = false
        }

        text
            .removePrefix("\uFEFF")
            .split(Regex("\\r\\n|\\n|\\r"))
            .forEachIndexed { index, raw ->

                val line =
                    raw
                        .trim()
                        .replace("\u0000", "")

                if (line.isEmpty()) {
                    return@forEachIndexed
                }

                val header =
                    HEADER.matchEntire(line)

                when {

                    header != null -> {

                        finish()

                        name =
                            header.groupValues[1].trim()

                        enabled = false
                    }

                    line.startsWith("//") ||
                    line.startsWith("#") ||
                    line.startsWith(";") -> {

                        if (name == null) {
                            preamble += line
                        }
                    }

                    PATCH.matches(line) -> {

                        if (name == null) {

                            // Legacy/unlabelled PNACH patch.
                            // PCSX2 still supports these.
                            body += line

                            if (cheats.none {
                                    it.name == "Unlabelled Patches"
                                }) {
                                name = "Unlabelled Patches"
                            }

                        } else {

                            body += line
                        }
                    }

                    DESCRIPTION.matches(line) -> {

                        if (name != null) {
                            description =
                                DESCRIPTION
                                    .matchEntire(line)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.trim()
                        } else {
                            preamble += line
                        }
                    }

                    AUTHOR.matches(line) -> {

                        if (name != null) {
                            author =
                                AUTHOR
                                    .matchEntire(line)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.trim()
                        } else {
                            preamble += line
                        }
                    }

                    COMMENT.matches(line) -> {

                        if (name != null) {

                            val comment =
                                COMMENT
                                    .matchEntire(line)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.trim()

                            if (
                                description.isNullOrBlank() &&
                                !comment.isNullOrBlank()
                            ) {
                                description = comment
                            }

                        } else {
                            preamble += line
                        }
                    }

                    GAME_TITLE.matches(line) -> {

                        if (name == null) {
                            preamble += line
                        }
                    }

                    else -> {

                        if (preserveInvalid) {

                            if (name == null) {
                                preamble += line
                            } else {
                                body += line
                            }

                        } else {

                            warnings +=
                                "Line ${index + 1}: unsupported PNACH line skipped."
                        }
                    }
                }
            }

        finish()

        return CheatFile(
            key,
            null,
            null,
            cheats,
            preamble,
            warnings
        )
    }

    private fun stripInlineComment(line: String): String {
        return line
            .substringBefore("//")
            .trim()
    }

    fun serialize(file: CheatFile): String =
        buildString {

            file.preamble.forEach {
                append(it)
                append('\n')
            }

            if (
                file.preamble.isNotEmpty() &&
                file.cheats.isNotEmpty()
            ) {
                append('\n')
            }

            file.cheats.forEachIndexed { index, cheat ->

                if (index > 0) {
                    append('\n')
                }

                append('[')
                append(cheat.name)
                append("]\n")

                cheat.description?.let {
                    append("description=")
                    append(it)
                    append('\n')
                }

                cheat.lines.forEach {
                    append(it)
                    append('\n')
                }
            }
        }
}

package com.breezybuilds.cheatstation.cheats.dolphin

import android.content.Context
import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.storage.DolphinStorage
import com.breezybuilds.cheatstation.storage.StoreException

class DolphinCheatInstaller(
    private val ctx: Context,
    private val storage: DolphinStorage
) {

    fun apply(
        titleId: String,
        cheats: List<Cheat>
    ): Result<Unit> {
        return runCatching {
            val store = storage.gameSettingsStore()
                ?: throw StoreException(
                    "Dolphin data folder is not accessible. " +
                        "Android may be blocking Android/data. " +
                        "Select the Dolphin data folder in Settings."
                )

            val path = "${titleId.uppercase()}.ini"
            var text = store.readText(path) ?: ""

            cheats
                .distinctBy { it.key + "|" + (it.description ?: "ActionReplay") }
                .forEach { cheat ->
                    text = setEnabled(text, cheat, cheat.enabled)
                }

            store.writeText(path, text)
        }
    }

    fun remove(
        titleId: String,
        cheats: List<Cheat>
    ): Result<Unit> {
        return runCatching {
            val store = storage.gameSettingsStore()
                ?: throw StoreException(
                    "Dolphin data folder is not accessible. " +
                        "Android may be blocking Android/data. " +
                        "Select the Dolphin data folder in Settings."
                )

            val path = "${titleId.uppercase()}.ini"
            val existing = store.readText(path)
                ?: return@runCatching

            var text = existing

            cheats
                .distinctBy { it.key + "|" + (it.description ?: "ActionReplay") }
                .forEach { cheat ->
                    text = removeCheat(text, cheat)
                }

            store.writeText(path, text)
        }
    }

    fun installedNames(titleId: String): Set<String> {
        val store = storage.gameSettingsStore()
            ?: return emptySet()

        val text = store.readText(
            "${titleId.uppercase()}.ini"
        ) ?: return emptySet()

        return Regex(
            """^\*?\$([^\r\n]+)\s*$""",
            RegexOption.MULTILINE
        )
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun sectionFor(cheat: Cheat): String {
        return when (
            cheat.description
                ?.trim()
                ?.lowercase()
        ) {
            "gecko" -> "Gecko"
            "onframe" -> "OnFrame"
            "patch" -> "Patch"
            else -> "ActionReplay"
        }
    }

    private fun setEnabled(
        text: String,
        cheat: Cheat,
        enabled: Boolean
    ): String {
        val name = cheat.name.trim()
        val escaped = Regex.escape(name)
        val section = sectionFor(cheat)

        val header = Regex(
            "^\\*?\\$${escaped}\\s*$",
            RegexOption.MULTILINE
        )

        if (header.containsMatchIn(text)) {
            return header.replace(
                text,
                if (enabled) "*$$name" else "$$name"
            )
        }

        if (!enabled) {
            return text
        }

        val block = buildString {
            append("\n")
            append("*$")
            append(name)
            append("\n")

            cheat.codeLines.forEach { line ->
                append(line.trim())
                append("\n")
            }

            append("\n")
        }

        val sectionRegex = Regex(
            "\\[${Regex.escape(section)}\\]",
            RegexOption.IGNORE_CASE
        )

        val match = sectionRegex.find(text)

        return if (match != null) {
            val insertAt = match.range.last + 1

            text.substring(0, insertAt) +
                block +
                text.substring(insertAt)
        } else {
            val prefix =
                if (text.isBlank()) "" else "\n"

            text +
                prefix +
                "[$section]\n" +
                block
        }
    }

    private fun removeCheat(
        text: String,
        cheat: Cheat
    ): String {
        val name = cheat.name.trim()
        val escaped = Regex.escape(name)

        val block = Regex(
            "(?ms)^\\*?\\$${escaped}\\s*\\n.*?(?=^\\*?\\$|^\\s*\\z)"
        )

        return block
            .replace(text, "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trimEnd() + "\n"
    }
}

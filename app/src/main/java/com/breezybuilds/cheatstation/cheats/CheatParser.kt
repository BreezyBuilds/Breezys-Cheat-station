package com.breezybuilds.cheatstation.cheats

import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile
import com.breezybuilds.cheatstation.model.CodeLine
import com.breezybuilds.cheatstation.util.TitleId

/**
 * Parser/writer for Gateway-style cheat text files as used by Citra/Azahar and Luma/Atmosphere-style tools:
 *
 *     [Infinite Health]
 *     0A1B2C3D 00000001
 *     # optional comment
 *
 * A cheat that is switched on in Azahar is written with a leading asterisk: `[*Infinite Health]`.
 * The parser also accepts `*[Name]` and `{Name}` headers. Change [ENABLED_MARK] / [header] to alter the write style.
 */
object CheatParser {
    const val ENABLED_MARK = "*"

    private val HEADER = Regex("^[\\[{]\\s*(\\*?)\\s*(.+?)\\s*[\\]}]$")
    private val STAR_HEADER = Regex("^\\*\\s*[\\[{]\\s*(.+?)\\s*[\\]}]$")
    private val META = Regex("^[#;]+\\s*(title|game|version|region|title\\s?id)\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)

    fun isCodeLine(line: String) = CodeLine.isCode(line)
    fun isComment(line: String) = line.startsWith("#") || line.startsWith(";") || line.startsWith("//")

    fun header(c: Cheat) = "[" + (if (c.enabled) ENABLED_MARK else "") + cleanName(c.name) + "]"
    private fun cleanName(n: String) = n.replace(Regex("[\\r\\n]+"), " ").trim()

    /**
     * @param preserveInvalid keep unrecognised lines (used for the user's existing file so nothing is lost);
     *                        for downloaded files invalid lines are dropped and reported as warnings.
     * Never throws on malformed input.
     */
    fun parse(text: String, titleId: String? = null, preserveInvalid: Boolean = false): CheatFile {
        val warnings = mutableListOf<String>()
        val preamble = mutableListOf<String>()
        val cheats = mutableListOf<Cheat>()
        var title: String? = null
        var version: String? = null
        var metaTitleId: String? = null

        var name: String? = null
        var enabled = false
        var body = mutableListOf<String>()

        fun finish() {
            val n = name ?: return
            val hasCode = body.any { isCodeLine(it) }
            if (!hasCode && !preserveInvalid) {
                warnings += "Cheat \"$n\" has no valid code lines and was skipped."
            } else {
                val desc = body.firstOrNull { isComment(it) }?.trimStart('#', ';', '/', ' ')?.ifBlank { null }
                cheats += Cheat(name = n, lines = body.toList(), description = desc, enabled = enabled)
            }
            name = null; body = mutableListOf(); enabled = false
        }

        val clean = text.removePrefix("\uFEFF")
        clean.split(Regex("\r\n|\n|\r")).forEachIndexed { idx, raw ->
            val line = raw.trim().replace("\u0000", "")
            if (line.isEmpty()) return@forEachIndexed
            val star = STAR_HEADER.matchEntire(line)
            val hdr = if (star == null) HEADER.matchEntire(line) else null
            when {
                star != null -> { finish(); name = star.groupValues[1]; enabled = true }
                hdr != null -> { finish(); name = hdr.groupValues[2]; enabled = hdr.groupValues[1] == ENABLED_MARK }
                isComment(line) -> {
                    if (name == null) {
                        preamble += line
                        META.matchEntire(line)?.let { m ->
                            val v = m.groupValues[2].trim()
                            when (m.groupValues[1].lowercase().replace(" ", "")) {
                                "title", "game" -> if (title == null) title = v
                                "version" -> if (version == null) version = v.removePrefix("v").removePrefix("V").trim()
                                "titleid" -> metaTitleId = TitleId.extract(v)
                            }
                        }
                    } else body += line
                }
                isCodeLine(line) -> {
                    if (name == null) warnings += "Line ${idx + 1}: code found before any [cheat name]; ignored."
                    else body += line
                }
                else -> {
                    warnings += "Line ${idx + 1}: not a valid cheat line (${line.take(30)}); " + if (preserveInvalid) "kept as is." else "ignored."
                    if (preserveInvalid) { if (name == null) preamble += line else body += line }
                }
            }
        }
        finish()

        val seen = HashSet<String>()
        cheats.forEach { if (!seen.add(it.key)) warnings += "Duplicate cheat name \"${it.name}\" in file." }
        return CheatFile(titleId = titleId ?: metaTitleId, version = version, title = title, cheats = cheats, preamble = preamble, warnings = warnings)
    }

    fun serializeCheat(c: Cheat): String = (listOf(header(c)) + c.lines).joinToString("\n")

    /** Produces the file text. Output always ends with a newline; empty files serialise to "". */
    fun serialize(file: CheatFile): String {
        val sb = StringBuilder()
        if (file.preamble.isNotEmpty()) { file.preamble.forEach { sb.append(it).append('\n') }; if (file.cheats.isNotEmpty()) sb.append('\n') }
        file.cheats.forEachIndexed { i, c ->
            if (i > 0) sb.append('\n')
            sb.append(serializeCheat(c)).append('\n')
        }
        return sb.toString()
    }
}

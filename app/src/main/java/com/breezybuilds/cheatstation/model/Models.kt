package com.breezybuilds.cheatstation.model

/** A 3DS title known to the app (installed title, or a CIA/3DS/CXI file, or entered manually). */
data class Game(
    val title: String,
    val titleId: String,
    val region: String? = null,
    val version: String? = null,
    val productCode: String? = null,
    val installed: Boolean = false,
    val source: String = "",
)

/**
 * One cheat entry. [lines] holds the body exactly as it will be written (code lines and comments, in order).
 * Only lines that look like Gateway/Citra codes count as code, see [CheatParser.isCodeLine].
 */
data class Cheat(
    val name: String,
    val lines: List<String>,
    val description: String? = null,
    val enabled: Boolean = false,
    val installed: Boolean = false,
) {
    val codeLines: List<String> get() = lines.filter { CodeLine.isCode(it) }
    /** Human readable code block. */
    val code: String get() = codeLines.joinToString("\n")
    /** Whitespace/case-normalised code used to compare two cheats. */
    val normalizedCode: String get() = codeLines.joinToString("\n") { it.trim().uppercase().replace(Regex("\\s+"), " ") }
    val key: String get() = name.trim().lowercase()
}

/** A Gateway/Citra code line is two or more 8-digit hex words, e.g. "0A1B2C3D 00000001". */
object CodeLine {
    private val CODE_RE = Regex("^[0-9A-Fa-f]{8}(\\s+[0-9A-Fa-f]{8})*$")
    private val PNACH_RE = Regex("^patch\\s*=.*$", RegexOption.IGNORE_CASE)
    fun isCode(line: String) = CODE_RE.matches(line.trim()) || PNACH_RE.matches(line.trim())
}

data class CheatFile(
    val titleId: String?,
    val version: String?,
    val title: String? = null,
    val cheats: List<Cheat>,
    /** Comment lines that appear before the first cheat. Preserved on write. */
    val preamble: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

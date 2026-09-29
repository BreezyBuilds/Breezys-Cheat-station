package com.breezybuilds.cheatstation.cheats

import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile

object PnachParser {
    private val HEADER = Regex("^\\[\\s*(.+?)\\s*]$")
    private val PATCH = Regex("^patch\\s*=.*$", RegexOption.IGNORE_CASE)
    private val DESC = Regex("^description\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)
    fun parse(text: String, key: String? = null, preserveInvalid: Boolean = false): CheatFile {
        val cheats = mutableListOf<Cheat>(); val pre = mutableListOf<String>(); val warnings = mutableListOf<String>()
        var name: String? = null; var enabled = false; var body = mutableListOf<String>(); var desc: String? = null
        fun finish() { name?.let { if (body.any { PATCH.matches(it) }) cheats += Cheat(it.removePrefix("Cheats\\").trim(), body.toList(), desc, enabled) else if (!preserveInvalid) warnings += "Cheat $it had no patch lines." }; name=null; body=mutableListOf(); desc=null; enabled=false }
        text.removePrefix("\uFEFF").split(Regex("\\r\\n|\\n|\\r")).forEachIndexed { i, raw ->
            val line=raw.trim().replace("\u0000", ""); if(line.isEmpty()) return@forEachIndexed
            val h=HEADER.matchEntire(line)
            when {
                h != null -> { finish(); val n=h.groupValues[1]; name=n; enabled=false }
                line.startsWith("//") || line.startsWith("#") || line.startsWith(";") -> if(name==null) pre+=line else body+=line
                PATCH.matches(line) -> if(name==null) warnings += "Line ${i+1}: patch found before a section." else body+=line
                DESC.matches(line) -> if(name!=null) desc=DESC.matchEntire(line)?.groupValues?.get(1)?.trim()
                line.startsWith("gametitle=", true) || line.startsWith("author=", true) -> if(name==null) pre+=line else body+=line
                else -> if(preserveInvalid) { if(name==null) pre+=line else body+=line } else warnings += "Line ${i+1}: unsupported PNACH line skipped."
            }
        }
        finish(); return CheatFile(key, null, null, cheats, pre, warnings)
    }
    fun serialize(file: CheatFile): String = buildString {
        file.preamble.forEach { append(it).append('\n') }
        if (file.preamble.isNotEmpty() && file.cheats.isNotEmpty()) append('\n')
        file.cheats.forEachIndexed { i,c ->
            if(i>0) append('\n')
            append('[').append(c.name).append("]\n")
            c.description?.let { append("description=").append(it).append('\n') }
            c.lines.forEach { append(it).append('\n') }
        }
    }
}

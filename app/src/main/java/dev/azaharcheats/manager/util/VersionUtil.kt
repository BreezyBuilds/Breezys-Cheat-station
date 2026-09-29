package dev.azaharcheats.manager.util

object VersionUtil {
    /** Converts a TMD title version (u16: major<<10 | minor<<4 | micro) to "1.2" style text. */
    fun fromTmd(v: Int): String {
        val major = (v shr 10) and 0x3F
        val minor = (v shr 4) and 0x3F
        val micro = v and 0xF
        return if (micro != 0) "$major.$minor.$micro" else "$major.$minor"
    }

    /** Parses "v1.2", "1.2.0", "1.10" into [1,2] / [1,10]; null when not a version. */
    fun parse(raw: String?): List<Int>? {
        val s = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.trim() ?: return null
        if (!Regex("^\\d+(\\.\\d+)*$").matches(s)) return null
        val parts = s.split('.').map { it.toIntOrNull() ?: return null }.toMutableList()
        while (parts.size > 1 && parts.last() == 0) parts.removeAt(parts.size - 1)
        return parts
    }

    fun compare(a: String?, b: String?): Int? {
        val pa = parse(a) ?: return null
        val pb = parse(b) ?: return null
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** true / false when both are known, null when either is unknown. */
    fun matches(a: String?, b: String?): Boolean? = compare(a, b)?.let { it == 0 }

    /** Returns "1.2" for "v1.2"; null when it is not a version. */
    fun display(raw: String?): String? = parse(raw)?.let { p -> if (p.size == 1) "${p[0]}.0" else p.joinToString(".") }
}

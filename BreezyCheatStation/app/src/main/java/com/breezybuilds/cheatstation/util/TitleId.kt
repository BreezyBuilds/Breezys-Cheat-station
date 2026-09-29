package com.breezybuilds.cheatstation.util

object TitleId {
    private val HEX16 = Regex("^[0-9A-F]{16}$")
    private val FIND16 = Regex("(?<![0-9A-Fa-f])[0-9A-Fa-f]{16}(?![0-9A-Fa-f])")

    /** Returns the upper-case 16 digit Title ID, or null if [raw] is not a valid one. */
    fun normalize(raw: String?): String? {
        val s = raw?.trim()?.removePrefix("0x")?.removePrefix("0X")?.uppercase() ?: return null
        return if (HEX16.matches(s)) s else null
    }

    /** Finds a Title ID inside arbitrary text (e.g. a file name). Only a fallback, never authoritative. */
    fun extract(text: String?): String? = text?.let { FIND16.find(it)?.value?.uppercase() }

    fun fromLong(v: Long): String = String.format("%016X", v)

    fun fromParts(high: String, low: String): String? =
        normalize(high.trim().padStart(8, '0') + low.trim().padStart(8, '0'))

    fun high(id: String) = id.substring(0, 8)
    fun low(id: String) = id.substring(8)

    /** 00040000 = game/application title. */
    fun isApplication(id: String) = id.startsWith("00040000")
}

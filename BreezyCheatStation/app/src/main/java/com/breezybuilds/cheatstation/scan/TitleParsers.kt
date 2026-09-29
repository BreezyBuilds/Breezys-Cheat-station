package com.breezybuilds.cheatstation.scan

import com.breezybuilds.cheatstation.util.TitleId
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Random-access read abstraction so the binary parsers can be unit-tested with plain byte arrays. */
interface ByteSource {
    val size: Long
    /** Returns up to [length] bytes (fewer at end of data) or null when nothing could be read. */
    fun read(offset: Long, length: Int): ByteArray?
}

class ArrayByteSource(private val data: ByteArray) : ByteSource {
    override val size: Long get() = data.size.toLong()
    override fun read(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || offset >= data.size) return null
        val end = minOf(data.size.toLong(), offset + length).toInt()
        return data.copyOfRange(offset.toInt(), end)
    }
}

class ChannelByteSource(private val ch: FileChannel) : ByteSource {
    override val size: Long get() = try { ch.size() } catch (e: Exception) { 0L }
    override fun read(offset: Long, length: Int): ByteArray? = try {
        val buf = ByteBuffer.allocate(length)
        var total = 0
        while (total < length) {
            val n = ch.read(buf, offset + total)
            if (n <= 0) break
            total += n
        }
        if (total <= 0) null else buf.array().copyOf(total)
    } catch (e: Exception) { null }
}

/** What could be learned from a title container. */
data class TitleMeta(
    val titleId: String,
    val productCode: String? = null,
    val name: String? = null,
    val region: String? = null,
    val tmdVersion: Int? = null,
    val format: String = "",
)

/** Readers for NCCH (.cxi/.app), NCSD (.3ds/.cci), CIA and TMD. Never throw; return null when unrecognised. */
object TitleParsers {
    private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF
    private fun le32(b: ByteArray, o: Int): Long =
        ((u8(b, o) or (u8(b, o + 1) shl 8) or (u8(b, o + 2) shl 16) or (u8(b, o + 3) shl 24)).toLong()) and 0xFFFFFFFFL
    private fun le64(b: ByteArray, o: Int): Long = le32(b, o) or (le32(b, o + 4) shl 32)
    private fun be16(b: ByteArray, o: Int): Int = (u8(b, o) shl 8) or u8(b, o + 1)
    private fun be32(b: ByteArray, o: Int): Long =
        ((u8(b, o).toLong() shl 24) or (u8(b, o + 1).toLong() shl 16) or (u8(b, o + 2).toLong() shl 8) or u8(b, o + 3).toLong())
    private fun be64(b: ByteArray, o: Int): Long = (be32(b, o) shl 32) or be32(b, o + 4)
    private fun ascii(b: ByteArray, o: Int, n: Int): String =
        String(b, o, n, Charsets.ISO_8859_1).trim { it == '\u0000' || it == ' ' }
    private fun align64(v: Long) = (v + 63L) and 63L.inv()

    fun parse(src: ByteSource): TitleMeta? = try {
        val magic = src.read(0x100, 4)?.let { String(it, Charsets.ISO_8859_1) }
        when (magic) {
            "NCSD" -> parseNcsd(src)
            "NCCH" -> parseNcch(src, 0L, "CXI")
            else -> parseCia(src)
        }
    } catch (e: Exception) { null }

    fun parseNcsd(src: ByteSource): TitleMeta? {
        val h = src.read(0, 0x200) ?: return null
        if (h.size < 0x130 || String(h, 0x100, 4, Charsets.ISO_8859_1) != "NCSD") return null
        val part0 = le32(h, 0x120) * 0x200L
        if (part0 <= 0) return null
        return parseNcch(src, part0, "3DS")
    }

    fun parseNcch(src: ByteSource, base: Long, format: String = "CXI"): TitleMeta? {
        val h = src.read(base, 0x200) ?: return null
        if (h.size < 0x1A8 || String(h, 0x100, 4, Charsets.ISO_8859_1) != "NCCH") return null
        val id = TitleId.fromLong(le64(h, 0x108))
        val code = ascii(h, 0x150, 16).ifBlank { null }
        val exefsOff = le32(h, 0x1A0) * 0x200L
        val exefsSize = le32(h, 0x1A4)
        val smdh = if (exefsSize > 0) readSmdh(src, base + exefsOff) else null
        return TitleMeta(id, code, smdh?.first, smdh?.second ?: regionFromProductCode(code), null, format)
    }

    /** (english title, region) from the ExeFS icon file, or null (e.g. ExeFS is encrypted). */
    private fun readSmdh(src: ByteSource, exefs: Long): Pair<String?, String?>? {
        val hdr = src.read(exefs, 0x200) ?: return null
        if (hdr.size < 0x100) return null
        for (i in 0 until 10) {
            if (ascii(hdr, i * 16, 8) != "icon") continue
            val off = le32(hdr, i * 16 + 8)
            val d = src.read(exefs + 0x200 + off, 0x2040) ?: return null
            if (d.size < 0x2020 || String(d, 0, 4, Charsets.ISO_8859_1) != "SMDH") return null
            fun title(lang: Int) = String(d, 0x8 + lang * 0x200, 0x80, Charsets.UTF_16LE).trim { it == '\u0000' || it.isWhitespace() }
            val name = title(1).ifBlank { title(0) }.ifBlank { null }?.replace(Regex("\\s*\\n\\s*"), " ")
            return name to regionFromSmdh(le32(d, 0x2018))
        }
        return null
    }

    fun regionFromSmdh(flags: Long): String? {
        if (flags == 0L) return null
        if ((flags and 0x7FL) == 0x7FL || flags == 0xFFFFFFFFL) return "Region free"
        val names = listOf(1L to "Japan", 2L to "USA", 4L to "Europe", 8L to "Australia", 16L to "China", 32L to "Korea", 64L to "Taiwan")
        return names.filter { (flags and it.first) != 0L }.joinToString("/") { it.second }.ifBlank { null }
    }

    /** "CTR-P-ABCE": the last letter of the 4 character game code is the region letter. */
    fun regionFromProductCode(code: String?): String? {
        val m = Regex("^CTR-[A-Z]-([A-Z0-9]{4})$").matchEntire(code?.trim() ?: return null) ?: return null
        return when (m.groupValues[1][3]) {
            'J' -> "Japan"; 'E' -> "USA"; 'K' -> "Korea"; 'C' -> "China"; 'T' -> "Taiwan"; 'U' -> "Australia"
            'P', 'D', 'F', 'I', 'S', 'H', 'R', 'X', 'Y', 'Z', 'V', 'L' -> "Europe"
            'A' -> "Region free"
            else -> null
        }
    }

    fun parseCia(src: ByteSource): TitleMeta? {
        val h = src.read(0, 0x20) ?: return null
        if (h.size < 0x20 || le32(h, 0) != 0x2020L) return null
        val cert = le32(h, 0x08); val ticket = le32(h, 0x0C); val tmd = le32(h, 0x10)
        if (tmd < 0x100 || tmd > 0x100000) return null
        val certOff = align64(le32(h, 0))
        val ticketOff = align64(certOff + cert)
        val tmdOff = align64(ticketOff + ticket)
        val contentOff = align64(tmdOff + tmd)
        val tmdBytes = src.read(tmdOff, 0x400) ?: return null
        val (id, ver) = parseTmd(tmdBytes) ?: return null
        val ncch = parseNcch(src, contentOff, "CIA")
        return TitleMeta(id, ncch?.productCode, ncch?.name, ncch?.region ?: regionFromProductCode(ncch?.productCode), ver, "CIA")
    }

    /** Parses a raw TMD blob. Returns (titleId, versionU16) or null. */
    fun parseTmd(b: ByteArray): Pair<String, Int>? {
        if (b.size < 4) return null
        val sigLen = when (be32(b, 0).toInt()) {
            0x010000, 0x010003 -> 0x200
            0x010001, 0x010004 -> 0x100
            0x010002, 0x010005 -> 0x3C
            else -> return null
        }
        val hdr = align64(4L + sigLen).toInt()
        if (b.size < hdr + 0x9E) return null
        val id = TitleId.fromLong(be64(b, hdr + 0x4C))
        val version = be16(b, hdr + 0x9C)
        return id to version
    }
}

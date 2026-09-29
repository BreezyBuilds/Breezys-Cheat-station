package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.util.AppLog
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Ps2IsoReader(private val ctx: Context, private val doc: DocumentFile) {
    companion object { const val SECTOR = 2048; const val PVD = 16 * SECTOR }
    private data class Entry(val name: String, val lba: Long, val size: Long, val dir: Boolean)
    private var channel: java.nio.channels.FileChannel? = null

    private fun open() = ctx.contentResolver.openFileDescriptor(doc.uri, "r")?.let { pfd -> FileInputStream(pfd.fileDescriptor).channel to pfd }
    private fun read(offset: Long, length: Int): ByteArray? {
        val p = open() ?: return null
        return try {
            val ch = p.first; val b = ByteBuffer.allocate(length); var pos = offset
            while (b.hasRemaining()) { val n = ch.read(b, pos); if (n <= 0) break; pos += n }
            b.flip(); ByteArray(b.remaining()).also { b.get(it) }
        } finally { try { p.second.close() } catch (_: Exception) {} }
    }
    private fun u32le(b: ByteArray, o: Int): Long = ByteBuffer.wrap(b, o, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL

    private fun dirEntries(lba: Long, size: Long): List<Entry> {
        val bytes = read(lba * SECTOR, size.coerceAtMost(4L * 1024 * 1024).toInt()) ?: return emptyList()
        val out = mutableListOf<Entry>(); var p = 0
        while (p + 34 <= bytes.size) {
            val len = bytes[p].toInt() and 0xff
            if (len == 0) { p = ((p / SECTOR) + 1) * SECTOR; continue }
            if (p + len > bytes.size || len < 34) break
            val extent = u32le(bytes, p + 2); val dataLen = u32le(bytes, p + 10); val flags = bytes[p + 25].toInt() and 0xff
            val nameLen = bytes[p + 32].toInt() and 0xff
            if (p + 33 + nameLen > bytes.size) break
            val raw = bytes.copyOfRange(p + 33, p + 33 + nameLen)
            val name = raw.toString(Charsets.US_ASCII).trimEnd('\u0000').trimEnd()
            if (name != "\u0000" && name != "\u0001") out += Entry(name, extent, dataLen, flags and 2 != 0)
            p += len
        }
        return out
    }

    private fun find(path: String): Entry? {
        val root = read(PVD.toLong(), SECTOR) ?: return null
        if (root.size < 157) return null
        val rec = root.copyOfRange(156, 156 + (root[156].toInt() and 0xff))
        val rootLba = u32le(rec, 2); val rootSize = u32le(rec, 10)
        val parts = path.trim('/').split('/').filter { it.isNotBlank() }
        fun walk(lba: Long, size: Long, idx: Int): Entry? {
            val wanted = parts[idx].uppercase()
            val e = dirEntries(lba, size).firstOrNull { it.name.substringBefore(';').uppercase() == wanted }
            if (e == null) return null
            return if (idx == parts.lastIndex) e else if (e.dir) walk(e.lba, e.size, idx + 1) else null
        }
        return if (parts.isEmpty()) null else walk(rootLba, rootSize, 0)
    }

    fun readFile(path: String): ByteArray? = find(path)?.let { read(it.lba * SECTOR, it.size.coerceAtMost(8L * 1024 * 1024).toInt()) }

    fun bootExecutable(): String? {
        val cnf = readFile("SYSTEM.CNF") ?: return null
        val text = cnf.toString(Charsets.US_ASCII)
        val line = text.lineSequence().firstOrNull { it.trimStart().startsWith("BOOT2", true) } ?: return null
        val value = line.substringAfter('=', "").trim().trim('"')
        return value.substringAfterLast('\\').substringAfterLast('/').substringBefore(';').trim().ifBlank { null }
    }

    fun elfCrc(exeName: String): Long? {
        val data = readFile(exeName) ?: return null
        var crc = 0L
        var i = 0
        while (i + 4 <= data.size) {
            val v = (data[i].toLong() and 255) or ((data[i+1].toLong() and 255) shl 8) or ((data[i+2].toLong() and 255) shl 16) or ((data[i+3].toLong() and 255) shl 24)
            crc = (crc xor v) and 0xffffffffL
            i += 4
        }
        return crc
    }
}

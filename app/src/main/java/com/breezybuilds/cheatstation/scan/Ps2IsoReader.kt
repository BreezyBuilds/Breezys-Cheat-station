package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Ps2IsoReader(
    private val ctx: Context,
    private val doc: DocumentFile
) {
    companion object {
        private const val SECTOR = 2048L
        private const val PVD_SECTOR = 16L
        private const val MAX_FILE_READ = 16 * 1024 * 1024
    }

    private data class Entry(
        val name: String,
        val lba: Long,
        val size: Long,
        val directory: Boolean
    )

    private fun read(offset: Long, length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)

        val pfd = try {
            ctx.contentResolver.openFileDescriptor(doc.uri, "r")
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val buffer = ByteBuffer.allocate(length)
                var position = offset

                while (buffer.hasRemaining()) {
                    val n = channel.read(buffer, position)
                    if (n <= 0) break
                    position += n
                }

                buffer.flip()

                if (!buffer.hasRemaining()) {
                    null
                } else {
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun u32le(bytes: ByteArray, offset: Int): Long {
        return ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
            .toLong() and 0xffffffffL
    }

    private fun u32be(bytes: ByteArray, offset: Int): Long {
        return ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int
            .toLong() and 0xffffffffL
    }

    private fun directoryEntries(
        lba: Long,
        size: Long
    ): List<Entry> {
        val length = size
            .coerceAtLeast(SECTOR)
            .coerceAtMost(MAX_FILE_READ.toLong())
            .toInt()

        val bytes = read(lba * SECTOR, length) ?: return emptyList()

        val result = mutableListOf<Entry>()
        var position = 0

        while (position + 34 <= bytes.size) {
            val recordLength = bytes[position].toInt() and 0xff

            if (recordLength == 0) {
                position = ((position / SECTOR.toInt()) + 1) * SECTOR.toInt()
                continue
            }

            if (recordLength < 34 || position + recordLength > bytes.size) {
                break
            }

            val extentLba = u32le(bytes, position + 2)
            val extentSize = u32le(bytes, position + 10)
            val flags = bytes[position + 25].toInt() and 0xff
            val nameLength = bytes[position + 32].toInt() and 0xff

            if (position + 33 + nameLength > bytes.size) {
                break
            }

            val rawName = bytes.copyOfRange(
                position + 33,
                position + 33 + nameLength
            )

            val name = rawName
                .toString(Charsets.US_ASCII)
                .trimEnd('\u0000')
                .trimEnd()

            if (name != "\u0000" && name != "\u0001") {
                result += Entry(
                    name = name,
                    lba = extentLba,
                    size = extentSize,
                    directory = (flags and 2) != 0
                )
            }

            position += recordLength
        }

        return result
    }

    private fun rootDirectory(): Entry? {
        val pvd = read(PVD_SECTOR * SECTOR, SECTOR.toInt())
            ?: return null

        if (pvd.size < 190) return null

        val recordLength = pvd[156].toInt() and 0xff

        if (recordLength < 34 || 156 + recordLength > pvd.size) {
            return null
        }

        val record = pvd.copyOfRange(
            156,
            156 + recordLength
        )

        val lba = u32le(record, 2)
        val size = u32le(record, 10)

        return Entry(
            name = "/",
            lba = lba,
            size = size,
            directory = true
        )
    }

    private fun normaliseName(name: String): String {
        return name
            .substringBefore(';')
            .trimEnd('.')
            .uppercase()
    }

    private fun find(path: String): Entry? {
        val root = rootDirectory() ?: return null

        val parts = path
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }

        if (parts.isEmpty()) return null

        var current = root

        for (part in parts) {
            if (!current.directory) return null

            val wanted = normaliseName(part)

            val match = directoryEntries(
                current.lba,
                current.size
            ).firstOrNull {
                normaliseName(it.name) == wanted
            } ?: return null

            current = match
        }

        return current
    }

    fun readFile(path: String): ByteArray? {
        val entry = find(path) ?: return null

        if (entry.directory) return null

        val length = entry.size
            .coerceAtMost(MAX_FILE_READ.toLong())
            .toInt()

        return read(entry.lba * SECTOR, length)
    }

    fun bootExecutable(): String? {
        val cnf = readFile("SYSTEM.CNF")
            ?: readFile("SYSTEM.CNF;1")
            ?: return null

        val text = cnf.toString(Charsets.US_ASCII)

        val bootLine = text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it.startsWith("BOOT2", ignoreCase = true) &&
                    it.contains('=')
            }
            ?: return null

        val value = bootLine
            .substringAfter('=')
            .trim()
            .trim('"', '\'')

        return value
            .substringAfterLast('\\')
            .substringAfterLast('/')
            .substringBefore(';')
            .trim()
            .ifBlank { null }
    }

    /**
     * PCSX2's game CRC is an 8-character hash of the executable.
     *
     * The PS2 executable is read as 32-bit little-endian words and
     * combined using XOR, producing the 32-bit value used in
     * SERIAL_CRC.pnach filenames.
     */
    fun elfCrc(exeName: String): Long? {
        val data = readFile(exeName) ?: return null
        if (data.isEmpty()) return null

        var crc = 0L
        var offset = 0

        while (offset + 4 <= data.size) {
            val word =
                (data[offset].toLong() and 0xffL) or
                ((data[offset + 1].toLong() and 0xffL) shl 8) or
                ((data[offset + 2].toLong() and 0xffL) shl 16) or
                ((data[offset + 3].toLong() and 0xffL) shl 24)

            crc = (crc xor word) and 0xffffffffL
            offset += 4
        }

        return crc
    }
}

package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DolphinDiscReader(
    private val ctx: Context
) {
    data class DiscInfo(
        val gameId: String,
        val format: String
    )

    private val rawDiscFormats = setOf("iso", "gcm")

    companion object {
        private const val WII_MAGIC = 0x5D1C9EA3L
        private const val GAMECUBE_MAGIC = 0xC2339F3DL
        private const val RVZ_HEADER_OFFSET = 0x58L
        private const val RVZ_HEADER_SIZE = 0x80
        private const val WBFS_HEADER_SIZE = 12
        private const val WBFS_DISC_HEADER_SIZE = 0x100
        private const val WII_SECTOR_SIZE = 0x8000
        private const val MAX_WII_SECTORS_PER_DISC = 286864
    }

    fun read(doc: DocumentFile): DiscInfo? {
        val name = doc.name.orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "iso", "gcm" -> readRawDisc(doc, extension)
            "wbfs" -> readWbfs(doc)
            "rvz" -> readRvz(doc)
            else -> null
        }
    }

    private fun readRawDisc(
        doc: DocumentFile,
        extension: String
    ): DiscInfo? {
        val header = readAt(doc, 0L, 0x60) ?: return null

        if (header.size < 0x20) return null

        val gameId = asciiId(header, 0) ?: return null

        val magic = readU32BE(header, 0x18)

        val validDisc = magic == WII_MAGIC ||
            readU32BE(header, 0x1C) == GAMECUBE_MAGIC

        if (!validDisc) return null

        return DiscInfo(
            gameId = gameId,
            format = extension.uppercase()
        )
    }

    private fun readRvz(doc: DocumentFile): DiscInfo? {
        val header = readAt(
            doc,
            RVZ_HEADER_OFFSET,
            RVZ_HEADER_SIZE
        ) ?: return null

        if (header.size < 0x20) return null

        val gameId = asciiId(header, 0) ?: return null

        val wiiMagic = readU32BE(header, 0x18)
        val gameCubeMagic = readU32BE(header, 0x1C)

        val validDisc = wiiMagic == WII_MAGIC ||
            gameCubeMagic == GAMECUBE_MAGIC

        if (!validDisc) return null

        return DiscInfo(
            gameId = gameId,
            format = "RVZ"
        )
    }

    private fun readWbfs(doc: DocumentFile): DiscInfo? {
        val header = readAt(
            doc,
            0L,
            WBFS_HEADER_SIZE
        ) ?: return null

        if (header.size < WBFS_HEADER_SIZE) return null

        val magic = readU32BE(header, 0)

        if (magic != 0x57424653L) {
            return null
        }

        val hdSectorSizeShift = header[8].toInt() and 0xFF
        val wbfsSectorSizeShift = header[9].toInt() and 0xFF

        if (hdSectorSizeShift !in 9..16) return null
        if (wbfsSectorSizeShift < 15) return null

        val hdSectorSize = 1L shl hdSectorSizeShift

        val nHdSectors = readU32BE(header, 4)

        if (nHdSectors <= 0L) return null

        val wbfsSectorSize = 1L shl wbfsSectorSizeShift

        val nWiiSectors =
            (nHdSectors * hdSectorSize) / WII_SECTOR_SIZE

        if (nWiiSectors <= 0L) return null

        val nWbfsSectorsPerDisc =
            MAX_WII_SECTORS_PER_DISC shr
                (wbfsSectorSizeShift - 15)

        if (nWbfsSectorsPerDisc <= 0) return null

        val discInfoSizeUnaligned =
            WBFS_DISC_HEADER_SIZE.toLong() +
                nWbfsSectorsPerDisc * 2

        val discInfoSize =
            alignUp(
                discInfoSizeUnaligned,
                hdSectorSize
            )

        val maxDiscSlots =
            ((hdSectorSize - WBFS_HEADER_SIZE).toInt())
                .coerceAtMost(4096)

        val discTable = readAt(
            doc,
            WBFS_HEADER_SIZE.toLong(),
            maxDiscSlots
        ) ?: return null

        for (slot in discTable.indices) {
            if ((discTable[slot].toInt() and 0xFF) == 0) {
                continue
            }

            val discInfoOffset =
                hdSectorSize +
                    slot.toLong() * discInfoSize

            val discInfo = readAt(
                doc,
                discInfoOffset,
                WBFS_DISC_HEADER_SIZE
            ) ?: continue

            if (discInfo.size < WBFS_DISC_HEADER_SIZE) {
                continue
            }

            val wiiMagic = readU32BE(
                discInfo,
                0x18
            )

            val gameCubeMagic = readU32BE(
                discInfo,
                0x1C
            )

            if (wiiMagic != WII_MAGIC &&
                gameCubeMagic != GAMECUBE_MAGIC
            ) {
                continue
            }

            val gameId = asciiId(
                discInfo,
                0
            ) ?: continue

            return DiscInfo(
                gameId = gameId,
                format = "WBFS"
            )
        }

        return null
    }

    private fun asciiId(
        data: ByteArray,
        offset: Int
    ): String? {
        if (offset < 0 ||
            offset + 6 > data.size
        ) {
            return null
        }

        val id = data
            .copyOfRange(offset, offset + 6)
            .toString(Charsets.US_ASCII)
            .trim()

        if (!isValidGameId(id)) {
            return null
        }

        return id
    }

    private fun isValidGameId(
        id: String
    ): Boolean {
        if (id.length != 6) {
            return false
        }

        return id.all { char ->
            char in 'A'..'Z' ||
                char in '0'..'9'
        }
    }

    private fun readU32BE(
        data: ByteArray,
        offset: Int
    ): Long {
        if (offset < 0 ||
            offset + 4 > data.size
        ) {
            return -1L
        }

        return ByteBuffer
            .wrap(data, offset, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int
            .toLong() and 0xFFFFFFFFL
    }

    private fun alignUp(
        value: Long,
        alignment: Long
    ): Long {
        if (alignment <= 0L) {
            return value
        }

        val remainder = value % alignment

        return if (remainder == 0L) {
            value
        } else {
            value + alignment - remainder
        }
    }

    private fun readAt(
        doc: DocumentFile,
        offset: Long,
        length: Int
    ): ByteArray? {
        if (offset < 0L || length <= 0) {
            return null
        }

        val pfd = try {
            ctx.contentResolver.openFileDescriptor(
                doc.uri,
                "r"
            )
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            FileInputStream(
                pfd.fileDescriptor
            ).channel.use { channel ->

                val buffer =
                    ByteBuffer.allocate(length)

                var position = offset

                while (buffer.hasRemaining()) {
                    val read = channel.read(
                        buffer,
                        position
                    )

                    if (read <= 0) {
                        break
                    }

                    position += read
                }

                if (buffer.position() < length) {
                    return null
                }

                buffer.flip()

                ByteArray(
                    buffer.remaining()
                ).also {
                    buffer.get(it)
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
}

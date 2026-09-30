package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.storage.Ps2Storage
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.util.AppLog

class Ps2GameScanner(
    private val ctx: Context,
    private val storage: Ps2Storage
) {

    private val exts = setOf(
        "iso",
        "bin",
        "chd",
        "cso",
        "gz",
        "zso",
        "elf"
    )

    fun scan(progress: (String) -> Unit = {}): ScanResult {

        val root = storage.gamesDoc()
            ?: return ScanResult(
                emptyList(),
                emptyList(),
                "No PS2 games folder is selected."
            )

        val out = LinkedHashMap<String, Game>()
        val warnings = mutableListOf<String>()

        fun addGame(
            file: DocumentFile,
            serial: String,
            crc: Long,
            type: String
        ) {
            val crcText = crc
                .toString(16)
                .uppercase()
                .padStart(8, '0')

            val key = "${serial}_${crcText}"

            out[key] = Game(
                title = file.name?.substringBeforeLast('.') ?: "PS2 Game",
                titleId = key,
                region = regionFrom(serial),
                version = crcText,
                installed = false,
                source = type
            )
        }

        fun scanIso(file: DocumentFile) {

            try {

                val reader = Ps2IsoReader(ctx, file)

                val executable = reader.bootExecutable()

                if (executable == null) {
                    warnings +=
                        "${file.name}: SYSTEM.CNF did not contain a valid BOOT executable."

                    return
                }

                val serial = serialFrom(executable)

                if (serial == null) {
                    warnings +=
                        "${file.name}: could not extract a PS2 serial from $executable."

                    return
                }

                val crc = reader.elfCrc(executable)

                if (crc == null) {
                    warnings +=
                        "${file.name}: could not calculate the ELF CRC."

                    return
                }

                addGame(
                    file,
                    serial,
                    crc,
                    "PS2 ISO"
                )

            } catch (e: Exception) {

                AppLog.e(
                    "PS2",
                    "Could not scan ${file.name}",
                    e
                )

                warnings +=
                    "${file.name}: could not be read."
            }
        }

        fun scanElf(file: DocumentFile) {

            try {

                val bytes = ctx.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: ByteArray(0)

                if (bytes.isEmpty()) {
                    warnings +=
                        "${file.name}: ELF file is empty."

                    return
                }

                val executableName =
                    file.name
                        ?.substringBeforeLast('.')
                        ?: ""

                val serial =
                    serialFrom(executableName)
                        ?: serialFrom(
                            bytes
                                .take(1024 * 1024)
                                .toByteArray()
                                .toString(Charsets.US_ASCII)
                        )

                if (serial == null) {
                    warnings +=
                        "${file.name}: could not identify a PS2 serial."

                    return
                }

                val crc = elfCrc(bytes)

                addGame(
                    file,
                    serial,
                    crc,
                    "PS2 ELF"
                )

            } catch (e: Exception) {

                AppLog.e(
                    "PS2",
                    "Could not scan ELF ${file.name}",
                    e
                )

                warnings +=
                    "${file.name}: could not be read."
            }
        }

        fun walk(
            directory: DocumentFile,
            depth: Int
        ) {

            for (file in directory.listFiles()) {

                if (file.isDirectory) {

                    if (depth > 0) {
                        walk(file, depth - 1)
                    }

                    continue
                }

                val extension =
                    file.name
                        .orEmpty()
                        .substringAfterLast(
                            '.',
                            ""
                        )
                        .lowercase()

                if (extension !in exts) {
                    continue
                }

                progress(
                    "Reading ${file.name}…"
                )

                when (extension) {

                    "iso" -> scanIso(file)

                    "elf" -> scanElf(file)

                    else -> {
                        warnings +=
                            "${file.name}: automatic serial/CRC detection currently supports ISO and ELF files."
                    }
                }
            }
        }

        walk(root, 5)

        return ScanResult(
            out.values.sortedBy {
                it.title.lowercase()
            },
            warnings
        )
    }

    private fun serialFrom(
        text: String
    ): String? {

        val match =
            Regex(
                "([A-Z]{4})[_-](\\d{3})[._-](\\d{2})",
                RegexOption.IGNORE_CASE
            ).find(
                text.uppercase()
            )
                ?: return null

        return "${match.groupValues[1]}-${match.groupValues[2]}${match.groupValues[3]}"
    }

    private fun elfCrc(
        data: ByteArray
    ): Long {

        var crc = 0L
        var offset = 0

        while (offset + 4 <= data.size) {

            val word =
                (data[offset].toLong() and 0xffL) or
                ((data[offset + 1].toLong() and 0xffL) shl 8) or
                ((data[offset + 2].toLong() and 0xffL) shl 16) or
                ((data[offset + 3].toLong() and 0xffL) shl 24)

            crc =
                (crc xor word) and 0xffffffffL

            offset += 4
        }

        return crc
    }

    private fun regionFrom(
        serial: String
    ): String =
        when {

            serial.startsWith(
                "SLES",
                true
            ) -> "PAL"

            serial.startsWith(
                "SLUS",
                true
            ) -> "NTSC-U"

            serial.startsWith(
                "SLPS",
                true
            ) ||
            serial.startsWith(
                "SLPM",
                true
            ) -> "NTSC-J"

            else -> "Unknown"
        }
}

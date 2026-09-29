package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.storage.Ps2Storage
import com.breezybuilds.cheatstation.util.AppLog

class Ps2GameScanner(private val ctx: Context, private val storage: Ps2Storage) {
    private val exts = setOf("iso","bin","chd","cso","gz","zso","elf")
    fun scan(progress: (String) -> Unit = {}): ScanResult {
        val root = storage.gamesDoc() ?: return ScanResult(emptyList(), emptyList(), "No PS2 games folder is selected.")
        val out = LinkedHashMap<String, Game>(); val warnings = mutableListOf<String>()
        fun walk(d: DocumentFile, depth: Int) {
            for (f in d.listFiles()) {
                if (f.isDirectory && depth > 0) walk(f, depth - 1)
                else if (!f.isDirectory && f.name.orEmpty().substringAfterLast('.', "").lowercase() in exts) {
                    progress("Reading ${f.name}…")
                    try {
                        if (f.name.orEmpty().lowercase().endsWith(".iso")) {
                            val r = Ps2IsoReader(ctx, f); val exe = r.bootExecutable(); val serial = exe?.let { serialFrom(it) }
                            val crc = exe?.let { r.elfCrc(it) }
                            if (serial != null && crc != null) {
                                val key = "${serial}_${crc.toString(16).uppercase().padStart(8,'0')}"
                                out[key] = Game(f.name?.substringBeforeLast('.') ?: "PS2 Game", key, regionFrom(serial), crc.toString(16).uppercase().padStart(8,'0'), serial, false, "PS2 ISO")
                            } else warnings += "${f.name}: could not identify the PS2 serial/ELF CRC."
                        } else warnings += "${f.name}: automatic serial/CRC detection currently supports ISO images."
                    } catch (e: Exception) { AppLog.e("PS2", "Could not scan ${f.name}", e); warnings += "${f.name}: could not be read." }
                }
            }
        }
        walk(root, 5)
        return ScanResult(out.values.sortedBy { it.title.lowercase() }, warnings)
    }
    private fun serialFrom(exe: String): String? {
        val m = Regex("([A-Z]{4})[_-](\\d{3})[._-](\\d{2})", RegexOption.IGNORE_CASE).find(exe.uppercase()) ?: return null
        return "${m.groupValues[1]}-${m.groupValues[2]}${m.groupValues[3]}"
    }
    private fun regionFrom(serial: String): String = when { serial.startsWith("SLES", true) -> "PAL"; serial.startsWith("SLUS", true) -> "NTSC-U"; serial.startsWith("SLPS", true) || serial.startsWith("SLPM", true) -> "NTSC-J"; else -> "Unknown" }
}

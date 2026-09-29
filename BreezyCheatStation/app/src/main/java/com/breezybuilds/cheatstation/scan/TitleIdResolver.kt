package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.util.AppLog
import com.breezybuilds.cheatstation.util.TitleId
import java.io.FileInputStream

/** Reads Title IDs (and whatever else is available) from documents chosen through the Storage Access Framework. */
class TitleIdResolver(private val ctx: Context) {

    /** Reads metadata from a .cia/.3ds/.cci/.cxi/.app document by parsing its headers (not its file name). */
    fun fromFile(doc: DocumentFile): TitleMeta? = try {
        ctx.contentResolver.openFileDescriptor(doc.uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { fis -> TitleParsers.parse(ChannelByteSource(fis.channel)) }
        }
    } catch (e: Exception) {
        AppLog.w("TitleId", "Could not read ${doc.name}: ${e.javaClass.simpleName}")
        null
    }

    /** Reads an installed-title `.tmd` file: (titleId, version u16). */
    fun fromTmd(doc: DocumentFile): Pair<String, Int>? = try {
        ctx.contentResolver.openInputStream(doc.uri)?.use { ins ->
            val buf = ByteArray(0x400)
            var n = 0
            while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
            TitleParsers.parseTmd(buf.copyOf(n))
        }
    } catch (e: Exception) { null }

    /** Header data wins; the file name is only a last resort (second value = true when it was used). */
    fun resolve(meta: TitleMeta?, fileName: String?): Pair<String?, Boolean> {
        meta?.titleId?.let { return it to false }
        return TitleId.extract(fileName) to true
    }
}

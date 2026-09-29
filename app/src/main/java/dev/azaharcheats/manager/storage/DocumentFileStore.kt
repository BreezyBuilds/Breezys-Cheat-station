package dev.azaharcheats.manager.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile

/** [FileStore] on top of the Storage Access Framework. [base] is a directory picked by the user. */
class DocumentFileStore(private val ctx: Context, private val base: DocumentFile) : FileStore {

    private fun parts(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun walk(segments: List<String>, create: Boolean): DocumentFile? {
        var cur = base
        for (p in segments) {
            val next = cur.findFile(p)
            cur = when {
                next != null && next.isDirectory -> next
                next != null -> throw StoreException("'$p' exists but is not a folder")
                create -> cur.createDirectory(p) ?: throw StoreException("Could not create folder '$p' (is the folder read-only?)")
                else -> return null
            }
        }
        return cur
    }

    private fun find(path: String): DocumentFile? {
        val seg = parts(path)
        if (seg.isEmpty()) return base
        val parent = try { walk(seg.dropLast(1), false) } catch (e: StoreException) { null } ?: return null
        return parent.findFile(seg.last())
    }

    override fun exists(path: String) = try { find(path) != null } catch (e: Exception) { false }

    override fun readText(path: String): String? {
        val doc = find(path) ?: return null
        if (!doc.isFile) return null
        return try {
            ctx.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw StoreException("Could not open '$path' for reading")
        } catch (e: StoreException) { throw e }
        catch (e: Exception) { throw StoreException("Could not read '$path'", e) }
    }

    override fun writeText(path: String, text: String) {
        val seg = parts(path)
        require(seg.isNotEmpty())
        try {
            if (!base.canWrite()) throw StoreException("The selected folder is read-only or permission was revoked")
            val parent = walk(seg.dropLast(1), true) ?: throw StoreException("Folder not available")
            val name = seg.last()
            var doc = parent.findFile(name)
            if (doc == null) {
                doc = parent.createFile("text/plain", name) ?: throw StoreException("Could not create '$name'")
                if (doc.name != name) doc.renameTo(name) // some providers append an extension
            }
            val out = ctx.contentResolver.openOutputStream(doc.uri, "wt")
                ?: throw StoreException("Could not open '$name' for writing")
            out.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
        } catch (e: StoreException) { throw e }
        catch (e: SecurityException) { throw StoreException("Permission denied while writing '$path'", e) }
        catch (e: Exception) { throw StoreException("Could not write '$path'", e) }
    }

    override fun delete(path: String): Boolean = try { find(path)?.delete() ?: false } catch (e: Exception) { false }

    override fun list(dir: String): List<String> = try {
        walk(parts(dir), false)?.listFiles()?.mapNotNull { it.name } ?: emptyList()
    } catch (e: Exception) { emptyList() }
}

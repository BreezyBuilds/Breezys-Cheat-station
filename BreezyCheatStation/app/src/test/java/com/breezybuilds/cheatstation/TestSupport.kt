package com.breezybuilds.cheatstation

import com.breezybuilds.cheatstation.storage.FileStore
import com.breezybuilds.cheatstation.storage.StoreException

/** Simple in-memory [FileStore] for tests. */
open class InMemoryFileStore : FileStore {
    val files = LinkedHashMap<String, String>()
    override fun exists(path: String) = files.containsKey(path)
    override fun readText(path: String): String? = files[path]
    override fun writeText(path: String, text: String) { files[path] = text }
    override fun delete(path: String) = files.remove(path) != null
    override fun list(dir: String): List<String> {
        val prefix = if (dir.isEmpty()) "" else dir.trimEnd('/') + "/"
        return files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct()
    }
}

/** Fails (or silently corrupts) the first write to [target]; later writes work, so restores can succeed. */
class FlakyStore(private val target: String, private val corrupt: Boolean = false) : InMemoryFileStore() {
    private var tripped = false
    override fun writeText(path: String, text: String) {
        if (path == target && !tripped) {
            tripped = true
            if (corrupt) { files[path] = text.take(text.length / 2); return }
            throw StoreException("disk full")
        }
        super.writeText(path, text)
    }
}

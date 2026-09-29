package com.breezybuilds.cheatstation.storage

import java.io.File

/** FileStore for paths that the Android runtime actually allows this app to access. */
class DirectFileStore(private val base: File) : FileStore {
    private fun file(path: String): File = path.split('/').filter { it.isNotEmpty() }.fold(base) { cur, part -> File(cur, part) }
    override fun exists(path: String): Boolean = file(path).exists()
    override fun readText(path: String): String? = file(path).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    override fun writeText(path: String, text: String) {
        val target = file(path)
        target.parentFile?.mkdirs()
        if (!target.parentFile?.isDirectory.orDefault(false)) throw StoreException("Could not create the destination folder")
        try { target.writeText(text, Charsets.UTF_8) } catch (e: Exception) { throw StoreException("Could not write '$path'", e) }
    }
    override fun delete(path: String): Boolean = file(path).delete()
    override fun list(dir: String): List<String> = file(dir).list()?.toList() ?: emptyList()
    private fun Boolean?.orDefault(value: Boolean) = this ?: value
}

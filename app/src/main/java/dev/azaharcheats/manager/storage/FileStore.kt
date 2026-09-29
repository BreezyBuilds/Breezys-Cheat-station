package dev.azaharcheats.manager.storage

class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Minimal file abstraction (relative paths, '/' separated) so logic can be tested without Android. */
interface FileStore {
    fun exists(path: String): Boolean
    /** Returns null when the file does not exist. */
    fun readText(path: String): String?
    /** Creates parent folders and the file if needed. Throws [StoreException] on failure. */
    fun writeText(path: String, text: String)
    fun delete(path: String): Boolean
    /** File and folder names directly inside [dir] ("" = the store root). */
    fun list(dir: String): List<String>
}

package com.breezybuilds.cheatstation.cheats

import com.breezybuilds.cheatstation.storage.FileStore
import com.breezybuilds.cheatstation.storage.StoreException
import com.breezybuilds.cheatstation.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates/restores timestamped copies of a game's cheat file inside `<cheats>/backups/`.
 * Backup name: `<TITLEID>_cheats_backup_YYYYMMDD_HHMMSS.txt`.
 */
class BackupManager(private val store: FileStore, private val now: () -> Date = { Date() }, private val keepPerTitle: Int = 25) {
    companion object {
        const val DIR = "backups"
        private val NAME = Regex("^([0-9A-F]{16})_cheats_backup_(\\d{8}_\\d{6})(_\\d+)?\\.txt$")
        fun titleIdOf(backupName: String): String? = NAME.matchEntire(backupName)?.groupValues?.get(1)
    }

    /** Writes [content] as a new backup, verifies it, and returns its file name. Throws [StoreException] on failure. */
    fun create(titleId: String, content: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now())
        var name = "${titleId}_cheats_backup_$stamp.txt"
        var n = 2
        while (store.exists("$DIR/$name")) name = "${titleId}_cheats_backup_${stamp}_${n++}.txt"
        store.writeText("$DIR/$name", content)
        if (store.readText("$DIR/$name") != content) {
            store.delete("$DIR/$name")
            throw StoreException("Backup could not be verified")
        }
        AppLog.i("Backup", "Created $name")
        prune(titleId)
        return name
    }

    /** Newest first. */
    fun list(titleId: String? = null): List<String> =
        store.list(DIR).filter { NAME.matches(it) && (titleId == null || it.startsWith(titleId)) }.sortedDescending()

    fun read(name: String): String? = if (NAME.matches(name)) store.readText("$DIR/$name") else null

    /** Restores the backup over the live cheat file for its title (the current file is backed up first). Returns the title id. */
    fun restore(name: String): String {
        val tid = titleIdOf(name) ?: throw StoreException("Not a backup file: $name")
        val text = read(name) ?: throw StoreException("Backup $name could not be read")
        // Keep the file we are about to overwrite, so a restore can itself be undone.
        val current = store.readText("$tid.txt")
        if (current != null && current.isNotBlank() && current != text) create(tid, current)
        store.writeText("$tid.txt", text)
        AppLog.i("Backup", "Restored $name")
        return tid
    }

    fun delete(name: String): Boolean = NAME.matches(name) && store.delete("$DIR/$name")

    private fun prune(titleId: String) {
        val all = list(titleId)
        if (all.size > keepPerTitle) all.drop(keepPerTitle).forEach { store.delete("$DIR/$it") }
    }
}

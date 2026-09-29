package com.breezybuilds.cheatstation.cheats

import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile
import com.breezybuilds.cheatstation.storage.FileStore
import com.breezybuilds.cheatstation.storage.StoreException
import com.breezybuilds.cheatstation.util.AppLog

sealed class InstallResult {
    data class Success(val message: String, val backupName: String?, val notes: List<String> = emptyList()) : InstallResult()
    data class Failure(val message: String, val restored: Boolean = false) : InstallResult()
}

/**
 * Safe read -> backup -> modify -> write -> verify -> (restore on failure) pipeline.
 * Only the entries you ask for are changed; everything else in the file is written back as it was.
 */
class CheatInstaller(private val store: FileStore, private val backups: BackupManager) {

    fun path(titleId: String) = "$titleId.txt"

    /** Existing cheats for the game (empty file when none). Malformed lines are preserved. */
    fun readExisting(titleId: String): CheatFile {
        val text = try { store.readText(path(titleId)) } catch (e: StoreException) { throw e }
        return CheatParser.parse(text ?: "", titleId, preserveInvalid = true)
    }

    fun preview(titleId: String, incoming: List<Cheat>): MergeResult =
        CheatMerger.merge(readExisting(titleId), incoming)

    fun install(titleId: String, incoming: List<Cheat>, replaceKeys: Set<String> = emptySet()): InstallResult {
        if (incoming.isEmpty()) return InstallResult.Failure("No cheats selected.")
        val existing = try { readExisting(titleId) } catch (e: StoreException) { return InstallResult.Failure("Could not read the current cheat file: ${e.message}") }
        val merged = CheatMerger.merge(existing, incoming, replaceKeys)
        val summary = buildString {
            append("${merged.added.size} added")
            if (merged.replaced.isNotEmpty()) append(", ${merged.replaced.size} replaced")
            if (merged.identical.isNotEmpty()) append(", ${merged.identical.size} already installed")
            if (merged.conflicts.isNotEmpty()) append(", ${merged.conflicts.size} kept (different code already present)")
        }
        return commit(titleId, CheatParser.serialize(merged.file), summary, merged.notes)
    }

    fun remove(titleId: String, names: Set<String>): InstallResult {
        val existing = try { readExisting(titleId) } catch (e: StoreException) { return InstallResult.Failure("Could not read the current cheat file: ${e.message}") }
        val updated = CheatMerger.remove(existing, names)
        val removed = existing.cheats.size - updated.cheats.size
        if (removed == 0) return InstallResult.Success("Nothing to remove.", null)
        return commit(titleId, CheatParser.serialize(updated), "$removed removed")
    }

    fun setEnabled(titleId: String, name: String, enabled: Boolean): InstallResult {
        val existing = try { readExisting(titleId) } catch (e: StoreException) { return InstallResult.Failure("Could not read the current cheat file: ${e.message}") }
        return commit(titleId, CheatParser.serialize(CheatMerger.setEnabled(existing, name, enabled)), if (enabled) "Enabled" else "Disabled")
    }

    private fun commit(titleId: String, newText: String, summary: String, notes: List<String> = emptyList()): InstallResult {
        val p = path(titleId)
        val old = try { store.readText(p) } catch (e: StoreException) { return InstallResult.Failure("Could not read the current cheat file: ${e.message}") }
        if (old != null && normalize(old) == normalize(newText)) return InstallResult.Success("No changes needed. $summary", null, notes)

        var backup: String? = null
        if (old != null && old.isNotBlank()) {
            backup = try { backups.create(titleId, old) } catch (e: Exception) {
                AppLog.e("Installer", "Backup failed, aborting write", e)
                return InstallResult.Failure("Could not create a backup, so nothing was changed. (${e.message})")
            }
        }
        return try {
            store.writeText(p, newText)
            val back = store.readText(p)
            if (back == null || normalize(back) != normalize(newText)) throw StoreException("Verification failed: the file on disk differs from what was written")
            AppLog.i("Installer", "Wrote $p: $summary")
            InstallResult.Success(summary, backup, notes)
        } catch (e: Exception) {
            AppLog.e("Installer", "Write failed for $p", e)
            val restored = try {
                if (old != null) store.writeText(p, old) else store.delete(p)
                true
            } catch (r: Exception) { AppLog.e("Installer", "Restore failed", r); false }
            InstallResult.Failure(
                "Could not update the cheat file (${e.message}). " + if (restored) "Your previous cheats were restored." else "Restoring failed; a backup is in the backups folder.",
                restored,
            )
        }
    }

    private fun normalize(s: String) = s.replace("\r\n", "\n").trimEnd()
}

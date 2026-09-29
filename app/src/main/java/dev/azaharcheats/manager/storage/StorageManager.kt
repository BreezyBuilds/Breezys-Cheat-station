package dev.azaharcheats.manager.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import dev.azaharcheats.manager.util.AppLog
import java.io.File

/** Handles the Storage Access Framework grants and turns them into [FileStore]s. */
class StorageManager(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("azahar_cm", Context.MODE_PRIVATE)
    private val resolver = ctx.contentResolver

    var rootUri: Uri?
        get() = prefs.getString("root_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit { putString("root_uri", v?.toString()) }
    /** Optional override when the cheats folder is not <root>/cheats. */
    var cheatsUri: Uri?
        get() = prefs.getString("cheats_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit { putString("cheats_uri", v?.toString()) }
    /** Optional folder that holds .cia/.3ds/.cxi files. */
    var gamesUri: Uri?
        get() = prefs.getString("games_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit { putString("games_uri", v?.toString()) }

    enum class Slot { ROOT, CHEATS, GAMES }

    /** Persists the grant and remembers it. Returns null on success or an error message. */
    fun accept(slot: Slot, uri: Uri): String? {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
            AppLog.e("Storage", "Could not persist permission", e)
            return "Android would not keep access to that folder. Please pick it again."
        }
        if (slot == Slot.ROOT) {
            val doc = DocumentFile.fromTreeUri(ctx, uri)
            val v = validateRoot(doc)
            if (!v.first) { return v.second }
        }
        when (slot) { Slot.ROOT -> rootUri = uri; Slot.CHEATS -> cheatsUri = uri; Slot.GAMES -> gamesUri = uri }
        AppLog.i("Storage", "Accepted $slot folder")
        return null
    }

    /** Use a folder even though it does not look like an Azahar folder (grant was already persisted by [accept]). */
    fun forceRoot(uri: Uri) { rootUri = uri }

    fun clear(slot: Slot) {
        val u = when (slot) { Slot.ROOT -> rootUri; Slot.CHEATS -> cheatsUri; Slot.GAMES -> gamesUri }
        try { u?.let { resolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } } catch (_: Exception) {}
        when (slot) { Slot.ROOT -> rootUri = null; Slot.CHEATS -> cheatsUri = null; Slot.GAMES -> gamesUri = null }
    }

    fun hasPermission(uri: Uri?): Boolean = uri != null &&
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }

    fun rootDoc(): DocumentFile? = rootUri?.takeIf { hasPermission(it) }?.let { DocumentFile.fromTreeUri(ctx, it) }?.takeIf { it.exists() }

    /** Folders that make a directory look like an Azahar user directory. */
    private val markers = listOf("sdmc", "nand", "config", "cheats", "sysdata", "log", "states", "shaders")

    /** (valid, message) */
    fun validateRoot(doc: DocumentFile?): Pair<Boolean, String> {
        if (doc == null || !doc.exists() || !doc.isDirectory) return false to "That folder is not available."
        if (doc.name.equals("cheats", true)) return true to "OK"
        val names = doc.listFiles().mapNotNull { it.name?.lowercase() }
        val hits = markers.count { it in names }
        return if (hits >= 1) true to "OK" else false to
            "That doesn't look like an Azahar data folder (no sdmc, nand, config or cheats folder inside). Pick the folder that contains them."
    }

    /**
     * Auto-discovery, limited by what Android permits: (1) reuse an existing persisted grant that looks like Azahar,
     * (2) otherwise return a folder-picker hint pointing at the most likely location.
     */
    fun autoDetectExistingGrant(): Uri? {
        for (p in resolver.persistedUriPermissions) {
            if (!p.isReadPermission || !p.isWritePermission) continue
            val doc = DocumentFile.fromTreeUri(ctx, p.uri) ?: continue
            if (validateRoot(doc).first && doc.listFiles().any { it.name.equals("sdmc", true) || it.name.equals("cheats", true) || it.name.equals("nand", true) }) {
                AppLog.i("Storage", "Reusing an existing folder grant")
                rootUri = p.uri
                return p.uri
            }
        }
        return null
    }

    /** Where to open the system folder picker so the user starts near Azahar's data. */
    fun pickerHint(): Uri? {
        val candidates = listOf("azahar-emu", "Azahar", "azahar", "citra-emu", "Android/data/io.github.azahar_emu.azahar/files")
        val base = File("/storage/emulated/0")
        val found = candidates.firstOrNull { try { File(base, it).exists() } catch (_: Exception) { false } }
        val rel = found ?: candidates.last()
        return try {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$rel")
        } catch (_: Exception) { null }
    }

    fun rootStore(): FileStore? = rootDoc()?.let { DocumentFileStore(ctx, it) }

    /** Store rooted at the cheats folder (created inside a validated Azahar folder when missing). */
    fun cheatsStore(): FileStore? {
        cheatsUri?.takeIf { hasPermission(it) }?.let { u ->
            DocumentFile.fromTreeUri(ctx, u)?.takeIf { it.exists() && it.isDirectory }?.let { return DocumentFileStore(ctx, it) }
        }
        val root = rootDoc() ?: return null
        if (root.name.equals("cheats", true)) return DocumentFileStore(ctx, root)
        val existing = root.findFile("cheats")
        val dir = when {
            existing != null && existing.isDirectory -> existing
            existing != null -> return null
            else -> {
                AppLog.i("Storage", "No cheats folder found, creating one inside the Azahar folder")
                root.createDirectory("cheats")
            }
        } ?: return null
        return DocumentFileStore(ctx, dir)
    }

    fun gamesStore(): FileStore? = gamesUri?.takeIf { hasPermission(it) }
        ?.let { DocumentFile.fromTreeUri(ctx, it) }?.let { DocumentFileStore(ctx, it) }

    fun describe(uri: Uri?): String {
        if (uri == null) return "Not set"
        val doc = DocumentFile.fromTreeUri(ctx, uri)
        val decoded = Uri.decode(uri.lastPathSegment ?: "")
        return (doc?.name ?: decoded).ifBlank { decoded } + if (!hasPermission(uri)) "  (permission lost)" else ""
    }
}

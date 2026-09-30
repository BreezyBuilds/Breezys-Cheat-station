package com.breezybuilds.cheatstation.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.util.AppLog
import com.breezybuilds.cheatstation.emulator.EmulatorDetector
import java.io.File
import com.breezybuilds.cheatstation.storage.DirectFileStore

/** Handles the Storage Access Framework grants and turns them into [FileStore]s. */
class StorageManager(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("breezy_cheat_station_storage", Context.MODE_PRIVATE)
    private val legacyPrefs = ctx.getSharedPreferences("azahar_cm", Context.MODE_PRIVATE)
    private val resolver = ctx.contentResolver

    var rootUri: Uri?
        get() = (prefs.getString("root_uri", null) ?: legacyPrefs.getString("root_uri", null))?.let(Uri::parse)
        private set(v) = prefs.edit { putString("root_uri", v?.toString()) }
    /** Optional override when the cheats folder is not <root>/cheats. */
    var cheatsUri: Uri?
        get() = (prefs.getString("cheats_uri", null) ?: legacyPrefs.getString("cheats_uri", null))?.let(Uri::parse)
        private set(v) = prefs.edit { putString("cheats_uri", v?.toString()) }
    /** Optional folder that holds .cia/.3ds/.cxi files. */
    var gamesUri: Uri?
        get() = (prefs.getString("games_uri", null) ?: legacyPrefs.getString("games_uri", null))?.let(Uri::parse)
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

    /** Use a folder even though it does not match the usual 3DS emulator markers. */
    fun forceRoot(uri: Uri) { rootUri = uri }

    fun clear(slot: Slot) {
        val u = when (slot) { Slot.ROOT -> rootUri; Slot.CHEATS -> cheatsUri; Slot.GAMES -> gamesUri }
        try { u?.let { resolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } } catch (_: Exception) {}
        when (slot) { Slot.ROOT -> rootUri = null; Slot.CHEATS -> cheatsUri = null; Slot.GAMES -> gamesUri = null }
    }

    fun hasPermission(uri: Uri?): Boolean = uri != null &&
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }

    fun rootDoc(): DocumentFile? = autoDetect3dsFolder()?.let { DocumentFile.fromFile(it) } ?: rootUri?.takeIf { hasPermission(it) }?.let { DocumentFile.fromTreeUri(ctx, it) }?.takeIf { it.exists() }

    /** Common markers found in 3DS emulator data/user folders. */
    private val markers = listOf("sdmc", "nand", "config", "cheats", "sysdata", "log", "states", "shaders")

    /** (valid, message) */
    fun validateRoot(doc: DocumentFile?): Pair<Boolean, String> {
        if (doc == null || !doc.exists() || !doc.isDirectory) return false to "That folder is not available."
        if (doc.name.equals("cheats", true)) return true to "OK"
        val names = doc.listFiles().mapNotNull { it.name?.lowercase() }
        val hits = markers.count { it in names }
        return if (hits >= 1) true to "OK" else false to
            "That doesn't look like a 3DS emulator data folder. Pick the emulator's user/data folder (for example one containing sdmc, nand, config, cheats or similar data)."
    }

    /**
     * Auto-discovery, limited by what Android permits: reuse an existing persisted grant that looks like a 3DS emulator data folder,
     * (2) otherwise return a folder-picker hint pointing at the most likely location.
     */
    fun autoDetectExistingGrant(): Uri? {
        rootUri?.takeIf { hasPermission(it) }?.let { uri ->
            val doc = DocumentFile.fromTreeUri(ctx, uri)
            if (doc != null && doc.exists() && doc.isDirectory &&
                (doc.name.equals("Azahar", true) || validateRoot(doc).first)) {
                AppLog.i("Storage", "Reusing saved 3DS emulator folder grant: ${doc.name}")
                return uri
            }
        }

        for (p in resolver.persistedUriPermissions) {
            if (!p.isReadPermission || !p.isWritePermission) continue
            val doc = DocumentFile.fromTreeUri(ctx, p.uri) ?: continue
            if (validateRoot(doc).first && doc.listFiles().any {
                    it.name.equals("sdmc", true) ||
                    it.name.equals("cheats", true) ||
                    it.name.equals("nand", true)
                }) {
                AppLog.i("Storage", "Reusing an existing folder grant")
                rootUri = p.uri
                return p.uri
            }
        }
        return null
    }

    /** Automatically finds a likely 3DS emulator folder when Android exposes it directly. */
    fun autoDetect3dsFolder(): File? {
        val emulator = EmulatorDetector.detect3dsEmulator(ctx)
        val names = when (emulator?.name) {
            "Azahar" -> listOf("Azahar", "azahar-emu", "citra-emu", "Citra")
            "Citra" -> listOf("citra-emu", "Citra", "citra")
            "Lime3DS" -> listOf("lime3ds", "Lime3DS", "citra-emu")
            else -> listOf("citra-emu", "Citra", "Lime3DS", "Azahar", "azahar-emu")
        }
        val base = File("/storage/emulated/0")
        val candidates = names.flatMap { name ->
            listOf(
                File(base, name),
                File(base, "Android/data/$name/files"),
                File(base, "Android/data/$name/files/citra-emu")
            )
        }
        return candidates.firstOrNull { folder ->
            try {
                folder.exists() && folder.isDirectory && folder.listFiles()?.any { child ->
                    child.name.equals("sdmc", true) ||
                    child.name.equals("nand", true) ||
                    child.name.equals("config", true) ||
                    child.name.equals("cheats", true)
                } == true
            } catch (_: Exception) {
                false
            }
        }
    }
    /** Where to open the system folder picker near common 3DS emulator locations. */
    fun pickerHint(): Uri? {
        val emulator = EmulatorDetector.detect3dsEmulator(ctx)
        val rel = if (emulator?.name == "Azahar") {
            "Azahar"
        } else {
            val candidates = listOf("citra-emu", "Citra", "3ds", "emulator", "Android/data")
            val base = File("/storage/emulated/0")
            candidates.firstOrNull { try { File(base, it).exists() } catch (_: Exception) { false } } ?: candidates.last()
        }
        return try {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$rel")
        } catch (_: Exception) { null }
    }

    fun has3dsStorage(): Boolean = autoDetect3dsFolder()?.isDirectory == true || rootDoc() != null

    fun rootStore(): FileStore? = autoDetect3dsFolder()?.let { DirectFileStore(it) } ?: rootDoc()?.let { DocumentFileStore(ctx, it) }

    /** Store rooted at the cheats folder (created inside a validated emulator data folder when missing). */
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
                AppLog.i("Storage", "No cheats folder found, creating one inside the emulator data folder")
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
    fun describe3dsRoot(): String {
        autoDetect3dsFolder()?.let { return it.path }
        return describe(rootUri)
    }

}

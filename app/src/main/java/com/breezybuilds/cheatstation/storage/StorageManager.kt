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

    private fun emulatorRootKey(packageName: String): String =
        "3ds_root_uri_" + packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    var selected3dsEmulatorPackage: String?
        get() = prefs.getString("selected_3ds_emulator_package", null)
        private set(value) = prefs.edit {
            if (value == null) remove("selected_3ds_emulator_package")
            else putString("selected_3ds_emulator_package", value)
        }

    fun setSelected3dsEmulator(packageName: String) {
        selected3dsEmulatorPackage = packageName
    }

    fun emulatorRootUri(packageName: String): Uri? =
        prefs.getString(emulatorRootKey(packageName), null)?.let(Uri::parse)

    fun saveEmulatorRootUri(packageName: String, uri: Uri) {
        prefs.edit {
            putString(emulatorRootKey(packageName), uri.toString())
        }
    }

    fun selected3dsRootUri(): Uri? {
        val packageName = selected3dsEmulatorPackage ?: return null

        emulatorRootUri(packageName)?.let { return it }

        return null
    }

    fun selected3dsRootDoc(): DocumentFile? =
        selected3dsRootUri()
            ?.takeIf { hasPermission(it) }
            ?.let { DocumentFile.fromTreeUri(ctx, it) }
            ?.takeIf { it.exists() && it.isDirectory }

    fun clearEmulatorRoot(packageName: String) {
        prefs.edit {
            remove(emulatorRootKey(packageName))
        }
    }

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

    fun rootDoc(): DocumentFile? =
        selected3dsRootDoc()
            ?: autoDetect3dsFolder()?.let { DocumentFile.fromFile(it) }

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
        val packageName = selected3dsEmulatorPackage ?: return null

        val uri = emulatorRootUri(packageName) ?: return null

        val doc = DocumentFile.fromTreeUri(ctx, uri)
            ?: return null

        if (!doc.exists() || !doc.isDirectory || !hasPermission(uri)) {
            return null
        }

        if (!validateRoot(doc).first) {
            return null
        }

        AppLog.i(
            "Storage",
            "Reusing saved 3DS folder for $packageName: ${doc.name}"
        )

        return uri
    }

    /** Automatically finds a likely 3DS emulator folder when Android exposes it directly. */
    fun autoDetect3dsFolder(): File? {
        val selectedPackage = selected3dsEmulatorPackage ?: return null

        val emulator = EmulatorDetector
            .detect3dsEmulators(ctx)
            .firstOrNull { it.packageName == selectedPackage }
            ?: return null

        val names = when (emulator.name) {
            "Azahar" -> listOf(
                "Azahar",
                "azahar-emu"
            )

            "Citra" -> listOf(
                "citra-emu",
                "Citra",
                "citra"
            )

            "Lime3DS" -> listOf(
                "lime3ds",
                "Lime3DS",
                "citra-emu"
            )

            else -> emptyList()
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
                folder.exists() &&
                    folder.isDirectory &&
                    folder.listFiles()?.any { child ->
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
        val emulator =
            selected3dsEmulatorPackage?.let { packageName ->
                EmulatorDetector
                    .detect3dsEmulators(ctx)
                    .firstOrNull { it.packageName == packageName }
            }
                ?: EmulatorDetector.detect3dsEmulator(ctx)

        val rel = when (emulator?.name) {
            "Azahar" -> "Azahar"

            "Citra" -> {
                val candidates =
                    listOf("citra-emu", "Citra", "citra")

                val base = File("/storage/emulated/0")

                candidates.firstOrNull {
                    try {
                        File(base, it).exists()
                    } catch (_: Exception) {
                        false
                    }
                } ?: candidates.first()
            }

            "Lime3DS" -> {
                val candidates =
                    listOf("Lime3DS", "lime3ds", "citra-emu")

                val base = File("/storage/emulated/0")

                candidates.firstOrNull {
                    try {
                        File(base, it).exists()
                    } catch (_: Exception) {
                        false
                    }
                } ?: candidates.first()
            }

            else -> "Android/data"
        }

        return try {
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$rel"
            )
        } catch (_: Exception) {
            null
        }
    }

    fun has3dsStorage(): Boolean = autoDetect3dsFolder()?.isDirectory == true || rootDoc() != null

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


    fun describe(uri: Uri?): String {
        if (uri == null) return "Not set"
        val doc = DocumentFile.fromTreeUri(ctx, uri)
        val decoded = Uri.decode(uri.lastPathSegment ?: "")
        return (doc?.name ?: decoded).ifBlank { decoded } + if (!hasPermission(uri)) "  (permission lost)" else ""
    }
    fun threeDsStorageState(): StorageState {
        val packageName = selected3dsEmulatorPackage
            ?: return StorageState.NOT_CONFIGURED

        val detected = EmulatorDetector
            .detect3dsEmulators(ctx)
            .any { it.packageName == packageName }

        if (!detected) {
            return StorageState.INVALID
        }

        selected3dsRootUri()?.let { uri ->
            if (!hasPermission(uri)) {
                return StorageState.PERMISSION_REQUIRED
            }

            val doc = DocumentFile.fromTreeUri(ctx, uri)
                ?: return StorageState.INVALID

            if (!doc.exists() || !doc.isDirectory) {
                return StorageState.INVALID
            }

            return StorageState.ACCESSIBLE
        }

        return if (autoDetect3dsFolder() != null) {
            StorageState.PERMISSION_REQUIRED
        } else {
            StorageState.DETECTED
        }
    }

    fun describe3dsRoot(): String {
        if (selected3dsEmulatorPackage == null) {
            return "Not configured"
        }

        autoDetect3dsFolder()?.let { return it.path }

        selected3dsRootUri()?.let {
            return describe(it)
        }

        return "Not configured"
    }

}

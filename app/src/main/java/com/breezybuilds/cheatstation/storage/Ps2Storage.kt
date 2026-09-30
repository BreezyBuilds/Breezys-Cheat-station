package com.breezybuilds.cheatstation.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import java.io.File
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.util.AppLog

class Ps2Storage(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("breezy_ps2_storage", Context.MODE_PRIVATE)
    private val resolver = ctx.contentResolver
    var rootUri: Uri?
        get() = prefs.getString("root_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit { putString("root_uri", v?.toString()) }
    var gamesUri: Uri?
        get() = prefs.getString("games_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit { putString("games_uri", v?.toString()) }
    var transferUri: Uri?
        get() = prefs.getString("transfer_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit { putString("transfer_uri", v?.toString()) }
    var manualPath: String?
        get() = prefs.getString("manual_path", null)
        private set(v) = prefs.edit { putString("manual_path", v) }

    data class EmulatorLocation(val name: String, val packageName: String, val path: String, val accessible: Boolean)

    fun detectEmulator(): EmulatorLocation? {
        val candidates = listOf("xyz.aethersx2.android", "xyz.aethersx2.android.debug", "xyz.aethersx2.android.test", "xyz.aethersx2.tturnip")
        for (pkg in candidates) {
            try {
                val info = ctx.packageManager.getApplicationInfo(pkg, 0)
                val label = ctx.packageManager.getApplicationLabel(info).toString()
                val path = "/storage/emulated/0/Android/data/$pkg/files"
                return EmulatorLocation(label, pkg, path, File(path).isDirectory)
            } catch (_: PackageManager.NameNotFoundException) { }
        }
        return null
    }

    fun saveManualPath(path: String): String? {
        val clean = path.trim().removeSuffix("/")
        if (clean.isBlank() || !clean.startsWith("/")) return "Enter an absolute Android path, for example /storage/emulated/0/Android/data/xyz.aethersx2.android/files"
        manualPath = clean
        return null
    }

    fun clearManualPath() { manualPath = null }

    fun directCheatsStore(): FileStore? {
        val candidates = listOfNotNull(manualPath, detectEmulator()?.path)
        for (candidate in candidates) {
            val base = File(candidate)
            if (!base.isDirectory || !base.canWrite()) continue
            val cheats = if (base.name.equals("cheats", true)) base else File(base, "cheats").apply { if (!exists()) mkdirs() }
            if (cheats.isDirectory && cheats.canWrite()) return DirectFileStore(cheats)
        }
        return null
    }

    fun acceptRoot(uri: Uri): String? = accept(uri, true)
    fun acceptGames(uri: Uri): String? = accept(uri, false)
    fun acceptTransfer(uri: Uri): String? {
        try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (e: Exception) { return "Android would not keep access to that folder. Please pick it again." }
        val doc = DocumentFile.fromTreeUri(ctx, uri)
        if (doc == null || !doc.exists() || !doc.isDirectory || !doc.canWrite()) return "That folder is not writable. Choose a normal shared-storage folder such as Documents/Breezy Cheat Station."
        transferUri = uri
        return null
    }
    private fun accept(uri: Uri, root: Boolean): String? {
        try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (e: Exception) { return "Android would not keep access to that folder. Please pick it again." }
        val doc = DocumentFile.fromTreeUri(ctx, uri)
        if (root && !validateRoot(doc).first) return validateRoot(doc).second
        if (root) rootUri = uri else gamesUri = uri
        return null
    }
    fun hasPermission(uri: Uri?): Boolean = uri != null && resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
    fun rootDoc(): DocumentFile? = rootUri?.takeIf { hasPermission(it) }?.let { DocumentFile.fromTreeUri(ctx, it) }?.takeIf { it.exists() }
    fun gamesDoc(): DocumentFile? = gamesUri?.takeIf { hasPermission(it) }?.let { DocumentFile.fromTreeUri(ctx, it) }?.takeIf { it.exists() }
    fun transferDoc(): DocumentFile? = transferUri?.takeIf { hasPermission(it) }?.let { DocumentFile.fromTreeUri(ctx, it) }?.takeIf { it.exists() && it.canWrite() }
    fun cheatsStore(): FileStore? = cheatsStore(rootDoc()) ?: directCheatsStore()
    fun transferCheatsStore(): FileStore? = cheatsStore(transferDoc())
    private fun cheatsStore(base: DocumentFile?): FileStore? {
        val root = base ?: return null
        val existing = root.findFile("cheats")
        val dir = when { root.name.equals("cheats", true) -> root; existing?.isDirectory == true -> existing; existing != null -> null; else -> root.createDirectory("cheats") }
        return dir?.let { DocumentFileStore(ctx, it) }
    }
    fun installTarget(): String = when {
        rootDoc() != null || directCheatsStore() != null -> "NetherSX2 data folder"
        transferDoc() != null -> "shared transfer folder (import into NetherSX2)"
        else -> "not configured"
    }
    fun describe(uri: Uri?): String {
        if (uri == null) return "Not set"
        val doc = DocumentFile.fromTreeUri(ctx, uri)
        return (doc?.name ?: Uri.decode(uri.lastPathSegment ?: "")).ifBlank { "Selected folder" } + if (!hasPermission(uri)) " (permission lost)" else ""
    }
    fun validateRoot(doc: DocumentFile?): Pair<Boolean,String> {
        if (doc == null || !doc.exists() || !doc.isDirectory) return false to "That folder is not available."
        if (doc.name.equals("cheats", true)) return true to "OK"
        val names = doc.listFiles().mapNotNull { it.name?.lowercase() }.toSet()
        val hits = listOf("cheats","bios","sstates","memcards","logs","covers","gamesettings").count { it in names }
        return if (hits >= 1) true to "OK" else false to "That doesn't look like a PS2 emulator data folder. Pick the NetherSX2/PCSX2 user folder or a folder containing cheats, bios, memcards or similar emulator data."
    }
}

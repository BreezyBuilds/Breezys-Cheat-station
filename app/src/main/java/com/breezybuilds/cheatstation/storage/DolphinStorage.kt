package com.breezybuilds.cheatstation.storage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.io.File

class DolphinStorage(private val ctx: Context) {

    private val prefs =
        ctx.getSharedPreferences("breezy_dolphin_storage", Context.MODE_PRIVATE)

    private val resolver = ctx.contentResolver

    var dolphinUserUri: Uri?
        get() = prefs.getString("dolphin_user_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit {
            if (v == null) remove("dolphin_user_uri")
            else putString("dolphin_user_uri", v.toString())
        }

    var wiiGamesUri: Uri?
        get() = prefs.getString("wii_games_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit {
            if (v == null) remove("wii_games_uri")
            else putString("wii_games_uri", v.toString())
        }

    var gameCubeGamesUri: Uri?
        get() = prefs.getString("gamecube_games_uri", null)?.let(Uri::parse)
        private set(v) = prefs.edit {
            if (v == null) remove("gamecube_games_uri")
            else putString("gamecube_games_uri", v.toString())
        }

    data class DolphinLocation(
        val name: String,
        val packageName: String,
        val path: String,
        val accessible: Boolean
    )

    fun detectDolphin(): DolphinLocation? {
        val candidates = listOf(
            "org.dolphinemu.dolphinemu",
            "org.dolphinemu.dolphinemu.debug"
        )

        for (pkg in candidates) {
            try {
                val info = ctx.packageManager.getApplicationInfo(pkg, 0)
                val label =
                    ctx.packageManager.getApplicationLabel(info).toString()

                val path =
                    "/storage/emulated/0/Android/data/$pkg/files"

                return DolphinLocation(
                    name = label,
                    packageName = pkg,
                    path = path,
                    accessible = File(path).isDirectory &&
                        File(path).canWrite()
                )
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        return null
    }

    fun directGameSettingsStore(): FileStore? {
        val detected = detectDolphin() ?: return null
        if (!detected.accessible) return null

        val base = File(detected.path)

        val gameSettings =
            if (base.name.equals("GameSettings", true)) {
                base
            } else {
                File(base, "GameSettings").apply {
                    if (!exists()) mkdirs()
                }
            }

        if (!gameSettings.isDirectory || !gameSettings.canWrite()) {
            return null
        }

        return DirectFileStore(gameSettings)
    }

    fun acceptDolphinUser(uri: Uri): String? =
        accept(uri) { dolphinUserUri = uri }

    fun acceptWiiGames(uri: Uri): String? =
        accept(uri) { wiiGamesUri = uri }

    fun acceptGameCubeGames(uri: Uri): String? =
        accept(uri) { gameCubeGamesUri = uri }

    private fun accept(uri: Uri, save: () -> Unit): String? {
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            return "Android would not keep access to that folder. Please pick it again."
        }

        val doc = DocumentFile.fromTreeUri(ctx, uri)

        if (
            doc == null ||
            !doc.exists() ||
            !doc.isDirectory ||
            !doc.canWrite()
        ) {
            return "That folder is not writable."
        }

        save()
        return null
    }

    fun hasPermission(uri: Uri?): Boolean =
        uri != null &&
            resolver.persistedUriPermissions.any {
                it.uri == uri &&
                    it.isReadPermission &&
                    it.isWritePermission
            }

    fun dolphinUserDoc(): DocumentFile? =
        dolphinUserUri
            ?.takeIf { hasPermission(it) }
            ?.let { DocumentFile.fromTreeUri(ctx, it) }
            ?.takeIf { it.exists() && it.isDirectory }

    fun wiiGamesDoc(): DocumentFile? =
        wiiGamesUri
            ?.takeIf { hasPermission(it) }
            ?.let { DocumentFile.fromTreeUri(ctx, it) }
            ?.takeIf { it.exists() && it.isDirectory }

    fun gameCubeGamesDoc(): DocumentFile? =
        gameCubeGamesUri
            ?.takeIf { hasPermission(it) }
            ?.let { DocumentFile.fromTreeUri(ctx, it) }
            ?.takeIf { it.exists() && it.isDirectory }

    fun gameSettingsStore(): FileStore? {
        val direct = directGameSettingsStore()
        if (direct != null) return direct

        val root = dolphinUserDoc() ?: return null

        val existing = root.findFile("GameSettings")

        val dir = when {
            root.name.equals("GameSettings", true) -> root
            existing?.isDirectory == true -> existing
            existing != null -> null
            else -> root.createDirectory("GameSettings")
        }

        return dir?.let {
            DocumentFileStore(ctx, it)
        }
    }

    fun clearDolphinUser() {
        dolphinUserUri = null
    }

    fun clearWiiGames() {
        wiiGamesUri = null
    }

    fun clearGameCubeGames() {
        gameCubeGamesUri = null
    }

    fun describe(uri: Uri?): String {
        if (uri == null) return "Not set"

        val doc = DocumentFile.fromTreeUri(ctx, uri)

        val name =
            doc?.name
                ?: Uri.decode(uri.lastPathSegment ?: "")
                .ifBlank { "Selected folder" }

        return name +
            if (!hasPermission(uri)) {
                " (permission lost)"
            } else {
                ""
            }
    }

    fun installTarget(): String = when {
        directGameSettingsStore() != null ->
            "Dolphin data folder (automatic access)"

        dolphinUserDoc() != null ->
            "Dolphin data folder (SAF)"

        else ->
            "not configured"
    }

    fun isConfigured(): Boolean =
        directGameSettingsStore() != null ||
            dolphinUserDoc() != null ||
            wiiGamesDoc() != null ||
            gameCubeGamesDoc() != null
}

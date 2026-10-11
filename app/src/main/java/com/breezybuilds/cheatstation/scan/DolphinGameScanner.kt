package com.breezybuilds.cheatstation.scan

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.storage.DolphinStorage
import com.breezybuilds.cheatstation.util.AppLog

enum class DolphinPlatform {
    WII,
    GAMECUBE
}

class DolphinGameScanner(
    private val ctx: Context,
    private val storage: DolphinStorage
) {
    private val gameExtensions = setOf(
        "iso",
        "rvz",
        "wbfs",
        "gcm",
        "gcz",
        "wia",
        "ciso"
    )

    private val discReader = DolphinDiscReader(ctx)

    fun scan(
        platform: DolphinPlatform,
        progress: (String) -> Unit = {}
    ): ScanResult {
        val root = when (platform) {
            DolphinPlatform.WII -> storage.wiiGamesDoc() ?: com.breezybuilds.cheatstation.emulator.UniversalRoms.systemFolder(ctx, "Wii")
            DolphinPlatform.GAMECUBE -> storage.gameCubeGamesDoc() ?: com.breezybuilds.cheatstation.emulator.UniversalRoms.systemFolder(ctx, "GameCube")
        } ?: return ScanResult(
            games = emptyList(),
            warnings = emptyList(),
            error = when (platform) {
                DolphinPlatform.WII ->
                    "Wii games folder is not configured. Open Settings to select it."

                DolphinPlatform.GAMECUBE ->
                    "GameCube games folder is not configured. Open Settings to select it."
            }
        )

        val warnings = mutableListOf<String>()
        val games = LinkedHashMap<String, Game>()

        progress(
            if (platform == DolphinPlatform.WII)
                "Scanning Wii games…"
            else
                "Scanning GameCube games…"
        )

        // Onboarding already enumerated these SAF URIs. Reuse that manifest instead of
        // traversing the same large folder tree a second time when no override is set.
        val hasOverride = when (platform) {
            DolphinPlatform.WII -> storage.wiiGamesUri != null
            DolphinPlatform.GAMECUBE -> storage.gameCubeGamesUri != null
        }
        val discovered = if (!hasOverride) com.breezybuilds.cheatstation.emulator.UniversalRoms.loadDiscovered(ctx,
            if (platform == DolphinPlatform.WII) "Wii" else "GameCube") else emptyList()
        if (discovered.isNotEmpty()) {
            progress("Identifying ${discovered.size} discovered game files…")
            discovered.forEachIndexed { index, entry ->
                val file = DocumentFile.fromSingleUri(ctx, android.net.Uri.parse(entry.uri))
                if (file != null) collect(file, 0, games, warnings, progress)
                if ((index + 1) % 5 == 0) progress("Identifying ${index + 1} of ${discovered.size} game files…")
            }
        } else {
            collect(root = root, depth = 0, games = games, warnings = warnings, progress = progress)
        }

        AppLog.i(
            "DolphinScan",
            "Found ${games.size} ${platform.name} game(s)"
        )

        return ScanResult(
            games = games.values.sortedBy {
                it.title.lowercase()
            },
            warnings = warnings
        )
    }

    private fun collect(
        root: DocumentFile,
        depth: Int,
        games: MutableMap<String, Game>,
        warnings: MutableList<String>,
        progress: (String) -> Unit
    ) {
        for (file in if (root.isFile) arrayOf(root) else root.listFiles()) {

            if (file.isDirectory) {
                if (depth < 4) {
                    collect(
                        root = file,
                        depth = depth + 1,
                        games = games,
                        warnings = warnings,
                        progress = progress
                    )
                }
                continue
            }

            val name = file.name.orEmpty()

            val extension = name
                .substringAfterLast('.', "")
                .lowercase()

            if (extension !in gameExtensions) {
                continue
            }

            progress("Reading $name")

            val discInfo = discReader.read(file)

            if (discInfo == null) {
                warnings +=
                    "$name: Game ID could not be read (.$extension)"
                continue
            }

            val gameId = discInfo.gameId

            progress("$name → $gameId")

            games[gameId] = Game(
                title = name.substringBeforeLast('.')
                    .ifBlank { gameId },
                titleId = gameId,
                version = null,
                productCode = gameId,
                installed = false,
                source = "Dolphin: $name"
            )
        }
    }
}

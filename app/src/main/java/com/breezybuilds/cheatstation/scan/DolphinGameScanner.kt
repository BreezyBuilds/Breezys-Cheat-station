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
            DolphinPlatform.WII -> storage.wiiGamesDoc()
            DolphinPlatform.GAMECUBE -> storage.gameCubeGamesDoc()
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

        collect(
            root = root,
            depth = 0,
            games = games,
            warnings = warnings,
            progress = progress
        )

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
        for (file in root.listFiles()) {

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

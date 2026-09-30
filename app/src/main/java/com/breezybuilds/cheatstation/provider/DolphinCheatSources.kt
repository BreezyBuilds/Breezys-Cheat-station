package com.breezybuilds.cheatstation.provider

data class DolphinCheatSource(
    val name: String,
    val description: String,
    val owner: String,
    val repo: String,
    val branch: String = "master",
    val path: String = "Data/Sys/GameSettings",
    val custom: Boolean = false
) {
    val id: String
        get() = "$owner/$repo@$branch/$path"

    fun rawUrl(file: String): String {
        val cleanPath = path.trim('/')
        val cleanFile = file.trimStart('/')

        return if (cleanPath.isEmpty() || cleanFile.startsWith("$cleanPath/")) {
            "https://raw.githubusercontent.com/$owner/$repo/$branch/$cleanFile"
        } else {
            "https://raw.githubusercontent.com/$owner/$repo/$branch/$cleanPath/$cleanFile"
        }
    }

    fun treeUrl(): String =
        "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
}

object DolphinCheatSources {

    val builtIn = listOf(
        DolphinCheatSource(
            name = "Dolphin Official",
            description = "Official Wii and GameCube GameSettings database.",
            owner = "dolphin-emu",
            repo = "dolphin"
        ),
        DolphinCheatSource(
            name = "Admentus Enhancement Codes",
            description = "Community Wii/GameCube enhancement and cheat collection.",
            owner = "Admentus64",
            repo = "Enhancement-Codes",
            path = ""
        )
    )

    val all: List<DolphinCheatSource>
        get() = builtIn

    val default: DolphinCheatSource
        get() = builtIn.first()
}

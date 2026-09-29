package com.breezybuilds.cheatstation.provider

/**
 * Where cheats are downloaded from. Everything about the GitHub source lives here so it can be changed
 * in Settings (or by editing DEFAULT) without touching any other code.
 *
 * A cheat file for a game is expected at:
 *   https://raw.githubusercontent.com/<owner>/<repo>/<branch>/<basePath>/<fileNamePattern with {TITLEID}>
 */
data class CheatSourceConfig(
    val owner: String,
    val repo: String,
    val branch: String,
    val basePath: String,
    val fileNamePattern: String,
    /** Optional GitHub token (raises API rate limits). Sent only to api.github.com, never logged. */
    val token: String? = null,
) {
    val id: String get() = "$owner/$repo@$branch/${basePath.trim('/')}"
    val displayName: String get() = "GitHub: $owner/$repo"

    fun fileName(titleId: String) = fileNamePattern.replace("{TITLEID}", titleId)
    fun rawUrl(titleId: String): String {
        val p = basePath.trim('/')
        return "https://raw.githubusercontent.com/$owner/$repo/$branch/" + (if (p.isEmpty()) "" else "$p/") + fileName(titleId)
    }
    fun treeUrl() = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"

    /** Regex that recognises this source's file names and captures the Title ID. */
    fun fileNameRegex(): Regex {
        val parts = fileNamePattern.split("{TITLEID}")
        return Regex("^" + parts.joinToString("([0-9A-Fa-f]{16})") { Regex.escape(it) } + "$")
    }

    /** null when valid, otherwise a message for the user. */
    fun validate(): String? {
        val name = Regex("^[A-Za-z0-9_.-]+$")
        if (!name.matches(owner) || !name.matches(repo)) return "Cheat source owner/repository name is not valid."
        if (!Regex("^[A-Za-z0-9_./-]+$").matches(branch) || branch.contains("..")) return "Cheat source branch name is not valid."
        if (basePath.contains("..") || !Regex("^[A-Za-z0-9_./ -]*$").matches(basePath)) return "Cheat source folder is not valid."
        if (!fileNamePattern.contains("{TITLEID}") || fileNamePattern.contains('/')) return "File name pattern must contain {TITLEID} and no slashes."
        return null
    }

    companion object {
        /**
         * Default public source: a community repository of Gateway/CTRPF-style cheat files, one "<TITLEID>.txt"
         * per game inside "cheats/". Change it in Settings > Cheat source if this repository moves or you prefer another.
         */
        val DEFAULT = CheatSourceConfig(
            owner = "jvhellraiser",
            repo = "CTRPF-AR-CHEAT-CODES",
            branch = "HEAD",
            basePath = "cheats",
            fileNamePattern = "{TITLEID}.txt",
        )
    }
}

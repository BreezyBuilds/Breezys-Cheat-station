package dev.azaharcheats.manager.cheats

import dev.azaharcheats.manager.util.VersionUtil

/** Whether a downloaded cheat file is known to fit the installed game version. */
data class Compatibility(val status: Status, val message: String?) {
    enum class Status { MATCH, MISMATCH, UNVERIFIED }

    companion object {
        fun check(gameVersion: String?, cheatVersion: String?): Compatibility {
            val g = VersionUtil.parse(gameVersion)
            val c = VersionUtil.parse(cheatVersion)
            return when {
                g == null -> Compatibility(Status.UNVERIFIED, "Game version could not be verified. Available cheats may not be compatible.")
                c == null -> Compatibility(Status.UNVERIFIED, "This cheat file does not say which game version it is for. Available cheats may not be compatible.")
                VersionUtil.matches(gameVersion, cheatVersion) == true -> Compatibility(Status.MATCH, null)
                else -> Compatibility(Status.MISMATCH, "These cheats are for version ${VersionUtil.display(cheatVersion)}, but your game is version ${VersionUtil.display(gameVersion)}. They may not work.")
            }
        }
    }
}

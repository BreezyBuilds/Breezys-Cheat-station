package com.breezybuilds.cheatstation.provider

/** Built-in GitHub sources that users can switch between without typing repository details. */
data class RecommendedCheatSource(
    val name: String,
    val description: String,
    val config: CheatSourceConfig,
)

object RecommendedCheatSources {
    val all = listOf(
        RecommendedCheatSource(
            "FlagBrew Sharkive",
            "Community Gateshark database with a dedicated 3DS folder.",
            CheatSourceConfig("FlagBrew", "Sharkive", "master", "3ds", "{TITLEID}.txt"),
        ),
        RecommendedCheatSource(
            "Sharkive community mirror",
            "A public Sharkive fork with the same 3DS Title-ID file layout.",
            CheatSourceConfig("darkenedlotus", "116a00", "master", "3ds", "{TITLEID}.txt"),
        ),
        RecommendedCheatSource(
            "Sharkive community fork",
            "Another public Sharkive fork that keeps the 3DS database under 3ds/.",
            CheatSourceConfig("Sariohara", "Sharkive-fork-0098", "master", "3ds", "{TITLEID}.txt"),
        ),
    )
}

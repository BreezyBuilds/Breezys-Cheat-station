package com.breezybuilds.cheatstation.cheats

data class Ps2CheatSource(
    val name: String,
    val description: String,
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val path: String = "cheats"
) {
    val id: String
        get() = "$owner/$repo@$branch/$path"

    fun rawUrl(filename: String): String =
        "https://raw.githubusercontent.com/$owner/$repo/$branch/${path.trim('/')}/$filename"
}

object Ps2CheatSources {

    val all = listOf(

        Ps2CheatSource(
            name = "PCSX2 Community Cheats",
            description = "Gameplay cheats in PCSX2 PNACH format.",
            owner = "xs1l3n7x",
            repo = "pcsx2_cheats_collection",
            branch = "main",
            path = "cheats"
        ),

        Ps2CheatSource(
            name = "PCSX2 Cheats & Patches",
            description = "Community gameplay cheats, 60 FPS, widescreen and other PS2 patches.",
            owner = "heavyjam-code",
            repo = "pcsx2-cheats-n-patches",
            branch = "main",
            path = "cheats"
        ),

        Ps2CheatSource(
            name = "PCSX2 Official Patches",
            description = "Official PCSX2 patches including widescreen and quality-of-life patches.",
            owner = "PCSX2",
            repo = "pcsx2_patches",
            branch = "main",
            path = "patches"
        )
    )

    val default: Ps2CheatSource
        get() = all.first()
}

package com.breezybuilds.cheatstation.provider

data class Ps2CheatSource(val name: String, val description: String, val owner: String, val repo: String, val branch: String = "main", val path: String = "cheats") {
    val id: String get() = "$owner/$repo@$branch/$path"
    fun rawUrl(key: String): String = "https://raw.githubusercontent.com/$owner/$repo/$branch/${path.trim('/')}/$key.pnach"
}

object Ps2CheatSources {
    val all = listOf(
        Ps2CheatSource("PCSX2 Cheats & Patches", "Current community PS2 cheats in PCSX2/NetherSX2 PNACH format.", "heavyjam-code", "pcsx2-cheats-n-patches"),
        Ps2CheatSource("Official PCSX2 Patches", "Official PCSX2 PNACH patches including widescreen, 60 FPS and game fixes.", "PCSX2", "pcsx2_patches", path = "patches"),
        Ps2CheatSource("PCSX2 Cheats Collection", "Large community collection of serial/CRC matched PNACH cheats.", "xs1l3n7x", "pcsx2_cheats_collection")
    )
    val default = all.first()
}

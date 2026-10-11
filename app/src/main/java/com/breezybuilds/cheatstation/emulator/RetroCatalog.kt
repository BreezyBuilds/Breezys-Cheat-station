package com.breezybuilds.cheatstation.emulator

import android.content.Context

/** One detection path shared by onboarding and the emulator settings page. */
object RetroCatalog {
    data class Entry(val id: String, val title: String, val systems: String, val packages: List<String>, val releases: String, val note: String)
    val entries = listOf(
        Entry("azahar", "3DS emulator", "Nintendo 3DS", listOf("org.azahar_emu.android", "org.azahar_emu.azahar", "org.citra.citra_emu", "org.citra.citra", "io.github.lime3ds.android", "org.lime3ds.android"), "https://github.com/azahar-emu/azahar/releases", "Azahar is recommended. Citra and Lime3DS are also recognised where installed."),
        Entry("nethersx2", "NetherSX2 / AetherSX2", "PlayStation 2", listOf("xyz.aethersx2.android", "xyz.aethersx2.android.debug", "xyz.aethersx2.android.test"), "https://github.com/Trixarian/NetherSX2-patch", "NetherSX2 is a patching project; its releases are not necessarily installable APKs."),
        Entry("dolphin", "Dolphin", "Wii and GameCube", listOf("org.dolphinemu.dolphinemu"), "https://dolphin-emu.org/download/", "One Dolphin installation supports both Wii and GameCube.")
    )
    fun detectedName(ctx: Context, e: Entry): String? = when (e.id) {
        "azahar" -> EmulatorDetector.detect3dsEmulator(ctx)?.name
        else -> e.packages.firstOrNull { EmulatorDetector.packageInstalled(ctx, it) }?.let { e.title }
    }
    fun installed(ctx: Context, e: Entry) = detectedName(ctx, e) != null
    fun selectedEmulators(systems: Set<String>) = entries.filter { e -> when(e.id) {
        "azahar" -> "3DS" in systems
        "nethersx2" -> "PS2" in systems
        else -> "Wii" in systems || "GameCube" in systems
    } }
}

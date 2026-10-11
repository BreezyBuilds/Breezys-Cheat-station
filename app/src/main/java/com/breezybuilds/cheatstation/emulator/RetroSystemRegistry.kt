package com.breezybuilds.cheatstation.emulator

/** Single registry for system selection and future feature expansion.
 * Only supported systems are enabled; future entries should be added once their
 * game scanners and cheat integration are implemented.
 */
object RetroSystemRegistry {
    data class System(
        val id: String,
        val title: String,
        val symbol: String,
        val emulatorId: String,
        val gameExtensions: Set<String>,
        val manufacturer: String
    )

    val available = listOf(
        System("3DS", "Nintendo 3DS", "◈", "azahar", setOf("3ds", "cia", "cxi"), "Nintendo"),
        System("PS2", "PlayStation 2", "◉", "nethersx2", setOf("iso", "bin", "chd", "cso", "gz", "zso", "elf"), "Sony"),
        System("Wii", "Nintendo Wii", "▣", "dolphin", setOf("wbfs", "iso", "rvz", "wia", "gcz"), "Nintendo"),
        System("GameCube", "GameCube", "◆", "dolphin", setOf("iso", "gcm", "rvz", "gcz", "ciso"), "Nintendo")
    )

    val manufacturers = listOf("Nintendo", "Sony", "Sega", "Microsoft", "Other")

    fun emulatorIds(systems: Set<String>): Set<String> =
        available.filter { it.id in systems }.map { it.emulatorId }.toSet()
}

package com.breezybuilds.cheatstation.emulator

import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.scan.ScanResult

enum class EmulatorSystem {
    THREE_DS,
    PS2,
    WII,
    GAMECUBE
}

interface EmulatorAdapter {

    val id: String

    val displayName: String

    val supportedSystems: Set<EmulatorSystem>

    /**
     * Whether this emulator appears to be installed/configured.
     */
    fun isInstalled(): Boolean

    /**
     * Scan games available to this emulator.
     */
    fun scan(
        system: EmulatorSystem,
        progress: (String) -> Unit = {}
    ): ScanResult

    /**
     * Whether the adapter can currently write cheat data
     * into the emulator's storage.
     */
    fun canInstallCheats(system: EmulatorSystem): Boolean

    /**
     * Install selected cheats for a game.
     *
     * Returns a human-readable error on failure, or null on success.
     */
    fun installCheats(
        system: EmulatorSystem,
        game: Game,
        cheats: List<String>
    ): String?
}

package com.breezybuilds.cheatstation.emulator.dolphin

import android.content.Context
import com.breezybuilds.cheatstation.emulator.EmulatorAdapter
import com.breezybuilds.cheatstation.emulator.EmulatorDetector
import com.breezybuilds.cheatstation.emulator.EmulatorSystem
import com.breezybuilds.cheatstation.model.Game
import com.breezybuilds.cheatstation.scan.DolphinGameScanner
import com.breezybuilds.cheatstation.scan.DolphinPlatform
import com.breezybuilds.cheatstation.scan.ScanResult
import com.breezybuilds.cheatstation.storage.DolphinStorage

class DolphinAdapter(
    private val context: Context,
    private val storage: DolphinStorage,
    private val scanner: DolphinGameScanner
) : EmulatorAdapter {

    override val id: String = "dolphin"

    override val displayName: String = "Dolphin"

    override val supportedSystems: Set<EmulatorSystem> =
        setOf(
            EmulatorSystem.WII,
            EmulatorSystem.GAMECUBE
        )

    override fun isInstalled(): Boolean {
        return EmulatorDetector.packageInstalled(
            context,
            "org.dolphinemu.dolphinemu"
        )
    }

    override fun scan(
        system: EmulatorSystem,
        progress: (String) -> Unit
    ): ScanResult {
        return when (system) {
            EmulatorSystem.WII ->
                scanner.scan(DolphinPlatform.WII, progress)

            EmulatorSystem.GAMECUBE ->
                scanner.scan(DolphinPlatform.GAMECUBE, progress)

            else ->
                ScanResult(
                    games = emptyList(),
                    warnings = emptyList(),
                    error = "$displayName does not support this system."
                )
        }
    }

    override fun canInstallCheats(
        system: EmulatorSystem
    ): Boolean {
        return storage.dolphinUserDoc() != null &&
            system in supportedSystems
    }

    override fun installCheats(
        system: EmulatorSystem,
        game: Game,
        cheats: List<String>
    ): String? {
        /*
         * Dolphin cheat installation will be connected after
         * we resolve the real Game ID from the disc header.
         *
         * The current scanner intentionally uses FILE: URIs as
         * temporary identifiers, so do not write cheat files yet.
         */
        return "Dolphin cheat installation is not connected to this game yet."
    }
}

package com.breezybuilds.cheatstation.emulator

import android.content.Context

object EmulatorDetector {

    data class DetectedEmulator(
        val name: String,
        val packageName: String
    )

    private val threeDsEmulators = listOf(
        "Azahar" to "org.azahar_emu.android",
        "Azahar" to "org.azahar_emu.azahar",
        "Citra" to "org.citra.citra_emu",
        "Citra" to "org.citra.citra",
        "Azahar" to "io.github.lime3ds.android",
        "Lime3DS" to "org.lime3ds.android"
    )

    fun packageInstalled(
        context: Context,
        packageName: String
    ): Boolean {
        return try {
            context.packageManager.getApplicationInfo(
                packageName,
                0
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun detect3dsEmulators(context: Context): List<DetectedEmulator> {
        return threeDsEmulators
            .filter { (_, packageName) ->
                packageInstalled(context, packageName)
            }
            .map { (name, packageName) ->
                DetectedEmulator(name, packageName)
            }
    }

    fun detect3dsEmulator(context: Context): DetectedEmulator? {
        return detect3dsEmulators(context).firstOrNull()
    }
}

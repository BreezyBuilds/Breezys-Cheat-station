package com.breezybuilds.cheatstation.emulator

class EmulatorManager(
    private val adapters: List<EmulatorAdapter>
) {

    fun all(): List<EmulatorAdapter> = adapters

    fun installed(): List<EmulatorAdapter> =
        adapters.filter { it.isInstalled() }

    fun forSystem(system: EmulatorSystem): List<EmulatorAdapter> =
        adapters.filter { system in it.supportedSystems }

    fun installedForSystem(system: EmulatorSystem): List<EmulatorAdapter> =
        forSystem(system).filter { it.isInstalled() }

    fun find(id: String): EmulatorAdapter? =
        adapters.firstOrNull { it.id == id }
}

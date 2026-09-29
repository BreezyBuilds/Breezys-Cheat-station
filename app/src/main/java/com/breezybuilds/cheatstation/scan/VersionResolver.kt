package com.breezybuilds.cheatstation.scan

import com.breezybuilds.cheatstation.util.VersionUtil

/** Decides which installed version to report for a game. */
object VersionResolver {
    /** Highest of the base title's TMD, an installed update's TMD, and a container file's TMD; null when nothing is known. */
    fun resolve(baseTmd: Int?, updateTmd: Int?, fileTmd: Int? = null): String? =
        listOfNotNull(baseTmd, updateTmd, fileTmd).maxOrNull()?.let { VersionUtil.fromTmd(it) }
}

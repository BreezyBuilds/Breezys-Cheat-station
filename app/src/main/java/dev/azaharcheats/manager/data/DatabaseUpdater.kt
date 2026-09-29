package dev.azaharcheats.manager.data

import dev.azaharcheats.manager.model.Game
import dev.azaharcheats.manager.provider.ProviderError

data class UpdateSummary(val updated: Int, val noCheats: Int, val failed: Int, val message: String?) {
    fun describe(): String = buildString {
        append("$updated game(s) updated")
        if (noCheats > 0) append(", $noCheats without cheats in the source")
        if (failed > 0) append(", $failed could not be updated")
        append('.')
        if (message != null) append(' ').append(message)
    }
}

/** Refreshes the cheat index and the cached cheat file of each known game, independent of any APK update. */
object DatabaseUpdater {
    suspend fun updateAll(repo: CheatRepository, games: List<Game>, progress: (Int, Int) -> Unit = { _, _ -> }): UpdateSummary {
        var firstError = repo.refreshIndex()
        var updated = 0; var none = 0; var failed = 0; var consecutive = 0
        for ((i, g) in games.withIndex()) {
            progress(i + 1, games.size)
            if (repo.sourceHas(g.titleId) == false) { none++; continue }
            when (val r = repo.load(g.titleId)) {
                is CheatResult.Loaded -> if (r.fromCache) { failed++; consecutive++; if (firstError == null) firstError = r.notice } else { updated++; consecutive = 0 }
                is CheatResult.Unavailable -> if (r.kind == ProviderError.NOT_FOUND) { none++ } else { failed++; consecutive++; if (firstError == null) firstError = r.message }
            }
            if (consecutive >= 3) { firstError = firstError ?: "Stopped after repeated errors."; break }
        }
        return UpdateSummary(updated, none, failed, firstError)
    }
}

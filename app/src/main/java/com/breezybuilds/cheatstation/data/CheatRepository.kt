package com.breezybuilds.cheatstation.data

import com.breezybuilds.cheatstation.cheats.CheatParser
import com.breezybuilds.cheatstation.model.CheatFile
import com.breezybuilds.cheatstation.provider.CheatProvider
import com.breezybuilds.cheatstation.provider.ProviderError
import com.breezybuilds.cheatstation.provider.ProviderException
import com.breezybuilds.cheatstation.util.AppLog
import java.text.DateFormat
import java.util.Date

sealed class CheatResult {
    data class Loaded(val file: CheatFile, val fetchedAt: Long, val fromCache: Boolean, val notice: String?) : CheatResult()
    data class Unavailable(val message: String, val kind: ProviderError? = null) : CheatResult()
}

/** Reads every enabled 3DS source, with independent cache/index entries per repository. */
class CheatRepository(private val cache: CacheStore, private val providers: () -> List<CheatProvider>) {
    private fun sourceNotice(total: Int, live: Int, failed: Int, cachedFallback: Boolean): String {
        val message = "Combined $total compatible source(s); duplicate codes removed"
        return when {
            live == 0 -> "$message. Offline cached data only; refresh did not succeed."
            cachedFallback -> "$message. Some repositories failed; cached results were used."
            failed > 0 -> "$message. $failed repository request(s) failed."
            else -> message
        }
    }

    fun formatTime(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

    fun cached(titleId: String): CheatResult.Loaded? {
        val found = providers().mapNotNull { provider ->
            cache.loadCheats(provider.id, titleId)?.let { saved ->
                val parsed = CheatParser.parse(saved.text, titleId)
                if (parsed.cheats.isEmpty()) null else Triple(provider.displayName, parsed, saved.fetchedAt)
            }
        }
        if (found.isEmpty()) return null
        return CheatResult.Loaded(MultiSourceCheatMerger.merge(titleId, found.map { it.first to it.second }),
            found.maxOf { it.third }, true, "Cached cheats from ${found.size} source(s)")
    }

    suspend fun load(titleId: String): CheatResult {
        if (providers().isEmpty()) {
            return CheatResult.Unavailable(
                "No cheat repositories are enabled. Select at least one source in Cheats & Sources.")
        }
        var lastError: ProviderException? = null
        var liveSources = 0
        var failedSources = 0
        var usedCachedFallback = false
        val results = mutableListOf<Triple<String, com.breezybuilds.cheatstation.model.CheatFile, Long>>()
        for (provider in providers()) {
            try {
                val fetched = provider.fetch(titleId)
                val parsed = CheatParser.parse(fetched.text, titleId)
                if (parsed.cheats.isEmpty()) continue
                liveSources++
                cache.saveCheats(provider.id, titleId, fetched.text, fetched.fetchedAt)
                results.add(Triple(provider.displayName, parsed, fetched.fetchedAt))
            } catch (e: ProviderException) {
                lastError = e
                failedSources++
                AppLog.w("Repo", "${provider.displayName}: ${e.kind}")
                cache.loadCheats(provider.id, titleId)?.let { saved ->
                    val parsed = CheatParser.parse(saved.text, titleId)
                    if (parsed.cheats.isNotEmpty()) {
                        usedCachedFallback = true
                        results.add(Triple(provider.displayName, parsed, saved.fetchedAt))
                    }
                }
            } catch (e: Exception) {
                failedSources++
                AppLog.e("Repo", "Unexpected error from ${provider.displayName}", e)
                // Network and parser failures should not discard valid per-source cached cheats.
                cache.loadCheats(provider.id, titleId)?.let { saved ->
                    try {
                        val parsed = CheatParser.parse(saved.text, titleId)
                        if (parsed.cheats.isNotEmpty()) {
                            usedCachedFallback = true
                            results.add(Triple(provider.displayName, parsed, saved.fetchedAt))
                        }
                    } catch (cacheError: Exception) {
                        AppLog.e("Repo", "Could not parse cached cheats from ${provider.displayName}", cacheError)
                    }
                }
            }
        }
        if (results.isNotEmpty()) {
            return CheatResult.Loaded(MultiSourceCheatMerger.merge(titleId, results.map { it.first to it.second }),
                results.maxOf { it.third }, liveSources == 0,
                sourceNotice(results.size, liveSources, failedSources, usedCachedFallback))
        }
        return cached(titleId) ?: CheatResult.Unavailable(lastError?.message ?: "No cheats found in the selected repositories.", lastError?.kind)
    }

    /** Updates indexes independently, retaining successful indexes when another source fails. */
    suspend fun refreshIndex(): String? {
        var succeeded = 0
        var lastError: String? = null
        for (p in providers()) {
            try {
                val ids = p.listTitleIds()
                if (ids != null) {
                    cache.saveIndex(p.id, ids, System.currentTimeMillis())
                    succeeded++
                }
            } catch (e: ProviderException) { lastError = e.message }
            catch (e: Exception) { AppLog.e("Repo", "Index refresh failed: ${p.displayName}", e); lastError = "Could not refresh ${p.displayName}." }
        }
        return if (succeeded > 0) null else lastError
    }

    /** False only if ALL enabled sources have a known index and none contain the game. */
    fun sourceHas(titleId: String): Boolean? {
        val all = providers()
        if (all.isEmpty()) return null
        var unknown = false
        for (p in all) {
            val index = cache.loadIndex(p.id)
            if (index == null) unknown = true
            else if (titleId in index.ids) return true
        }
        return if (unknown) null else false
    }

    fun indexTime(): Long? = providers().mapNotNull { cache.loadIndex(it.id)?.fetchedAt }.maxOrNull()
}

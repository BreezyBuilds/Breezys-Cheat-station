package com.breezybuilds.cheatstation.data

import com.breezybuilds.cheatstation.cheats.PnachParser
import com.breezybuilds.cheatstation.provider.CheatProvider
import com.breezybuilds.cheatstation.provider.ProviderError
import com.breezybuilds.cheatstation.provider.ProviderException
import com.breezybuilds.cheatstation.util.AppLog
import java.text.DateFormat
import java.util.Date

class Ps2CheatRepository(
    private val cache: CacheStore,
    private val providers: () -> List<CheatProvider>
) {
    private fun sourceNotice(total: Int, live: Int, failed: Int, cachedFallback: Boolean): String {
        val message = "Combined $total compatible source(s); duplicate codes removed"
        return when {
            live == 0 -> "$message. Offline cached data only; refresh did not succeed."
            cachedFallback -> "$message. Some repositories failed; cached results were used."
            failed > 0 -> "$message. $failed repository request(s) failed."
            else -> message
        }
    }

    fun formatTime(t: Long) =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

    fun cached(key: String): CheatResult.Loaded? {
        val found = providers().mapNotNull { provider ->
            cache.loadCheats(provider.id, key)?.let { saved ->
                val parsed = PnachParser.parse(saved.text, key)
                if (parsed.cheats.isEmpty()) null else Triple(provider.displayName, parsed, saved.fetchedAt)
            }
        }
        if (found.isEmpty()) return null
        return CheatResult.Loaded(MultiSourceCheatMerger.merge(key, found.map { it.first to it.second }),
            found.maxOf { it.third }, true, "Cached cheats from ${found.size} source(s)")
    }

    suspend fun load(key: String): CheatResult {
        var lastError: ProviderException? = null
        var liveSources = 0
        var failedSources = 0
        var usedCachedFallback = false
        val found = mutableListOf<Triple<String, com.breezybuilds.cheatstation.model.CheatFile, Long>>()
        for (provider in providers()) {
            try {
                val fetched = provider.fetch(key)
                val parsed = PnachParser.parse(fetched.text, key)
                if (parsed.cheats.isEmpty()) continue
                liveSources++
                cache.saveCheats(provider.id, key, fetched.text, fetched.fetchedAt)
                found.add(Triple(provider.displayName, parsed, fetched.fetchedAt))
            } catch (e: ProviderException) {
                lastError = e
                failedSources++
                cache.loadCheats(provider.id, key)?.let { saved ->
                    val parsed = PnachParser.parse(saved.text, key)
                    if (parsed.cheats.isNotEmpty()) {
                        usedCachedFallback = true
                        found.add(Triple(provider.displayName, parsed, saved.fetchedAt))
                    }
                }
            } catch (e: Exception) {
                failedSources++
                AppLog.e("PS2Repo", "Unexpected error from ${provider.displayName}", e)
            }
        }
        if (found.isNotEmpty()) return CheatResult.Loaded(
            MultiSourceCheatMerger.merge(key, found.map { it.first to it.second }),
            found.maxOf { it.third }, liveSources == 0,
            sourceNotice(found.size, liveSources, failedSources, usedCachedFallback))
        return cached(key) ?: CheatResult.Unavailable(
            lastError?.message ?: "No PS2 cheats were found in the configured sources.", lastError?.kind)
    }
}

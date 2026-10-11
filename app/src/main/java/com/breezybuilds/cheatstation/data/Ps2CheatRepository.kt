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
        val found = mutableListOf<Triple<String, com.breezybuilds.cheatstation.model.CheatFile, Long>>()
        for (provider in providers()) {
            try {
                val fetched = provider.fetch(key)
                val parsed = PnachParser.parse(fetched.text, key)
                if (parsed.cheats.isEmpty()) continue
                cache.saveCheats(provider.id, key, fetched.text, fetched.fetchedAt)
                found.add(Triple(provider.displayName, parsed, fetched.fetchedAt))
            } catch (e: ProviderException) {
                lastError = e
                cache.loadCheats(provider.id, key)?.let { saved ->
                    val parsed = PnachParser.parse(saved.text, key)
                    if (parsed.cheats.isNotEmpty()) found.add(Triple(provider.displayName, parsed, saved.fetchedAt))
                }
            } catch (e: Exception) {
                AppLog.e("PS2Repo", "Unexpected error from ${provider.displayName}", e)
            }
        }
        if (found.isNotEmpty()) return CheatResult.Loaded(
            MultiSourceCheatMerger.merge(key, found.map { it.first to it.second }),
            found.maxOf { it.third }, false,
            "Combined ${found.size} compatible source(s); duplicate codes removed")
        return cached(key) ?: CheatResult.Unavailable(
            lastError?.message ?: "No PS2 cheats were found in the configured sources.", lastError?.kind)
    }
}

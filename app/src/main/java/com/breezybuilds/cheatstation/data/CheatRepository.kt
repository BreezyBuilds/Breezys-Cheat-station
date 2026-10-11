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
        var lastError: ProviderException? = null
        val results = mutableListOf<Triple<String, com.breezybuilds.cheatstation.model.CheatFile, Long>>()
        for (provider in providers()) {
            try {
                val fetched = provider.fetch(titleId)
                val parsed = CheatParser.parse(fetched.text, titleId)
                if (parsed.cheats.isEmpty()) continue
                cache.saveCheats(provider.id, titleId, fetched.text, fetched.fetchedAt)
                results.add(Triple(provider.displayName, parsed, fetched.fetchedAt))
            } catch (e: ProviderException) {
                lastError = e
                AppLog.w("Repo", "${provider.displayName}: ${e.kind}")
                cache.loadCheats(provider.id, titleId)?.let { saved ->
                    val parsed = CheatParser.parse(saved.text, titleId)
                    if (parsed.cheats.isNotEmpty()) results.add(Triple(provider.displayName, parsed, saved.fetchedAt))
                }
            } catch (e: Exception) {
                AppLog.e("Repo", "Unexpected error from ${provider.displayName}", e)
            }
        }
        if (results.isNotEmpty()) {
            return CheatResult.Loaded(MultiSourceCheatMerger.merge(titleId, results.map { it.first to it.second }),
                results.maxOf { it.third }, false, "Combined ${results.size} compatible source(s); duplicate codes removed")
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

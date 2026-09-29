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
    /** [fromCache] = true means the data was NOT just downloaded. */
    data class Loaded(val file: CheatFile, val fetchedAt: Long, val fromCache: Boolean, val notice: String?) : CheatResult()
    data class Unavailable(val message: String, val kind: ProviderError? = null) : CheatResult()
}

/** Combines a [CheatProvider] with the local cache: fresh data when possible, clearly-labelled cached data otherwise. */
class CheatRepository(private val cache: CacheStore, private val provider: () -> CheatProvider) {

    fun formatTime(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

    /** Cache only, never touches the network. */
    fun cached(titleId: String): CheatResult.Loaded? {
        val src = provider().id
        val c = cache.loadCheats(src, titleId) ?: return null
        return CheatResult.Loaded(CheatParser.parse(c.text, titleId), c.fetchedAt, true, null)
    }

    /** Tries the network; falls back to cached data (flagged as cached) when that fails. */
    suspend fun load(titleId: String): CheatResult {
        val p = provider()
        return try {
            val r = p.fetch(titleId)
            cache.saveCheats(p.id, titleId, r.text, r.fetchedAt)
            val file = CheatParser.parse(r.text, titleId)
            val notice = if (file.warnings.isNotEmpty()) "${file.warnings.size} line(s) in the source file were skipped because they were malformed." else null
            CheatResult.Loaded(file, r.fetchedAt, false, notice)
        } catch (e: ProviderException) {
            AppLog.w("Repo", "Fetch failed for $titleId: ${e.kind}")
            val c = cached(titleId)
            if (c != null && e.kind != ProviderError.CONFIG) {
                c.copy(notice = "${e.message} Showing saved (cached) cheats from ${formatTime(c.fetchedAt)}; they may be out of date.")
            } else CheatResult.Unavailable(e.message ?: "Could not load cheats.", e.kind)
        } catch (e: Exception) {
            AppLog.e("Repo", "Unexpected error for $titleId", e)
            cached(titleId)?.copy(notice = "Something went wrong while downloading. Showing saved (cached) cheats.")
                ?: CheatResult.Unavailable("Something went wrong while downloading cheats.")
        }
    }

    /** Refreshes the list of Title IDs the source has. Returns null on success, else an error message. */
    suspend fun refreshIndex(): String? {
        val p = provider()
        return try {
            val ids = p.listTitleIds() ?: return null
            cache.saveIndex(p.id, ids, System.currentTimeMillis())
            null
        } catch (e: ProviderException) { e.message }
        catch (e: Exception) { AppLog.e("Repo", "Index refresh failed", e); "Could not refresh the cheat list." }
    }

    /** true/false when the index is known, null otherwise. */
    fun sourceHas(titleId: String): Boolean? = cache.loadIndex(provider().id)?.ids?.contains(titleId)
    fun indexTime(): Long? = cache.loadIndex(provider().id)?.fetchedAt
}

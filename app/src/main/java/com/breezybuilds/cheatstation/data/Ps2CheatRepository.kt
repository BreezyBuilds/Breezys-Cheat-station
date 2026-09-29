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
        for (provider in providers()) {
            val c = cache.loadCheats(provider.id, key) ?: continue
            val f = PnachParser.parse(c.text, key)
            if (f.cheats.isNotEmpty()) {
                return CheatResult.Loaded(f, c.fetchedAt, true, null)
            }
        }
        return null
    }

    suspend fun load(key: String): CheatResult {
        var lastError: ProviderException? = null

        for (provider in providers()) {
            try {
                val r = provider.fetch(key)
                val f = PnachParser.parse(r.text, key)

                if (f.cheats.isEmpty()) {
                    lastError = ProviderException(
                        ProviderError.INVALID,
                        "No usable PS2 cheats were found in ${provider.displayName}."
                    )
                    continue
                }

                cache.saveCheats(provider.id, key, r.text, r.fetchedAt)

                val notice = if (f.warnings.isNotEmpty()) {
                    "${f.warnings.size} line(s) skipped while parsing the PNACH file. Source: ${provider.displayName}."
                } else {
                    "Source: ${provider.displayName}"
                }

                return CheatResult.Loaded(
                    f,
                    r.fetchedAt,
                    false,
                    notice
                )
            } catch (e: ProviderException) {
                lastError = e

                val cached = cache.loadCheats(provider.id, key)
                if (cached != null && e.kind != ProviderError.CONFIG) {
                    val f = PnachParser.parse(cached.text, key)
                    if (f.cheats.isNotEmpty()) {
                        return CheatResult.Loaded(
                            f,
                            cached.fetchedAt,
                            true,
                            "${e.message} Showing cached PS2 cheats from ${formatTime(cached.fetchedAt)}."
                        )
                    }
                }

                if (e.kind == ProviderError.CONFIG) break
            } catch (e: Exception) {
                AppLog.e("PS2Repo", "Unexpected error from ${provider.displayName}", e)
            }
        }

        return CheatResult.Unavailable(
            lastError?.message ?: "No PS2 cheats were found in the configured sources.",
            lastError?.kind
        )
    }
}

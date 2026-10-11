package com.breezybuilds.cheatstation.provider

import com.breezybuilds.cheatstation.cheats.dolphin.DolphinCheatParser
import com.breezybuilds.cheatstation.data.MultiSourceCheatMerger
import com.breezybuilds.cheatstation.model.CheatFile
import com.breezybuilds.cheatstation.util.AppLog
import org.json.JSONObject

class DolphinCheatProvider(
    private val sources: () -> List<DolphinCheatSource>,
    private val client: HttpClient = UrlConnectionClient()
) {

    fun fetch(gameId: String): CheatFile {
        val id = gameId.trim().uppercase()

        if (id.length != 6 || !id.all { it.isLetterOrDigit() }) {
            throw ProviderException(
                ProviderError.INVALID,
                "Invalid Dolphin Game ID: $gameId"
            )
        }

        var lastError: ProviderException? = null
        val results = mutableListOf<Pair<String, CheatFile>>()

        for (source in sources().distinctBy { it.id }) {
            try {
                val result = fetchFromSource(source, id)
                if (result != null && result.cheats.isNotEmpty()) {
                    AppLog.i("DolphinCheats", "Found $id in ${source.name}")
                    results.add(source.name to result)
                }
            } catch (e: ProviderException) {
                lastError = e
                AppLog.e("DolphinCheats", "Source ${source.name} failed", e)
            } catch (e: Exception) {
                AppLog.e("DolphinCheats", "Source ${source.name} failed", e)
            }
        }

        if (results.isNotEmpty()) return MultiSourceCheatMerger.merge(id, results)

        throw lastError ?: ProviderException(
            ProviderError.NOT_FOUND,
            "No Dolphin cheats were found in the enabled sources."
        )
    }

    private fun fetchFromSource(
        source: DolphinCheatSource,
        gameId: String
    ): CheatFile? {
        val exactFile = "$gameId.ini"

        val exactResponse = client.get(
            source.rawUrl(exactFile),
            mapOf("Accept" to "text/plain")
        )

        if (exactResponse.code in 200..299) {
            val parsed = DolphinCheatParser.parse(
                exactResponse.body,
                gameId
            )

            if (parsed.cheats.isNotEmpty()) {
                return parsed
            }
        } else if (exactResponse.code != 404) {
            throw httpFailure(source.name, exactResponse)
        }

        val file = findFile(source, gameId)
            ?: return null

        val response = client.get(
            source.rawUrl(file),
            mapOf("Accept" to "text/plain")
        )

        if (response.code !in 200..299) {
            throw httpFailure(source.name, response)
        }

        return DolphinCheatParser.parse(
            response.body,
            gameId
        )
    }

    private fun httpFailure(name: String, response: HttpResponse): ProviderException {
        val limited = response.code == 429 ||
            (response.code == 403 && response.headers["x-ratelimit-remaining"] == "0")
        return if (limited) ProviderException(ProviderError.RATE_LIMITED,
            "$name is temporarily rate limited by GitHub.")
        else ProviderException(ProviderError.HTTP, "$name returned HTTP ${response.code}.")
    }

    private fun findFile(
        source: DolphinCheatSource,
        gameId: String
    ): String? {
        val response = try {
            client.get(
                source.treeUrl(),
                mapOf(
                    "Accept" to "application/vnd.github+json",
                    "User-Agent" to "Breezys-Cheat-Station",
                    "X-GitHub-Api-Version" to "2026-03-10"
                )
            )
        } catch (e: Exception) {
            throw ProviderException(
                ProviderError.OFFLINE,
                "Could not connect to ${source.name}."
            )
        }

        if (response.code == 404) return null

        if (response.code !in 200..299) {
            throw httpFailure(source.name, response)
        }

        val index = try { JSONObject(response.body) } catch (e: Exception) {
            throw ProviderException(ProviderError.INVALID, "${source.name} returned an invalid repository index.")
        }
        if (index.optBoolean("truncated")) {
            throw ProviderException(ProviderError.INVALID,
                "${source.name} returned an incomplete repository index. Cheat availability cannot be confirmed.")
        }
        val tree = index.optJSONArray("tree")
            ?: throw ProviderException(ProviderError.INVALID, "${source.name} returned an invalid repository index.")

        val wantedFull = "$gameId.ini"
        val wantedShort = "${gameId.take(3)}.ini"
        val basePath = source.path.trim('/')

        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue

            if (item.optString("type") != "blob") continue

            val path = item.optString("path")
            val filename = path.substringAfterLast('/')

            if (
                (filename.equals(wantedFull, ignoreCase = true) ||
                    filename.equals(wantedShort, ignoreCase = true)) &&
                (basePath.isEmpty() ||
                    path.startsWith("$basePath/", ignoreCase = true))
            ) {
                return path
            }
        }

        return null
    }
}

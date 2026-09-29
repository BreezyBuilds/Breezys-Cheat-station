package com.breezybuilds.cheatstation.provider

import com.breezybuilds.cheatstation.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class Ps2CheatProvider(private val source: Ps2CheatSource, private val http: HttpClient = UrlConnectionClient()) : CheatProvider {
    override val id: String get() = source.id
    override val displayName: String get() = source.name

    private fun call(url: String): HttpResponse {
        val r = try { http.get(url) } catch (e: IOException) {
            throw ProviderException(ProviderError.OFFLINE, "Can't reach the PS2 cheat source. Check your internet connection and try again.")
        }
        when (r.code) {
            404 -> throw ProviderException(ProviderError.NOT_FOUND, "No PS2 cheats were found for this game in ${source.name}.")
            429 -> throw ProviderException(ProviderError.RATE_LIMITED, "The PS2 cheat source is rate limiting requests. Try again later.")
            in 200..299 -> return r
            else -> throw ProviderException(ProviderError.HTTP, "The PS2 cheat source returned HTTP ${r.code}.")
        }
    }

    override suspend fun fetch(titleId: String): ProviderResult = withContext(Dispatchers.IO) {
        val key = titleId.trim().uppercase()
        if (!Regex("^[A-Z0-9]{4,12}-?[0-9]{3,6}_[0-9A-F]{8}$").matches(key))
            throw ProviderException(ProviderError.INVALID, "Invalid PS2 game identifier. Expected SERIAL_CRC.")
        val url = source.rawUrl(key)
        val r = call(url)
        if (r.body.isBlank()) throw ProviderException(ProviderError.INVALID, "The PS2 cheat file was empty.")
        ProviderResult(key, r.body, System.currentTimeMillis(), url)
    }

    override suspend fun listTitleIds(): Set<String>? = withContext(Dispatchers.IO) {
        val api = "https://api.github.com/repos/${source.owner}/${source.repo}/git/trees/${source.branch}?recursive=1"
        val r = try { http.get(api, mapOf("Accept" to "application/vnd.github+json")) } catch (e: IOException) {
            throw ProviderException(ProviderError.OFFLINE, "Can't reach the PS2 cheat source.")
        }
        if (r.code !in 200..299) throw ProviderException(ProviderError.HTTP, "Could not read the PS2 cheat source index (HTTP ${r.code}).")
        val out = HashSet<String>()
        try {
            val tree = JSONObject(r.body).getJSONArray("tree")
            val prefix = source.path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val rx = Regex("^([A-Za-z0-9-]+_[0-9A-Fa-f]{8})\\.pnach$")
            for (i in 0 until tree.length()) {
                val o = tree.getJSONObject(i)
                if (o.optString("type") != "blob") continue
                val path = o.optString("path")
                if (!path.startsWith(prefix)) continue
                val name = path.removePrefix(prefix)
                if ('/' in name) continue
                rx.matchEntire(name)?.let { out += it.groupValues[1].uppercase() }
            }
            out
        } catch (e: Exception) {
            AppLog.e("PS2", "Could not parse source index", e)
            throw ProviderException(ProviderError.INVALID, "The PS2 cheat source returned an unexpected index format.")
        }
    }
}

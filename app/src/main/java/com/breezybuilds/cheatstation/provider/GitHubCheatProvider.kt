package com.breezybuilds.cheatstation.provider

import com.breezybuilds.cheatstation.cheats.CheatParser
import com.breezybuilds.cheatstation.util.AppLog
import com.breezybuilds.cheatstation.util.TitleId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.text.DateFormat
import java.util.Date

class GitHubCheatProvider(
    private val config: CheatSourceConfig,
    private val http: HttpClient = UrlConnectionClient(),
) : CheatProvider {
    override val id: String get() = config.id
    override val displayName: String get() = config.displayName

    private fun call(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        AppLog.d("GitHub", "GET $url")
        val r = try { http.get(url, headers) } catch (e: IOException) {
            AppLog.w("GitHub", "Network error: ${e.javaClass.simpleName}")
            throw ProviderException(ProviderError.OFFLINE, "Can't reach GitHub. Check your internet connection and try again.")
        }
        AppLog.d("GitHub", "HTTP ${r.code}")
        when {
            r.code == 429 || (r.code == 403 && r.headers["x-ratelimit-remaining"] == "0") -> {
                val reset = r.headers["x-ratelimit-reset"]?.toLongOrNull()?.let { " Try again after ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000))}." } ?: " Try again later."
                throw ProviderException(ProviderError.RATE_LIMITED, "GitHub is limiting requests.$reset")
            }
            r.code == 404 -> throw ProviderException(ProviderError.NOT_FOUND, "Not found in the cheat source.")
            r.code !in 200..299 -> throw ProviderException(ProviderError.HTTP, "The cheat source returned an error (HTTP ${r.code}).")
        }
        return r
    }

    override suspend fun fetch(titleId: String): ProviderResult = withContext(Dispatchers.IO) {
        val tid = TitleId.normalize(titleId) ?: throw ProviderException(ProviderError.INVALID, "That is not a valid Title ID.")
        config.validate()?.let { throw ProviderException(ProviderError.CONFIG, it) }
        val url = config.rawUrl(tid)
        val r = try { call(url) } catch (e: ProviderException) {
            if (e.kind == ProviderError.NOT_FOUND) throw ProviderException(ProviderError.NOT_FOUND, "No cheats for this game were found in ${config.displayName}.")
            throw e
        }
        if (r.body.isBlank()) throw ProviderException(ProviderError.INVALID, "The cheat file was empty.")
        val parsed = CheatParser.parse(r.body, tid)
        if (parsed.cheats.isEmpty()) {
            AppLog.w("GitHub", "Unparseable cheat file for $tid: ${parsed.warnings.firstOrNull() ?: "no cheats"}")
            throw ProviderException(ProviderError.INVALID, "The cheat file for this game could not be understood (no valid cheats).")
        }
        ProviderResult(tid, r.body, System.currentTimeMillis(), url)
    }

    override suspend fun listTitleIds(): Set<String>? = withContext(Dispatchers.IO) {
        config.validate()?.let { throw ProviderException(ProviderError.CONFIG, it) }
        val headers = mutableMapOf("Accept" to "application/vnd.github+json")
        config.token?.takeIf { it.isNotBlank() }?.let { headers["Authorization"] = "Bearer $it" }
        val r = call(config.treeUrl(), headers)
        try {
            val tree = JSONObject(r.body).getJSONArray("tree")
            val prefix = config.basePath.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val rx = config.fileNameRegex()
            val out = HashSet<String>()
            for (i in 0 until tree.length()) {
                val o = tree.getJSONObject(i)
                if (o.optString("type") != "blob") continue
                val path = o.optString("path")
                if (!path.startsWith(prefix)) continue
                val name = path.removePrefix(prefix)
                if (name.contains('/')) continue
                rx.matchEntire(name)?.let { out += it.groupValues[1].uppercase() }
            }
            if (JSONObject(r.body).optBoolean("truncated")) AppLog.w("GitHub", "Repository listing was truncated by GitHub")
            out
        } catch (e: Exception) {
            throw ProviderException(ProviderError.INVALID, "The cheat source returned data in an unexpected format.")
        }
    }
}

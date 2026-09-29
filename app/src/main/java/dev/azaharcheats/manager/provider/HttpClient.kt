package dev.azaharcheats.manager.provider

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpResponse(val code: Int, val body: String, val headers: Map<String, String>)

interface HttpClient {
    /** Throws java.io.IOException for network problems. */
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
}

class UrlConnectionClient(private val maxBytes: Int = 4_000_000) : HttpClient {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "AzaharCheatManager/1.0")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val out = ByteArrayOutputStream()
            stream?.use { s ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw java.io.IOException("Response too large")
                }
            }
            val h = HashMap<String, String>()
            c.headerFields.forEach { (k, v) -> if (k != null && v.isNotEmpty()) h[k.lowercase()] = v.first() }
            return HttpResponse(code, out.toString(Charsets.UTF_8.name()), h)
        } finally { c.disconnect() }
    }
}

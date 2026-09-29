package com.breezybuilds.cheatstation.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** In-memory ring-buffer log (also mirrored to logcat). Tokens and secrets are redacted. */
object AppLog {
    @Volatile var verbose: Boolean = false
    private const val MAX = 600
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val secrets = listOf(
        Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("(?i)(bearer|token)\\s+[A-Za-z0-9._\\-]{12,}"),
    )

    fun redact(s: String): String = secrets.fold(s) { acc, r -> acc.replace(r, "[redacted]") }

    fun d(tag: String, msg: String) { if (verbose) add('D', tag, msg) }
    fun i(tag: String, msg: String) = add('I', tag, msg)
    fun w(tag: String, msg: String) = add('W', tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) =
        add('E', tag, if (t != null) "$msg (${t.javaClass.simpleName}: ${t.message})" else msg)

    private fun add(level: Char, tag: String, msg: String) {
        val line = "${fmt.format(Date())} $level/$tag: ${redact(msg)}"
        synchronized(buf) {
            if (buf.size >= MAX) buf.removeFirst()
            buf.addLast(line)
        }
        try { Log.println(when (level) { 'E' -> Log.ERROR; 'W' -> Log.WARN; 'D' -> Log.DEBUG; else -> Log.INFO }, "AzaharCM", line) } catch (_: Throwable) {}
    }

    fun dump(): String = synchronized(buf) { buf.joinToString("\n") }
    fun clear() = synchronized(buf) { buf.clear() }
}

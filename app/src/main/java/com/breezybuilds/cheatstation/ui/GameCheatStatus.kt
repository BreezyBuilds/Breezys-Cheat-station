package com.breezybuilds.cheatstation.ui

import android.content.Context
import android.graphics.Color
import android.widget.TextView

/** Persistent library summaries; negative results are recorded only for confirmed NOT_FOUND. */
internal object GameCheatStatus {
    private const val TTL = 7L * 24 * 60 * 60 * 1000
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("library_cheat_counts_v1", Context.MODE_PRIVATE)
    fun get(ctx: Context, platform: String, id: String): Int? {
        val key = "$platform:$id"
        val p = prefs(ctx)
        if (!p.contains(key) || System.currentTimeMillis() - p.getLong("$key:time", 0) > TTL) return null
        return p.getInt(key, -2).takeIf { it >= 0 }
    }
    fun remove(ctx: Context, platform: String, id: String) {
        val key = "$platform:$id"
        prefs(ctx).edit().remove(key).remove("$key:time").apply()
    }
    fun put(ctx: Context, platform: String, id: String, count: Int) {
        val key = "$platform:$id"
        prefs(ctx).edit().putInt(key, count).putLong("$key:time", System.currentTimeMillis()).apply()
    }
    fun style(view: TextView, raw: String) {
        val value = raw.trim()
        val count = Regex("^([0-9]+) +cheat", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.toIntOrNull()
        when {
            count != null && count > 0 -> {
                view.text = "✓  $count ${if (count == 1) "cheat" else "cheats"} available"
                view.setTextColor(Ui.Appearance.positive(view.context))
            }
            count == 0 || value.startsWith("No cheats", true) -> {
                view.text = "✕  No cheats found"
                view.setTextColor(Ui.Appearance.negative(view.context))
            }
            value.startsWith("Scanning", true) || value.startsWith("Checking", true) -> {
                view.text = "◌  Scanning cheats…"
                view.setTextColor(Ui.Appearance.secondary(view.context))
            }
            value.startsWith("Check failed", true) || value.startsWith("Unable", true) -> {
                view.text = "⚠  Unable to check cheats"
                view.setTextColor(Ui.Appearance.warning(view.context))
            }
            else -> {
                view.text = "○  Check cheats"
                view.setTextColor(Ui.Appearance.secondary(view.context))
            }
        }
    }
}

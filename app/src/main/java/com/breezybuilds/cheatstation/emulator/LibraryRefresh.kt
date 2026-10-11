package com.breezybuilds.cheatstation.emulator

import android.content.Context

/** A refresh decision must not enumerate ROMs or query the document provider. */
object LibraryRefresh {
    enum class Policy { WHEN_NEEDED, DAILY, EVERY_LAUNCH, MANUAL }
    private const val PREF = "retro_loader_library_refresh"
    fun policy(ctx: Context): Policy = try {
        Policy.valueOf(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("policy", Policy.WHEN_NEEDED.name) ?: Policy.WHEN_NEEDED.name)
    } catch (_: Exception) { Policy.WHEN_NEEDED }
    fun setPolicy(ctx: Context, policy: Policy) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putString("policy", policy.name).apply()
    fun needsScan(ctx: Context, system: String, signature: String, hasCache: Boolean): Boolean {
        if (!hasCache) return true
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getString("source_$system", null) != signature) return true
        return when (policy(ctx)) {
            Policy.WHEN_NEEDED, Policy.MANUAL -> false
            Policy.EVERY_LAUNCH -> !prefs.getBoolean("checked_this_launch_$system", false)
            Policy.DAILY -> System.currentTimeMillis() - prefs.getLong("checked_at_$system", 0L) >= 86_400_000L
        }
    }
    fun scanned(ctx: Context, system: String, signature: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("source_$system", signature)
            .putLong("checked_at_$system", System.currentTimeMillis())
            .putBoolean("checked_this_launch_$system", true).apply()
    }
}

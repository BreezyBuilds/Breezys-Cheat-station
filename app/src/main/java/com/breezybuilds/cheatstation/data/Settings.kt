package com.breezybuilds.cheatstation.data

import android.content.Context
import androidx.core.content.edit
import com.breezybuilds.cheatstation.provider.CheatSourceConfig

class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("breezy_cheat_station_settings", Context.MODE_PRIVATE)

    var autoUpdate: Boolean
        get() = p.getBoolean("auto_update", true)
        set(v) = p.edit { putBoolean("auto_update", v) }
    var debugLog: Boolean
        get() = p.getBoolean("debug_log", false)
        set(v) = p.edit { putBoolean("debug_log", v) }
    var lastAutoCheck: Long
        get() = p.getLong("last_auto_check", 0L)
        set(v) = p.edit { putLong("last_auto_check", v) }

    fun source(): CheatSourceConfig {
        val d = CheatSourceConfig.DEFAULT
        return CheatSourceConfig(
            owner = p.getString("src_owner", d.owner) ?: d.owner,
            repo = p.getString("src_repo", d.repo) ?: d.repo,
            branch = p.getString("src_branch", d.branch) ?: d.branch,
            basePath = p.getString("src_path", d.basePath) ?: d.basePath,
            fileNamePattern = p.getString("src_pattern", d.fileNamePattern) ?: d.fileNamePattern,
            token = p.getString("src_token", null)?.takeIf { it.isNotBlank() },
        )
    }

    fun saveSource(c: CheatSourceConfig) = p.edit {
        putString("src_owner", c.owner.trim()); putString("src_repo", c.repo.trim()); putString("src_branch", c.branch.trim())
        putString("src_path", c.basePath.trim()); putString("src_pattern", c.fileNamePattern.trim()); putString("src_token", c.token?.trim())
    }

    fun resetSource() = p.edit { remove("src_owner"); remove("src_repo"); remove("src_branch"); remove("src_path"); remove("src_pattern"); remove("src_token") }
}

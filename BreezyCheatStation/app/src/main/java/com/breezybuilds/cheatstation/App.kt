package com.breezybuilds.cheatstation

import android.app.Application
import android.content.Context
import com.breezybuilds.cheatstation.cheats.BackupManager
import com.breezybuilds.cheatstation.cheats.CheatInstaller
import com.breezybuilds.cheatstation.data.CacheStore
import com.breezybuilds.cheatstation.data.CheatRepository
import com.breezybuilds.cheatstation.data.Settings
import com.breezybuilds.cheatstation.provider.GitHubCheatProvider
import com.breezybuilds.cheatstation.scan.GameScanner
import com.breezybuilds.cheatstation.scan.TitleIdResolver
import com.breezybuilds.cheatstation.storage.StorageManager
import com.breezybuilds.cheatstation.util.AppLog
import java.io.File

/** Simple manual dependency container. */
class App : Application() {
    lateinit var storage: StorageManager
        private set
    lateinit var settings: Settings
        private set
    lateinit var cache: CacheStore
        private set
    lateinit var repository: CheatRepository
        private set
    lateinit var scanner: GameScanner
        private set

    override fun onCreate() {
        super.onCreate()
        storage = StorageManager(this)
        settings = Settings(this)
        cache = CacheStore(File(filesDir, "cache"))
        repository = CheatRepository(cache) { GitHubCheatProvider(settings.source()) }
        scanner = GameScanner(this, storage, TitleIdResolver(this))
        AppLog.verbose = settings.debugLog
        AppLog.i("App", "Started v${BuildConfigVersion.name(this)}")
    }

    /** Installer bound to the current cheats folder, or null if it is not available. */
    fun installer(): CheatInstaller? {
        val store = storage.cheatsStore() ?: return null
        return CheatInstaller(store, BackupManager(store))
    }

    fun backups(): BackupManager? = storage.cheatsStore()?.let { BackupManager(it) }
}

val Context.app: App get() = applicationContext as App

object BuildConfigVersion {
    fun name(ctx: Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
}

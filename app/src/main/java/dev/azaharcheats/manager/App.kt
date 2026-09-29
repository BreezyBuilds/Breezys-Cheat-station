package dev.azaharcheats.manager

import android.app.Application
import android.content.Context
import dev.azaharcheats.manager.cheats.BackupManager
import dev.azaharcheats.manager.cheats.CheatInstaller
import dev.azaharcheats.manager.data.CacheStore
import dev.azaharcheats.manager.data.CheatRepository
import dev.azaharcheats.manager.data.Settings
import dev.azaharcheats.manager.provider.GitHubCheatProvider
import dev.azaharcheats.manager.scan.GameScanner
import dev.azaharcheats.manager.scan.TitleIdResolver
import dev.azaharcheats.manager.storage.StorageManager
import dev.azaharcheats.manager.util.AppLog
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

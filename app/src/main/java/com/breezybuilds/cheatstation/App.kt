package com.breezybuilds.cheatstation

import android.app.Application
import android.content.Context
import com.breezybuilds.cheatstation.cheats.BackupManager
import com.breezybuilds.cheatstation.cheats.CheatInstaller
import com.breezybuilds.cheatstation.cheats.PnachCheatInstaller
import com.breezybuilds.cheatstation.cheats.PnachBackupManager
import com.breezybuilds.cheatstation.data.CacheStore
import com.breezybuilds.cheatstation.data.CheatRepository
import com.breezybuilds.cheatstation.data.Settings
import com.breezybuilds.cheatstation.data.Ps2CheatRepository
import com.breezybuilds.cheatstation.emulator.EmulatorManager
import com.breezybuilds.cheatstation.emulator.dolphin.DolphinAdapter
import com.breezybuilds.cheatstation.provider.GitHubCheatProvider
import com.breezybuilds.cheatstation.provider.Ps2CheatProvider
import com.breezybuilds.cheatstation.provider.Ps2CheatSources
import com.breezybuilds.cheatstation.provider.Ps2Settings
import com.breezybuilds.cheatstation.provider.DolphinSettings
import com.breezybuilds.cheatstation.scan.GameScanner
import com.breezybuilds.cheatstation.scan.DolphinGameScanner
import com.breezybuilds.cheatstation.scan.Ps2GameScanner
import com.breezybuilds.cheatstation.scan.TitleIdResolver
import com.breezybuilds.cheatstation.storage.DolphinStorage
import com.breezybuilds.cheatstation.storage.Ps2Storage
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

    lateinit var ps2Storage: Ps2Storage
        private set

    lateinit var ps2Settings: Ps2Settings
        private set

    lateinit var ps2Scanner: Ps2GameScanner
        private set

    lateinit var ps2Repository: Ps2CheatRepository
        private set

    lateinit var dolphinStorage: DolphinStorage
    lateinit var dolphinSettings: DolphinSettings
        private set

    lateinit var dolphinScanner: DolphinGameScanner
        private set

    lateinit var emulatorManager: EmulatorManager
        private set

    override fun onCreate() {
        super.onCreate()

        storage = StorageManager(this)
        settings = Settings(this)
        cache = CacheStore(File(filesDir, "cache"))

        repository = CheatRepository(cache) {
            GitHubCheatProvider(settings.source())
        }

        scanner = GameScanner(
            this,
            storage,
            TitleIdResolver(this)
        )

        ps2Storage = Ps2Storage(this)

        dolphinStorage = DolphinStorage(this)
        dolphinSettings = DolphinSettings(this)

        dolphinScanner = DolphinGameScanner(
            this,
            dolphinStorage
        )

        emulatorManager = EmulatorManager(
            listOf(
                DolphinAdapter(
                    this,
                    dolphinStorage,
                    dolphinScanner
                )
            )
        )

        ps2Settings = Ps2Settings(this)

        ps2Scanner = Ps2GameScanner(
            this,
            ps2Storage
        )

        ps2Repository = Ps2CheatRepository(cache) {
            val selected = ps2Settings.source()

            listOf(
                Ps2CheatProvider(selected)
            ) + Ps2CheatSources.all
                .filter { it.id != selected.id }
                .map { Ps2CheatProvider(it) }
        }

        AppLog.verbose = settings.debugLog
        AppLog.i(
            "App",
            "Started v${BuildConfigVersion.name(this)}"
        )
    }

    /** Installer bound to the current cheats folder, or null if it is not available. */
    fun installer(): CheatInstaller? {
        val store = storage.cheatsStore() ?: return null
        return CheatInstaller(
            store,
            BackupManager(store)
        )
    }

    fun backups(): BackupManager? =
        storage.cheatsStore()?.let {
            BackupManager(it)
        }

    fun ps2Installer(): PnachCheatInstaller? =
        ps2Storage.cheatsStore()?.let {
            PnachCheatInstaller(
                it,
                PnachBackupManager(it)
            )
        }
}

val Context.app: App
    get() = applicationContext as App

object BuildConfigVersion {

    fun name(ctx: Context): String =
        try {
            ctx.packageManager
                .getPackageInfo(ctx.packageName, 0)
                .versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
}

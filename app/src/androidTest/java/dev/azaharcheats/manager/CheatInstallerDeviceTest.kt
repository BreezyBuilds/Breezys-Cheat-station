package dev.azaharcheats.manager

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.azaharcheats.manager.cheats.BackupManager
import dev.azaharcheats.manager.cheats.CheatInstaller
import dev.azaharcheats.manager.cheats.InstallResult
import dev.azaharcheats.manager.model.Cheat
import dev.azaharcheats.manager.storage.DocumentFileStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end instrumentation test: exercises [CheatInstaller] and [BackupManager] on top of a
 * real [DocumentFileStore] writing to actual on-device storage, the same code path used when the
 * app installs cheats into a user-picked Azahar folder.
 */
@RunWith(AndroidJUnit4::class)
class CheatInstallerDeviceTest {

    private lateinit var root: File
    private lateinit var installer: CheatInstaller
    private val titleId = "0004000000123400"

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(ctx.cacheDir, "installer_test_${System.nanoTime()}").apply { mkdirs() }
        val store = DocumentFileStore(ctx, DocumentFile.fromFile(root))
        installer = CheatInstaller(store, BackupManager(store))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun installIntoFreshFileCreatesItWithNoBackup() {
        val cheat = Cheat("Infinite HP", listOf("0A1B2C3D 00000001"), enabled = true)
        val result = installer.install(titleId, listOf(cheat))
        assertTrue(result is InstallResult.Success)
        assertEquals(null, (result as InstallResult.Success).backupName)
        val onDisk = File(root, "$titleId.txt")
        assertTrue(onDisk.exists())
        assertTrue(onDisk.readText().contains("Infinite HP"))
    }

    @Test
    fun secondInstallBacksUpFirstAndKeepsBothCheats() {
        installer.install(titleId, listOf(Cheat("Infinite HP", listOf("0A1B2C3D 00000001"), enabled = true)))
        val result = installer.install(titleId, listOf(Cheat("Max Money", listOf("1A2B3C4D 000F4240"), enabled = true)))
        assertTrue(result is InstallResult.Success)
        val backupName = (result as InstallResult.Success).backupName
        assertTrue(backupName != null)
        assertTrue(File(root, "backups/$backupName").exists())

        val existing = installer.readExisting(titleId)
        val names = existing.cheats.map { it.name }
        assertTrue(names.contains("Infinite HP"))
        assertTrue(names.contains("Max Money"))
    }

    @Test
    fun installingIdenticalContentTwiceCreatesNoExtraBackup() {
        val cheat = Cheat("Infinite HP", listOf("0A1B2C3D 00000001"), enabled = true)
        installer.install(titleId, listOf(cheat))
        val before = File(root, "backups").let { if (it.exists()) it.listFiles()?.size ?: 0 else 0 }
        installer.install(titleId, listOf(cheat))
        val after = File(root, "backups").let { if (it.exists()) it.listFiles()?.size ?: 0 else 0 }
        assertEquals(before, after)
    }

    @Test
    fun removeThenRestoreFromBackupBringsCheatBack() {
        installer.install(titleId, listOf(Cheat("Infinite HP", listOf("0A1B2C3D 00000001"), enabled = true)))
        installer.remove(titleId, setOf("infinite hp"))
        assertTrue(installer.readExisting(titleId).cheats.none { it.key == "infinite hp" })

        val backupManager = BackupManager(DocumentFileStore(
            InstrumentationRegistry.getInstrumentation().targetContext,
            DocumentFile.fromFile(root),
        ))
        val backups = backupManager.list(titleId)
        assertTrue(backups.isNotEmpty())
        backupManager.restore(backups.first())

        assertTrue(installer.readExisting(titleId).cheats.any { it.key == "infinite hp" })
    }
}

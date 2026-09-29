package dev.azaharcheats.manager

import dev.azaharcheats.manager.cheats.BackupManager
import dev.azaharcheats.manager.cheats.CheatInstaller
import dev.azaharcheats.manager.cheats.CheatParser
import dev.azaharcheats.manager.cheats.InstallResult
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date

class BackupAndInstallerTest {
    private val tid = "00040000001B5000"
    private val fixed = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 28, 14, 3, 22) }.time
    private fun incoming(t: String) = CheatParser.parse(t, tid).cheats

    @Test fun backupHasTimestampedNameAndVerifiedContent() {
        val store = InMemoryFileStore()
        val bm = BackupManager(store, { fixed })
        val name = bm.create(tid, "hello")
        assertEquals("${tid}_cheats_backup_20260928_140322.txt", name)
        assertEquals("hello", store.readText("backups/$name"))
        assertEquals(listOf(name), bm.list(tid))
        assertEquals(tid, BackupManager.titleIdOf(name))
    }

    @Test fun backupsCreatedInSameSecondDoNotOverwrite() {
        val bm = BackupManager(InMemoryFileStore(), { fixed })
        val a = bm.create(tid, "one"); val b = bm.create(tid, "two")
        assertNotEquals(a, b)
        assertEquals("one", bm.read(a)); assertEquals("two", bm.read(b))
    }

    @Test fun restoreWritesBackupBackAndKeepsPreviousFile() {
        val store = InMemoryFileStore()
        var t = 0L
        val bm = BackupManager(store, { Date(fixed.time + (t++) * 1000) })
        store.writeText("$tid.txt", "current")
        val name = bm.create(tid, "old")
        assertEquals(tid, bm.restore(name))
        assertEquals("old", store.readText("$tid.txt"))
        assertTrue(bm.list(tid).any { bm.read(it) == "current" })
    }

    @Test fun oldBackupsArePruned() {
        val store = InMemoryFileStore()
        var t = 0L
        val bm = BackupManager(store, { Date(fixed.time + (t++) * 1000) }, keepPerTitle = 3)
        repeat(6) { bm.create(tid, "v$it") }
        assertEquals(3, bm.list(tid).size)
    }

    @Test fun installCreatesFileWithoutBackupWhenNoneExisted() {
        val store = InMemoryFileStore()
        val inst = CheatInstaller(store, BackupManager(store, { fixed }))
        val r = inst.install(tid, incoming("[Infinite HP]\n0A1B2C3D 00000001\n"))
        assertTrue(r is InstallResult.Success)
        assertNull((r as InstallResult.Success).backupName)
        assertEquals(1, CheatParser.parse(store.readText("$tid.txt")!!).cheats.size)
        assertTrue(store.list("backups").isEmpty())
    }

    @Test fun installBacksUpExistingFileAndKeepsUnrelatedCheats() {
        val store = InMemoryFileStore()
        store.writeText("$tid.txt", "[Mine]\n11111111 22222222\n")
        val inst = CheatInstaller(store, BackupManager(store, { fixed }))
        val r = inst.install(tid, incoming("[Infinite HP]\n0A1B2C3D 00000001\n[Mine]\n11111111 22222222\n")) as InstallResult.Success
        assertNotNull(r.backupName)
        assertEquals("[Mine]\n11111111 22222222\n", store.readText("backups/${r.backupName}"))
        val names = CheatParser.parse(store.readText("$tid.txt")!!).cheats.map { it.name }
        assertEquals(listOf("Mine", "Infinite HP"), names)
    }

    @Test fun installingTwiceDoesNotDuplicateOrRewrite() {
        val store = InMemoryFileStore()
        val inst = CheatInstaller(store, BackupManager(store, { fixed }))
        val c = incoming("[Infinite HP]\n0A1B2C3D 00000001\n")
        inst.install(tid, c)
        val r = inst.install(tid, c) as InstallResult.Success
        assertNull(r.backupName)
        assertEquals(1, CheatParser.parse(store.readText("$tid.txt")!!).cheats.size)
    }

    @Test fun failedWriteRestoresPreviousContent() {
        val store = FlakyStore("$tid.txt")
        store.files["$tid.txt"] = "[Mine]\n11111111 22222222\n"
        val inst = CheatInstaller(store, BackupManager(store, { fixed }))
        val r = inst.install(tid, incoming("[New]\n33333333 44444444\n"))
        assertTrue(r is InstallResult.Failure && r.restored)
        assertEquals("[Mine]\n11111111 22222222\n", store.readText("$tid.txt"))
    }

    @Test fun corruptedWriteIsDetectedByVerificationAndRestored() {
        val store = FlakyStore("$tid.txt", corrupt = true)
        store.files["$tid.txt"] = "[Mine]\n11111111 22222222\n"
        val inst = CheatInstaller(store, BackupManager(store, { fixed }))
        val r = inst.install(tid, incoming("[New]\n33333333 44444444\n"))
        assertTrue(r is InstallResult.Failure)
        assertEquals("[Mine]\n11111111 22222222\n", store.readText("$tid.txt"))
    }

    @Test fun failedWriteOfNewFileLeavesNothingBehind() {
        val store = FlakyStore("$tid.txt", corrupt = true)
        val inst = CheatInstaller(store, BackupManager(store, { fixed }))
        assertTrue(inst.install(tid, incoming("[New]\n33333333 44444444\n")) is InstallResult.Failure)
        assertFalse(store.exists("$tid.txt"))
    }

    @Test fun removeAndEnableWorkThroughBackups() {
        val store = InMemoryFileStore()
        store.writeText("$tid.txt", "[A]\n00000001 00000002\n\n[B]\n00000003 00000004\n")
        var t = 0L
        val inst = CheatInstaller(store, BackupManager(store, { Date(fixed.time + (t++) * 1000) }))
        assertTrue(inst.setEnabled(tid, "A", true) is InstallResult.Success)
        assertTrue(store.readText("$tid.txt")!!.contains("[*A]"))
        assertTrue(inst.remove(tid, setOf("B")) is InstallResult.Success)
        assertEquals(listOf("A"), CheatParser.parse(store.readText("$tid.txt")!!).cheats.map { it.name })
        assertEquals(2, store.list("backups").size)
    }
}

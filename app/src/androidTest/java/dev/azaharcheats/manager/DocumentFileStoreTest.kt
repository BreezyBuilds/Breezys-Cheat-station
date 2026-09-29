package dev.azaharcheats.manager

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.azaharcheats.manager.storage.DocumentFileStore
import dev.azaharcheats.manager.storage.StoreException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumentation test for [DocumentFileStore]. Runs on a real device/emulator and exercises
 * actual SAF/DocumentFile plumbing against a real directory on disk (via file:// DocumentFile,
 * which follows the same DocumentFile contract the app uses for user-picked tree URIs).
 */
@RunWith(AndroidJUnit4::class)
class DocumentFileStoreTest {

    private lateinit var root: File
    private lateinit var store: DocumentFileStore

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(ctx.cacheDir, "dfs_test_${System.nanoTime()}").apply { mkdirs() }
        val base = DocumentFile.fromFile(root)
        store = DocumentFileStore(ctx, base)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun writeThenReadRoundTrips() {
        store.writeText("cheats.txt", "[Test]\n0A1B2C3D 00000001\n")
        assertTrue(store.exists("cheats.txt"))
        assertEquals("[Test]\n0A1B2C3D 00000001\n", store.readText("cheats.txt"))
    }

    @Test
    fun writeCreatesNestedFolders() {
        store.writeText("backups/0004000000123400_cheats_backup_20260101_000000.txt", "data")
        assertTrue(store.exists("backups/0004000000123400_cheats_backup_20260101_000000.txt"))
        assertTrue(store.list("backups").contains("0004000000123400_cheats_backup_20260101_000000.txt"))
    }

    @Test
    fun readMissingFileReturnsNull() {
        assertNull(store.readText("nope.txt"))
    }

    @Test
    fun existsFalseForMissingFile() {
        assertFalse(store.exists("missing/nested.txt"))
    }

    @Test
    fun overwriteReplacesContentExactly() {
        store.writeText("f.txt", "first-version-longer-text")
        store.writeText("f.txt", "v2")
        assertEquals("v2", store.readText("f.txt"))
    }

    @Test
    fun deleteRemovesFile() {
        store.writeText("gone.txt", "x")
        assertTrue(store.delete("gone.txt"))
        assertFalse(store.exists("gone.txt"))
        assertNull(store.readText("gone.txt"))
    }

    @Test
    fun listReturnsOnlyDirectChildren() {
        store.writeText("a.txt", "1")
        store.writeText("sub/b.txt", "2")
        val names = store.list("")
        assertTrue(names.contains("a.txt"))
        assertTrue(names.contains("sub"))
        assertFalse(names.contains("b.txt"))
    }

    @Test(expected = StoreException::class)
    fun writingThroughAFileThatIsNotADirectoryThrows() {
        store.writeText("notadir.txt", "x")
        // "notadir.txt" already exists as a plain file; asking to treat it as a folder must fail.
        store.writeText("notadir.txt/child.txt", "y")
    }
}

package dev.azaharcheats.manager

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.azaharcheats.manager.scan.TitleIdResolver
import dev.azaharcheats.manager.util.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TitleIdResolverAndLogTest {

    @Test
    fun fromFileReturnsNullForGarbageInsteadOfThrowing() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val f = File(ctx.cacheDir, "garbage_${System.nanoTime()}.3ds")
        f.writeBytes(ByteArray(64) { it.toByte() }) // not a valid NCSD/CIA header
        val doc = DocumentFile.fromFile(f)
        val resolver = TitleIdResolver(ctx)
        // Must degrade gracefully (null), never crash, on a real content-resolver-backed read.
        assertNull(resolver.fromFile(doc))
        f.delete()
    }

    @Test
    fun resolveFallsBackToFileNameWhenHeaderMissing() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = TitleIdResolver(ctx)
        val (id, usedFileName) = resolver.resolve(null, "Some Game [0004000000123400].3ds")
        assertEquals("0004000000123400", id)
        assertTrue(usedFileName)
    }

    @Test
    fun resolveReturnsNullWhenNeitherHeaderNorFileNameHasAnId() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = TitleIdResolver(ctx)
        val (id, usedFileName) = resolver.resolve(null, "not_a_title_id.3ds")
        assertNull(id)
        assertTrue(usedFileName)
    }

    @Test
    fun appLogRingBufferCapturesEntriesOnRealDevice() {
        AppLog.clear()
        AppLog.i("Test", "hello from instrumentation test")
        val dump = AppLog.dump()
        assertTrue(dump.contains("hello from instrumentation test"))
    }

    @Test
    fun appLogRedactsSecrets() {
        AppLog.clear()
        AppLog.i("Test", "token=ghp_1234567890abcdef1234567890abcdef1234")
        val dump = AppLog.dump()
        assertTrue(!dump.contains("ghp_1234567890abcdef1234567890abcdef1234"))
    }
}

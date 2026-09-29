package dev.azaharcheats.manager

import dev.azaharcheats.manager.cheats.Compatibility
import dev.azaharcheats.manager.util.TitleId
import dev.azaharcheats.manager.util.VersionUtil
import org.junit.Assert.*
import org.junit.Test

class TitleIdAndVersionTest {
    @Test fun normalizeUppercasesValidIds() {
        assertEquals("00040000001B5000", TitleId.normalize("00040000001b5000"))
        assertEquals("00040000001B5000", TitleId.normalize("  0x00040000001B5000 "))
    }

    @Test fun normalizeRejectsInvalidIds() {
        assertNull(TitleId.normalize(null))
        assertNull(TitleId.normalize("1234"))
        assertNull(TitleId.normalize("00040000001B500G"))
        assertNull(TitleId.normalize("00040000001B50001"))
    }

    @Test fun extractFindsIdInFileNameOnlyWhenExactly16Digits() {
        assertEquals("00040000001B5000", TitleId.extract("Pokemon [00040000001b5000] (v1.2).cia"))
        assertNull(TitleId.extract("Pokemon 0004000000.cia"))
        assertNull(TitleId.extract("0004000000000000001B5000.cia"))
    }

    @Test fun fromPartsAndFromLong() {
        assertEquals("00040000001B5000", TitleId.fromParts("00040000", "001b5000"))
        assertEquals("00040000001B5000", TitleId.fromParts("40000", "1B5000"))
        assertEquals("00040000001B5000", TitleId.fromLong(0x00040000001B5000L))
        assertTrue(TitleId.isApplication("00040000001B5000"))
        assertFalse(TitleId.isApplication("0004000E001B5000"))
    }

    @Test fun tmdVersionConversion() {
        assertEquals("1.2", VersionUtil.fromTmd(0x0420))
        assertEquals("1.2.1", VersionUtil.fromTmd(0x0421))
        assertEquals("0.0", VersionUtil.fromTmd(0))
        assertEquals("2.0", VersionUtil.fromTmd(2 shl 10))
    }

    @Test fun versionParsingAndComparison() {
        assertEquals(listOf(1, 2), VersionUtil.parse("v1.2"))
        assertEquals(listOf(1), VersionUtil.parse("1.0.0"))
        assertNull(VersionUtil.parse("abc"))
        assertNull(VersionUtil.parse(null))
        assertTrue(VersionUtil.compare("1.10", "1.2")!! > 0)
        assertEquals(true, VersionUtil.matches("1.2", "v1.2.0"))
        assertEquals(false, VersionUtil.matches("1.2", "1.3"))
        assertNull(VersionUtil.matches(null, "1.0"))
        assertEquals("1.2", VersionUtil.display("v1.2"))
    }

    @Test fun compatibilityMessages() {
        assertEquals(Compatibility.Status.MATCH, Compatibility.check("1.2", "1.2").status)
        assertEquals(Compatibility.Status.MISMATCH, Compatibility.check("1.2", "1.0").status)
        val unknown = Compatibility.check(null, "1.0")
        assertEquals(Compatibility.Status.UNVERIFIED, unknown.status)
        assertTrue(unknown.message!!.contains("could not be verified"))
        assertEquals(Compatibility.Status.UNVERIFIED, Compatibility.check("1.2", null).status)
    }
}

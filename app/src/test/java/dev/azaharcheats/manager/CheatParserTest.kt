package dev.azaharcheats.manager

import dev.azaharcheats.manager.cheats.CheatParser
import org.junit.Assert.*
import org.junit.Test

class CheatParserTest {
    private val sample = """
        # Title: Test Game
        # Version: 1.2
        [Infinite HP]
        0A1B2C3D 00000001
        0A1B2C3E 00000002

        [*Max Money]
        # gives 99999
        D3000000 00000000
        00123456 0001869F
    """.trimIndent()

    @Test fun parsesNamesCodesOrderAndMetadata() {
        val f = CheatParser.parse(sample, "00040000001B5000")
        assertEquals(2, f.cheats.size)
        assertEquals("Infinite HP", f.cheats[0].name)
        assertEquals(listOf("0A1B2C3D 00000001", "0A1B2C3E 00000002"), f.cheats[0].codeLines)
        assertFalse(f.cheats[0].enabled)
        assertEquals("Max Money", f.cheats[1].name)
        assertTrue(f.cheats[1].enabled)
        assertEquals("gives 99999", f.cheats[1].description)
        assertEquals("1.2", f.version)
        assertEquals("Test Game", f.title)
        assertTrue(f.warnings.isEmpty())
    }

    @Test fun acceptsAlternativeHeaderStyles() {
        val f = CheatParser.parse("*[Star]\n00000001 00000002\n{Brace}\n00000003 00000004\n", null)
        assertEquals(listOf("Star", "Brace"), f.cheats.map { it.name })
        assertTrue(f.cheats[0].enabled)
    }

    @Test fun serializeRoundTripKeepsHexUntouched() {
        val f = CheatParser.parse(sample)
        val text = CheatParser.serialize(f)
        val again = CheatParser.parse(text)
        assertEquals(f.cheats.map { it.name to it.codeLines }, again.cheats.map { it.name to it.codeLines })
        assertEquals(f.cheats.map { it.enabled }, again.cheats.map { it.enabled })
        assertTrue(text.contains("0A1B2C3D 00000001"))
        assertTrue(text.contains("[*Max Money]"))
        assertTrue(text.contains("[Infinite HP]"))
        assertTrue(text.endsWith("\n"))
    }

    @Test fun generatedFileMatchesExpectedText() {
        val f = CheatParser.parse("[A]\n00000001 00000002\n[B]\n00000003 00000004\n")
        assertEquals("[A]\n00000001 00000002\n\n[B]\n00000003 00000004\n", CheatParser.serialize(f))
    }

    @Test fun malformedInputNeverThrowsAndIsReported() {
        val f = CheatParser.parse("garbage\n[Bad]\nnot a code\n[Good]\nAABBCCDD 11223344\n[Empty]\n\u0000\u0001\n")
        assertEquals(listOf("Good"), f.cheats.map { it.name })
        assertTrue(f.warnings.isNotEmpty())
    }

    @Test fun binaryAndEmptyInputAreSafe() {
        assertTrue(CheatParser.parse("").cheats.isEmpty())
        assertTrue(CheatParser.parse("\uFEFF").cheats.isEmpty())
        assertTrue(CheatParser.parse("]]][[[{{{}}}\n*\n[]\n[ ]\n").cheats.isEmpty())
        assertTrue(CheatParser.parse("\r\n\r\n\r\n").cheats.isEmpty())
    }

    @Test fun handlesWindowsLineEndingsAndBom() {
        val f = CheatParser.parse("\uFEFF[One]\r\n00000001 00000002\r\n[Two]\r\n00000003 00000004\r\n")
        assertEquals(listOf("One", "Two"), f.cheats.map { it.name })
    }

    @Test fun preserveInvalidKeepsUnknownLinesInExistingFiles() {
        val text = "[Custom]\n00000001 00000002\nweird line\n"
        val f = CheatParser.parse(text, preserveInvalid = true)
        assertEquals(1, f.cheats.size)
        assertTrue(CheatParser.serialize(f).contains("weird line"))
        assertTrue(f.warnings.isNotEmpty())
    }

    @Test fun duplicateNamesInOneFileAreReported() {
        val f = CheatParser.parse("[X]\n00000001 00000002\n[x]\n00000003 00000004\n")
        assertEquals(2, f.cheats.size)
        assertTrue(f.warnings.any { it.contains("Duplicate") })
    }

    @Test fun codeLineDetection() {
        assertTrue(CheatParser.isCodeLine("0A1B2C3D 00000001"))
        assertTrue(CheatParser.isCodeLine("0A1B2C3D  00000001 FFFFFFFF"))
        assertFalse(CheatParser.isCodeLine("0A1B2C3D"))
        assertFalse(CheatParser.isCodeLine("ZZZZZZZZ 00000001"))
    }
}

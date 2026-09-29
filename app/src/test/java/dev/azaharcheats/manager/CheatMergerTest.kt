package dev.azaharcheats.manager

import dev.azaharcheats.manager.cheats.CheatMerger
import dev.azaharcheats.manager.cheats.CheatParser
import org.junit.Assert.*
import org.junit.Test

class CheatMergerTest {
    private fun file(t: String) = CheatParser.parse(t, "00040000001B5000", preserveInvalid = true)

    @Test fun identicalCheatIsNotDuplicated() {
        val existing = file("[Infinite HP]\n0A1B2C3D 00000001\n")
        val incoming = file("[Infinite HP]\n0a1b2c3d   00000001\n").cheats
        val r = CheatMerger.merge(existing, incoming)
        assertEquals(1, r.file.cheats.size)
        assertEquals(listOf("Infinite HP"), r.identical)
        assertTrue(r.added.isEmpty() && r.conflicts.isEmpty())
    }

    @Test fun differentCodeIsReportedAsConflictAndExistingKept() {
        val existing = file("[Infinite HP]\n0A1B2C3D 00000001\n")
        val incoming = file("[infinite hp]\n0A1B2C3D 00000009\n").cheats
        val r = CheatMerger.merge(existing, incoming)
        assertEquals(1, r.conflicts.size)
        assertEquals("0A1B2C3D 00000001", r.file.cheats.single().code)
    }

    @Test fun conflictCanBeReplacedAndKeepsEnabledState() {
        val existing = file("[*Infinite HP]\n0A1B2C3D 00000001\n")
        val incoming = file("[Infinite HP]\n0A1B2C3D 00000009\n").cheats
        val r = CheatMerger.merge(existing, incoming, setOf("infinite hp"))
        assertEquals(listOf("Infinite HP"), r.replaced)
        assertEquals("0A1B2C3D 00000009", r.file.cheats.single().code)
        assertTrue(r.file.cheats.single().enabled)
    }

    @Test fun newCheatsAreAppendedEnabledAndUnrelatedEntriesUntouched() {
        val existing = file("# my notes\n[Mine]\n11111111 22222222\nweird\n")
        val incoming = file("[New]\n33333333 44444444\n").cheats
        val r = CheatMerger.merge(existing, incoming)
        val text = CheatParser.serialize(r.file)
        assertEquals(listOf("Mine", "New"), r.file.cheats.map { it.name })
        assertTrue(text.contains("# my notes"))
        assertTrue(text.contains("weird"))
        assertTrue(r.file.cheats[1].enabled)
        assertEquals(1, Regex("\\[Mine]").findAll(text).count())
    }

    @Test fun duplicateNamesInSourceAreCollapsed() {
        val incoming = file("[Dup]\n00000001 00000002\n[Dup]\n00000003 00000004\n").cheats
        val r = CheatMerger.merge(file(""), incoming)
        assertEquals(1, r.file.cheats.size)
        assertEquals("00000001 00000002", r.file.cheats.single().code)
        assertTrue(r.notes.isNotEmpty())
    }

    @Test fun removeAndToggleOnlyTouchNamedEntries() {
        val f = file("[A]\n00000001 00000002\n[B]\n00000003 00000004\n[C]\n00000005 00000006\n")
        val removed = CheatMerger.remove(f, setOf("b"))
        assertEquals(listOf("A", "C"), removed.cheats.map { it.name })
        val toggled = CheatMerger.setEnabled(f, "C", true)
        assertEquals(listOf(false, false, true), toggled.cheats.map { it.enabled })
    }
}

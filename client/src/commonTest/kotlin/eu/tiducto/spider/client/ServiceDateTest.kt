package eu.tiducto.spider.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceDateTest {

    // Europe/Prague serviceDay values ("noon minus 12h" local), including both DST switch days.
    @Test
    fun `serviceDay maps to its own service date across DST`() {
        assertEquals("2026-09-28", serviceDateOf(1_790_546_400)) // CEST
        assertEquals("2026-12-01", serviceDateOf(1_796_079_600)) // CET
        assertEquals("2026-03-29", serviceDateOf(1_774_735_200)) // spring forward
        assertEquals("2026-10-25", serviceDateOf(1_792_882_800)) // fall back
    }

    @Test
    fun `only real YYYY-MM-DD dates are service dates`() {
        assertTrue(isServiceDate("2026-09-28"))
        assertTrue(isServiceDate("2028-02-29"))
        assertFalse(isServiceDate("20260928"))
        assertFalse(isServiceDate("2026-9-28"))
        assertFalse(isServiceDate("2026-02-30"))
        assertFalse(isServiceDate("2026-13-01"))
        assertFalse(isServiceDate("2026-09-28T00:00:00Z"))
        assertFalse(isServiceDate(""))
    }
}

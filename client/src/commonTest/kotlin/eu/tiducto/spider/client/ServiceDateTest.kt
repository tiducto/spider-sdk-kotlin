package eu.tiducto.spider.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceDateTest {

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

package eu.tiducto.spider.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the fail-fast contract guard. Test names avoid `()` and other Kotlin/Native-forbidden chars in
 * backtick identifiers (see commit ef80981) — commonTest runs on every target, Native included.
 */
class ContractGuardTest {

    @Test
    fun `no header from the gateway is a no-op - guard stays dormant`() {
        // Today's gateway does not declare the version; the guard must not fire.
        ContractGuard.check(null)
    }

    @Test
    fun `matching version passes`() {
        ContractGuard.check(SpiderContract.VERSION)
    }

    @Test
    fun `compatible minor or patch bump passes`() {
        val major = SpiderContract.VERSION.substringBefore('.')
        ContractGuard.check("$major.99.99")
    }

    @Test
    fun `incompatible major version crashes with a mismatch error`() {
        val nextMajor = (SpiderContract.VERSION.substringBefore('.').toInt() + 1).toString()
        val error = assertFailsWith<SpiderContractMismatchError> {
            ContractGuard.check("$nextMajor.0.0")
        }
        assertEquals(SpiderContract.VERSION, error.expected)
        assertEquals("$nextMajor.0.0", error.actual)
    }

    @Test
    fun `the mismatch error is a fatal Error not an Exception so it bypasses SpiderResult`() {
        // The per-call runCatching blocks only catch Exception; this must be an Error so it propagates
        // and crashes instead of being wrapped in SpiderResult.Error.
        val error: Error = SpiderContractMismatchError(expected = "1.0.0", actual = "2.0.0")
        assertFailsWith<Error> { throw error }
    }
}

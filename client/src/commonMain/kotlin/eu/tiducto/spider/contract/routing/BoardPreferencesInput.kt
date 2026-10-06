package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Boarding preferences.
 */
@Serializable
internal data class BoardPreferencesInput(
    /** How much worse waiting at a stop is than riding for the same time, a multiplier from 0.1 to 100000; rejected, never clamped. */
    val waitReluctance: Double? = null,
    /** Least time at the stop before boarding, as an ISO-8601 duration: `PT0S` to `PT1H`; rejected, never clamped. */
    val slack: String? = null
)

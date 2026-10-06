package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Alighting preferences.
 */
@Serializable
internal data class AlightPreferencesInput(
    /** Least time needed to alight, as an ISO-8601 duration: `PT0S` to `PT1H`; rejected, never clamped. */
    val slack: String? = null
)

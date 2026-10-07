package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Walking preferences.
 */
@Serializable
internal data class WalkPreferencesInput(
    /** Walking speed on flat ground in metres per second, at least 0.1 (0.1 included); rejected, never clamped. */
    val speed: Double? = null,
    /** How much worse walking is than riding for the same time, a multiplier from 0.1 to 100000; rejected, never clamped. */
    val reluctance: Double? = null,
    /** Generalized cost added for each boarding, an integer from 0 to 1000000; rejected, never clamped. */
    val boardCost: Int? = null
)

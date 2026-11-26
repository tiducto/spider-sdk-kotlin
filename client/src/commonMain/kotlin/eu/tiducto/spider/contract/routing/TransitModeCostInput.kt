package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * The reluctance of one transit mode, a Spider extension.
 */
@Serializable
internal data class TransitModeCostInput(
    val mode: TransitMode,
    /** Multiplier on the time spent riding this mode, from 0.1 to 100000: above 1 avoids the mode, below 1 favours it; rejected, never clamped. */
    val reluctance: Double
)

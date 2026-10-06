package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Which modes the search may use.
 */
@Serializable
internal data class PlanModesInput(
    /** Only a direct walk, without transit. */
    val directOnly: Boolean? = null,
    /** Never a journey without a transit leg. */
    val transitOnly: Boolean? = null,
    val transit: PlanTransitModesInput? = null
)

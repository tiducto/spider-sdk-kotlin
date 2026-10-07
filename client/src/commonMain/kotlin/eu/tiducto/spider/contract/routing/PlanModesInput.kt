package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Which modes the search may use. `directOnly` and `transitOnly` together are a 400 `modes is invalid`.
 */
@Serializable
internal data class PlanModesInput(
    /** Only a direct walk, without transit. A direct-only plan has no pages: its cursors are null. */
    val directOnly: Boolean? = null,
    /** Never a journey without a transit leg. */
    val transitOnly: Boolean? = null,
    val transit: PlanTransitModesInput? = null
)

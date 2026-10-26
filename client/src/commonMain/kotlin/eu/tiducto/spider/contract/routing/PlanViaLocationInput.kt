package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Exactly one of `passThrough`, `visit`; neither or both is a 400 `via is invalid`.
 */
@Serializable
internal data class PlanViaLocationInput(
    /** The journey passes the location: on board, alighting there, or starting there. A stop where the vehicle does not let passengers off is not passed on board, and walking there to board is not a pass. */
    val passThrough: PlanPassThroughViaLocationInput? = null,
    /** The journey stops at the location: it alights there and boards again after `minimumWaitTime`. */
    val visit: PlanVisitViaLocationInput? = null
)

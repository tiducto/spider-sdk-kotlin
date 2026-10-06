package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Exactly one of `passThrough`, `visit`.
 */
@Serializable
internal data class PlanViaLocationInput(
    /** The journey passes the location, on board or by changing vehicles there. */
    val passThrough: PlanPassThroughViaLocationInput? = null,
    /** The journey stops at the location: it alights there and boards again after `minimumWaitTime`. */
    val visit: PlanVisitViaLocationInput? = null
)

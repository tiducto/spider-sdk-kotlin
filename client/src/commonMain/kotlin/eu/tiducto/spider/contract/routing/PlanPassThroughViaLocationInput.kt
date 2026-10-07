package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A location the journey passes.
 */
@Serializable
internal data class PlanPassThroughViaLocationInput(
    /** 1 to 10 feed-prefixed stop or station ids; passing any one of them is enough. Absent is a 400 `via.passThrough.stopLocationIds is required`, and empty or more than 10 `via.passThrough.stopLocationIds is out of range`. */
    val stopLocationIds: List<String>
)

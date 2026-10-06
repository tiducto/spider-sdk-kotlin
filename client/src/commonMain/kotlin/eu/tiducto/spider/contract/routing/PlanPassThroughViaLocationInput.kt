package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A location the journey passes.
 */
@Serializable
internal data class PlanPassThroughViaLocationInput(
    /** 1 to 10 feed-prefixed stop or station ids; passing any one of them is enough. More, or none, is a 400 naming `via`. */
    val stopLocationIds: List<String>
)

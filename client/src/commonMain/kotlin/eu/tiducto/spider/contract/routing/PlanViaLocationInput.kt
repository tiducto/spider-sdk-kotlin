package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * Exactly one of `passThrough`, `visit`; neither or both is a 400 `via is invalid`.
 */
@Serializable
internal data class PlanViaLocationInput(
    /** The journey passes the location, on board or by changing vehicles there. */
    val passThrough: kotlinx.serialization.json.JsonElement? = null,
    /** The journey stops at the location: it alights there and boards again after `minimumWaitTime`. */
    val visit: kotlinx.serialization.json.JsonElement? = null
)

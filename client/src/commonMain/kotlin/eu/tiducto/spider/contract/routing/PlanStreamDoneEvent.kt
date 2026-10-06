package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * The search work this stream used; ends the stream. `stoppedBy` is `targetResults` (enough itineraries sent), `maxWindow` (the whole window searched) or `rejected` (a declined plan); new values may be added.
 */
@Serializable
internal data class PlanStreamDoneEvent(
    /** Search iterations used. */
    val iterations: Int,
    /** Seconds of the window searched. */
    val windowSeconds: Int,
    /** Itineraries sent. */
    val resultCount: Int,
    /** Why the stream stopped. */
    val stoppedBy: String
)

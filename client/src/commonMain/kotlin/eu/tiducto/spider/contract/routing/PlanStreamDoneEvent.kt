package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * The search work this stream used; ends the stream. `stoppedBy` is `targetResults` (enough itineraries sent), `maxWindow` (the whole window searched), `directOnly` (a direct-only plan, which searches no transit) or `rejected` (a declined plan, one with no stops in range or no transit connection included); new values may be added.
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

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Sent once, after the last `chunk`. To continue, send `endCursor` as `after` or `startCursor` as `before` in a new request.
 */
@Serializable
internal data class PlanStreamPageInfoEvent(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    /** Why the plan was declined; empty when it was not. */
    val routingErrors: List<RoutingError>,
    /** Send as `before` for earlier itineraries; null when there is none. */
    val startCursor: String? = null,
    /** Send as `after` for later itineraries; null when there is none. */
    val endCursor: String? = null,
    /** The window the stream searched, as an ISO-8601 duration; null for a declined plan. */
    val searchWindowUsed: String? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Sent once, after the last `chunk`. To continue, send `endCursor` as `after` or `startCursor` as `before` in a new request.
 */
@Serializable
internal data class PlanStreamPageInfoEvent(
    /** Send as `before` for earlier itineraries; null when there is none, as for a declined, direct-only or unroutable plan. */
    val startCursor: String?,
    /** Send as `after` for later itineraries; null when there is none, as for a declined, direct-only or unroutable plan. */
    val endCursor: String?,
    /** True exactly when `endCursor` is present. */
    val hasNextPage: Boolean,
    /** True exactly when `startCursor` is present. */
    val hasPreviousPage: Boolean,
    /** The window the stream searched, as an ISO-8601 duration; null when no transit search ran, as for a declined, direct-only or unroutable plan. */
    val searchWindowUsed: String?,
    /** Why the plan was declined; empty when it was not. */
    val routingErrors: List<RoutingError>
)

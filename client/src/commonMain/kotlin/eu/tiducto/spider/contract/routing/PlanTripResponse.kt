package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One page of itineraries. An empty `itineraries` with empty `routingErrors` means the search ran and found nothing in the window: page on with `after` or widen `searchWindow`. A plan the router declines has no itineraries and the reason in `routingErrors`.
 */
@Serializable
internal data class PlanTripResponse(
    val itineraries: List<Itinerary>,
    val pageInfo: PlanPageInfo,
    /** Why the plan was declined; empty when it was not. */
    val routingErrors: List<RoutingError>,
    /** The date-time the search started from. */
    val searchDateTime: String? = null
)

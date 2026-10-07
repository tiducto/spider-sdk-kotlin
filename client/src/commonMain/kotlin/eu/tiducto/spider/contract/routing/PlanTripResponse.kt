package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One page of itineraries. An empty `itineraries` with empty `routingErrors` means the search ran and found nothing in the window. A plan the router declines has the reason in `routingErrors`; with `NO_STOPS_IN_RANGE` or `NO_TRANSIT_CONNECTION`, `itineraries` holds the direct walk when one exists, and is empty otherwise.
 */
@Serializable
internal data class PlanTripResponse(
    val itineraries: List<Itinerary>,
    val pageInfo: PlanPageInfo,
    /** Why the plan was declined; empty when it was not. */
    val routingErrors: List<RoutingError>,
    /** The date-time the search started from; with a cursor, the cursor's. */
    val searchDateTime: String
)

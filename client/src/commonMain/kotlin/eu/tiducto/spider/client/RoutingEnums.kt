package eu.tiducto.spider.client

import kotlinx.serialization.Serializable

// These enums are open: a value the API adds later decodes to UNKNOWN, and a minor SDK release may add
// entries, so keep an `else` branch when matching on them.

/** The realtime state of a leg or departure. */
@Serializable
enum class RealtimeState {
    SCHEDULED,
    UPDATED,
    CANCELED,
    ADDED,
    MODIFIED,
    UNKNOWN,
}

/** Why routing returned no (or fewer) itineraries. */
enum class RoutingErrorCode {
    LOCATION_NOT_FOUND,
    NO_STOPS_IN_RANGE,
    NO_TRANSIT_CONNECTION,
    NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW,
    OUTSIDE_BOUNDS,
    OUTSIDE_SERVICE_PERIOD,
    WALKING_BETTER_THAN_TRANSIT,
    UNKNOWN,
}

/** The plan input a [RoutingError] refers to. */
enum class InputField {
    DATE_TIME,
    FROM,
    TO,
    VIA,
    UNKNOWN,
}

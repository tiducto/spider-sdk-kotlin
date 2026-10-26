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
    /**
     * A stop id resolves to no stop or station ([InputField.FROM], [InputField.TO] or [InputField.VIA]), or a
     * coordinate has no street near enough to link to ([InputField.FROM] or [InputField.TO]).
     */
    LOCATION_NOT_FOUND,

    /** A direct-only plan found no walk (no [RoutingError.inputField]). */
    NO_DIRECT_MODE_CONNECTION,

    /** No stop is in walking range of the origin ([InputField.FROM]) or the destination ([InputField.TO]). */
    NO_STOPS_IN_RANGE,

    /**
     * The origin and the destination lie in parts of the transit network that no trip connects (no
     * [RoutingError.inputField]).
     */
    NO_TRANSIT_CONNECTION,

    /**
     * The transit search ran but found no transit itinerary leaving inside the search window
     * ([InputField.DATE_TIME]).
     */
    NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW,

    /** A coordinate lies outside the area the router covers ([InputField.FROM] or [InputField.TO]). */
    OUTSIDE_BOUNDS,

    /** The date is outside what the feed covers ([InputField.DATE_TIME]). */
    OUTSIDE_SERVICE_PERIOD,

    /**
     * The origin and destination are the same stop, a station and one of its own platforms, identical
     * coordinates or coordinates that snap to the same street point, or every transit itinerary the search found
     * was dropped as worse than walking (no [RoutingError.inputField]).
     */
    WALKING_BETTER_THAN_TRANSIT,

    /** A code this SDK version doesn't recognise. */
    UNKNOWN,
}

/** The plan input a [RoutingError] refers to. */
enum class InputField {
    /** The requested departure or arrival time. */
    DATE_TIME,

    /** The origin. */
    FROM,

    /** The destination. */
    TO,

    /** A via location, such as a via stop id the environment doesn't know. */
    VIA,

    UNKNOWN,
}

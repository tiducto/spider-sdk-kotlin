package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * POST body for `/routing/v1/plan`: one page of itineraries. Paging: `before` and `after` are exclusive. Use `first` with `after` or with no cursor, and `last` only with `before`. The next page is the same body plus `after` (the page's `pageInfo.endCursor`), sized by `first`. The previous page is the same body without `first` and `after`, plus `before` (`pageInfo.startCursor`), sized by `last`. Any other pairing is a 400 naming the member that breaks it. A key not listed here, at any depth, is a 400 `<path> is not allowed`. Every bound is rejected, never clamped. `null` on an optional member means absent.
 */
@Serializable
internal data class PlanTripRequest(
    val dateTime: PlanDateTimeInput,
    /** Where the journey starts. An unknown stop id is a 200 with the `routingErrors` code `LOCATION_NOT_FOUND` on `FROM`. */
    val origin: PlanLabeledLocationInput,
    /** Where the journey ends. An unknown stop id is a 200 with the `routingErrors` code `LOCATION_NOT_FOUND` on `TO`. */
    val destination: PlanLabeledLocationInput,
    /** How much time after `dateTime` the search covers (before it, for `latestArrival`), as an ISO-8601 duration such as `PT2H`: above zero and at most the environment's search-window limit; rejected, never clamped. */
    val searchWindow: String,
    /** Locations the journey must visit or pass through, in the order given. How many a request takes is an environment setting, and an environment set to 0 has via turned off; more is a 400 naming `via`. An unknown via stop id is a 200 with the `routingErrors` code `LOCATION_NOT_FOUND` on `VIA`. */
    val via: List<PlanViaLocationInput>? = null,
    val modes: PlanModesInput? = null,
    val preferences: PlanPreferencesInput? = null,
    /** Itineraries on this page: 1 up to the environment's itinerary limit; rejected, never clamped. Absent means the limit. With `after` or no cursor, never with `before`. */
    val first: Int? = null,
    /** Itineraries on the previous page: 1 up to the environment's itinerary limit; rejected, never clamped. Absent means the limit. Only with `before`. */
    val last: Int? = null,
    /** `pageInfo.startCursor` of a page, to fetch the page before it. Never with `after`. */
    val before: String? = null,
    /** `pageInfo.endCursor` of a page, to fetch the page after it. Never with `before`. */
    val after: String? = null,
    /** Delay-aware planning level; omitted or null plans on the timetable alone. */
    val reliability: Reliability? = null
)

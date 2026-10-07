package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * POST body for `/routing/v1/plan-stream`: the Plan Trip search, streamed. Instead of a fixed window, the stream widens its search until it has sent at least `targetResults` itineraries or has searched `maxWindow`. To continue, send the `pageInfo` event's `endCursor` as `after` (later) or its `startCursor` as `before` (earlier); `before` and `after` are exclusive. A cursor carries the search it continues: it overrides `dateTime` (whether `earliestDeparture` or `latestArrival`) and the sort; `dateTime` stays required and is still checked. A key not listed here, at any depth, is a 400 `<path> is not allowed`, whatever its value, null included. Every bound is rejected, never clamped. `null` on an optional member means absent, and on a required member is a 400 `<path> is required`.
 */
@Serializable
internal data class PlanStreamRequest(
    val dateTime: PlanDateTimeInput,
    /** Where the journey starts. A stop id that resolves to no stop or station, a bare or foreign-prefixed one included, is a 200 with the `routingErrors` code `LOCATION_NOT_FOUND` on `FROM`. */
    val origin: PlanLabeledLocationInput,
    /** Where the journey ends. A stop id that resolves to no stop or station, a bare or foreign-prefixed one included, is a 200 with the `routingErrors` code `LOCATION_NOT_FOUND` on `TO`. */
    val destination: PlanLabeledLocationInput,
    /** The stream stops once it has sent at least this many itineraries; the final flush may send more. 1 up to the environment's itinerary limit; rejected, never clamped. */
    val targetResults: Int,
    /** The stream stops once it has searched this far past `dateTime` (before it, for `latestArrival`), on every path, a cursor's included. An ISO-8601 duration in whole minutes: at least `PT2H` and at most the environment's search-window limit; a value that is not a whole number of minutes is a 400 `maxWindow is invalid`. Rejected, never clamped. */
    val maxWindow: String,
    /** Locations the journey must visit or pass through, in the order given, all of one kind: every entry `visit` or every entry `passThrough`; mixing them is a 400 `via is invalid`. How many a request takes is an environment setting, and an environment set to 0 has via turned off; more is a 400 `via is out of range`. A via stop id that resolves to no stop or station is a 200 with the `routingErrors` code `LOCATION_NOT_FOUND` on `VIA`. */
    val via: List<PlanViaLocationInput>? = null,
    val modes: PlanModesInput? = null,
    val preferences: PlanPreferencesInput? = null,
    /** `pageInfo.startCursor` of a page, to fetch the page before it. Never with `after`. An `endCursor` here is a 400 `before is invalid`. */
    val before: String? = null,
    /** `pageInfo.endCursor` of a page, to fetch the page after it. Never with `before`. A `startCursor` here is a 400 `after is invalid`. */
    val after: String? = null,
    /** Delay-aware planning level; omitted or null plans on the timetable alone. */
    val reliability: Reliability? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * POST body for `/routing/v1/trip`. A key not listed here is a 400 `<key> is not allowed`, whatever its value, null included. `null` on an optional member means absent, and on a required member is a 400 `<path> is required`.
 */
@Serializable
internal data class TripRequest(
    /** Feed-prefixed trip id (`<feedId>:<id>`). A bare or foreign-prefixed id resolves to nothing, and `trip` is null. */
    val id: String,
    /** The trip's GTFS service date, `YYYY-MM-DD` (`YYYYMMDD` also works). Absent or null is a 400 `serviceDate is required`, and a value that is not a real date a 400 `serviceDate is invalid`. */
    val serviceDate: String,
    /** The level `typicalDelay` is given at; the times do not depend on it. Omitted or null, `typicalDelay` is null. A value outside `Reliability` is a 400 `reliability is invalid`. */
    val reliability: Reliability? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * POST body for `/routing/trip`. A key not listed here is a 400 `<key> is not allowed`.
 */
@Serializable
internal data class TripRequest(
    /** Feed-prefixed trip id (`<feedId>:<id>`). */
    val id: String,
    /** The service date, `YYYY-MM-DD` (`YYYYMMDD` also works). Absent means today in the feed's time zone. A value that is not a real date is a 400 naming `serviceDate`. */
    val serviceDate: String? = null
)

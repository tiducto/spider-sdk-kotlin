package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * The station a stop belongs to (GTFS `parent_station`, a `location_type` 1 stop). Its `gtfsId` is a station id that plan's `stopLocationId` and departures' `id` accept.
 */
@Serializable
internal data class ParentStation(
    val gtfsId: String,
    val name: String
)

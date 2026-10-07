package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * `trip` is null for an id that resolves to no trip.
 */
@Serializable
internal data class TripResponse(
    val trip: TripTimetable?
)

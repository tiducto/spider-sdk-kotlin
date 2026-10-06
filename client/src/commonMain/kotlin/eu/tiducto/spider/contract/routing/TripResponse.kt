package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * `trip` is null for an unknown id.
 */
@Serializable
internal data class TripResponse(
    val trip: TripTimetable? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * `trip` is null for an id that resolves to no trip.
 */
@Serializable
internal data class TripResponse(
    val trip: kotlinx.serialization.json.JsonElement
)

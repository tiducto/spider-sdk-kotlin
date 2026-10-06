package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Progress, with the itineraries that became final since the previous `chunk`.
 */
@Serializable
internal data class PlanStreamChunkEvent(
    /** Seconds of the window searched so far. */
    val frontier: Int,
    /** Itineraries found so far. */
    val found: Int,
    /** Itineraries sent so far, this chunk's included. */
    val finalized: Int,
    /** Itineraries that became final with this chunk. */
    val results: List<Itinerary>
)

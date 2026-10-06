package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A stop or a station.
 */
@Serializable
internal data class PlanStopLocationInput(
    /** Feed-prefixed id of a stop or station (`<feedId>:<id>`). */
    val stopLocationId: String
)

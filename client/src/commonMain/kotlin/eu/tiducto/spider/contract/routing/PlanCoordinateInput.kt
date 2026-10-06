package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A WGS84 point.
 */
@Serializable
internal data class PlanCoordinateInput(
    /** Latitude in degrees. */
    val latitude: Double,
    /** Longitude in degrees. */
    val longitude: Double
)

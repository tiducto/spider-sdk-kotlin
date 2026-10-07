package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A WGS84 point.
 */
@Serializable
internal data class PlanCoordinateInput(
    /** Latitude in degrees, -90 to 90; rejected, never clamped. */
    val latitude: Double,
    /** Longitude in degrees, -180 to 180; rejected, never clamped. */
    val longitude: Double
)

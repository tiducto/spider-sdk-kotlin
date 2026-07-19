package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class Leg(
    val start: LegTime,
    val end: LegTime,
    val from: Place,
    val to: Place,
    val mode: Mode? = null,
    val route: PlanConnectionRoute? = null,
    val headsign: String? = null,
    val distance: Double? = null,
    val duration: Double? = null,
    val accessibilityScore: Double? = null,
    val trip: PlanConnectionTrip? = null,
    val legGeometry: PlanConnectionGeometry? = null
)

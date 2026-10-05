package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesStoptime(
    val serviceDay: Long? = null,
    val scheduledDeparture: Int? = null,
    val realtimeDeparture: Int? = null,
    val realtime: Boolean? = null,
    val realtimeState: RealtimeState? = null,
    /** Typical (p50) delay at this stop in seconds for this trip on the service date's day type; null when unknown. */
    val typicalDelay: Int? = null,
    val headsign: String? = null,
    val stop: StopDeparturesStop? = null,
    val trip: StopDeparturesTrip? = null
)

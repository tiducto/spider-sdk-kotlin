package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class TripStoptime(
    val serviceDay: Long? = null,
    val scheduledArrival: Int? = null,
    val scheduledDeparture: Int? = null,
    val realtimeArrival: Int? = null,
    val realtimeDeparture: Int? = null,
    val realtime: Boolean? = null,
    val realtimeState: RealtimeState? = null,
    val stop: TripStop? = null
)

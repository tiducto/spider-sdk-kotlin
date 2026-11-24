package eu.tiducto.spider.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

fun SpiderRealtime.pollVehicles(
    serviceDate: String,
    tripIds: List<String>,
    interval: Duration = 15.seconds,
): Flow<SpiderResult<VehiclePositions>> = poll(interval) { vehicles(serviceDate, tripIds) }

fun SpiderRealtime.pollVehicleForTrip(
    tripId: String,
    serviceDate: String,
    interval: Duration = 15.seconds,
    startTime: String? = null,
): Flow<SpiderResult<LiveVehicleUpdate>> = poll(interval) { vehicleForTrip(tripId, serviceDate, startTime) }

fun SpiderRealtime.pollTripUpdates(
    serviceDate: String,
    tripIds: List<String>,
    interval: Duration = 15.seconds,
): Flow<SpiderResult<TripUpdates>> = poll(interval) { tripUpdates(serviceDate, tripIds) }

@Deprecated(
    "Use pollTripUpdates; /realtime/v1/delays retires no earlier than 2027-05-24.",
    ReplaceWith("pollTripUpdates(serviceDate, tripIds, interval)"),
)
fun SpiderRealtime.pollDelays(
    serviceDate: String,
    tripIds: List<String>,
    interval: Duration = 15.seconds,
): Flow<SpiderResult<TripDelays>> = poll(interval) {
    @Suppress("DEPRECATION")
    delays(serviceDate, tripIds)
}

fun SpiderRealtime.pollAlerts(
    interval: Duration = 30.seconds,
): Flow<SpiderResult<ServiceAlerts>> = poll(interval) { alerts() }

private fun <T> poll(
    interval: Duration,
    fetch: suspend () -> SpiderResult<T>,
): Flow<SpiderResult<T>> = flow {
    while (true) {
        emit(fetch())
        delay(interval)
    }
}.distinctUntilChanged()

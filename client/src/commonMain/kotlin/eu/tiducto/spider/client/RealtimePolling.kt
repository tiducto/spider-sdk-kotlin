package eu.tiducto.spider.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

fun SpiderRealtime.pollVehicles(
    tripIds: List<String>,
    interval: Duration = 15.seconds,
): Flow<SpiderResult<VehiclePositions>> = poll(interval) { vehicles(tripIds) }

fun SpiderRealtime.pollVehicleForTrip(
    tripId: String,
    interval: Duration = 15.seconds,
): Flow<SpiderResult<LiveVehicleUpdate>> = poll(interval) { vehicleForTrip(tripId) }

fun SpiderRealtime.pollDelays(
    tripIds: List<String>,
    interval: Duration = 15.seconds,
): Flow<SpiderResult<TripDelays>> = poll(interval) { delays(tripIds) }

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

package examples.realtime

import eu.tiducto.spider.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun updateBoard(data: Any?) {}

private fun log(message: String) {}

fun setup() {
    val client = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = "your-api-key",
    ) {
        install(Realtime)
    }
}

suspend fun CoroutineScope.poll(client: SpiderClient, tripIds: List<String>) {
    while (isActive) {
        when (val result = client.realtime.delays(tripIds)) {
            is SpiderResult.Success -> updateBoard(result.data)
            is SpiderResult.Error -> log("realtime poll failed: ${result.error}")
        }
        delay(15_000)
    }
}

suspend fun vehicles(client: SpiderClient, tripIds: List<String>) {
    when (val result = client.realtime.vehicles(tripIds)) {
        is SpiderResult.Success ->
            result.data.vehicles.forEach { vehicle ->
                println("${vehicle.tripId} at ${vehicle.latitude}, ${vehicle.longitude}")
            }
        is SpiderResult.Error ->
            println("Failed to load vehicles: ${result.error}")
    }
}

suspend fun vehicleForTrip(client: SpiderClient, tripId: String) {
    when (val result = client.realtime.vehicleForTrip(tripId)) {
        is SpiderResult.Success -> {
            val vehicle = result.data.vehicle
            if (vehicle == null) {
                println("No vehicle reporting for this trip right now")
            } else {
                println("At ${vehicle.latitude}, ${vehicle.longitude}")
            }
        }
        is SpiderResult.Error ->
            println("Failed: ${result.error}")
    }
}

suspend fun delays(client: SpiderClient, tripIds: List<String>) {
    when (val result = client.realtime.delays(tripIds)) {
        is SpiderResult.Success ->
            result.data.delays.forEach { delay ->
                val minutes = (delay.delaySeconds ?: 0) / 60
                println("${delay.tripId}: ${if (minutes >= 0) "+$minutes" else "$minutes"} min")
            }
        is SpiderResult.Error ->
            println("Failed to load delays: ${result.error}")
    }
}

suspend fun alerts(client: SpiderClient) {
    when (val result = client.realtime.alerts()) {
        is SpiderResult.Success ->
            result.data.alerts.forEach { alert ->
                println("${alert.headerText}: ${alert.descriptionText}")
            }
        is SpiderResult.Error ->
            println("Failed to load alerts: ${result.error}")
    }
}

package examples.places

import eu.tiducto.spider.client.*

suspend fun searchPlaces(client: SpiderClient) {
    // A house number lists individual addresses; the location ranks nearer hits first and adds their distance.
    val result = client.places.search("Údolní 53", near = LatLon(49.1951, 16.6068))

    when (result) {
        is SpiderResult.Success ->
            result.data.forEach { place ->
                val where = place.admin[AdminLevel.CITY] ?: ""
                when (place.type) {
                    PlaceType.STOP -> println("Stop ${place.name} (${place.id}) — $where")
                    PlaceType.UNKNOWN -> Unit
                    else -> println("${place.name} — $where, ${place.distance} m away")
                }
            }
        is SpiderResult.Error -> println("Search failed: ${result.error}")
    }
}

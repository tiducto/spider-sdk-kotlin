package eu.tiducto.spider.client.util

import eu.tiducto.spider.client.LatLon

/**
 * Decode a Google Encoded Polyline (precision 1e5) into a list of points.
 * https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
internal fun decodePolyline(encoded: String): List<LatLon> {
    if (encoded.isEmpty()) return emptyList()
    val points = ArrayList<LatLon>(encoded.length / 4)
    var index = 0
    var lat = 0
    var lon = 0
    while (index < encoded.length) {
        var result = 0
        var shift = 0
        var b: Int
        do {
            if (index >= encoded.length) return points
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lat += dLat

        result = 0
        shift = 0
        do {
            if (index >= encoded.length) return points
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLon = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lon += dLon

        points.add(LatLon(lat = lat / 1e5, lon = lon / 1e5))
    }
    return points
}

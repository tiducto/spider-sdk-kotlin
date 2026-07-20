@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client.js

import cz.davidkurzica.client.AdminLevel
import cz.davidkurzica.client.Stop as CoreStop
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A stop returned by [SpiderStops.search]. The administrative geography (Kotlin `Map<AdminLevel,
 * String>`) is flattened into one nullable property per level; a level the deployment wasn't
 * enriched with is null.
 */
@JsExport
class Stop internal constructor(domain: CoreStop) {
    val gtfsId: String = domain.gtfsId
    val name: String = domain.name
    val lat: Double? = domain.lat
    val lon: Double? = domain.lon
    val wheelchairBoarding: String? = domain.wheelchairBoarding?.name
    val country: String? = domain.admin[AdminLevel.COUNTRY]
    val region: String? = domain.admin[AdminLevel.REGION]
    val district: String? = domain.admin[AdminLevel.DISTRICT]
    val city: String? = domain.admin[AdminLevel.CITY]
    val suburb: String? = domain.admin[AdminLevel.SUBURB]
}

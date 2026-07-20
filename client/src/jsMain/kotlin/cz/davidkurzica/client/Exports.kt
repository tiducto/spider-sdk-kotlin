@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * JS/TS-facing facade for the Spider SDK.
 *
 * The rich Kotlin API in commonMain (sealed [RouteLocation]/[RouteTime], [SpiderResult], immutable
 * collections, `kotlin.time.Instant`/`Duration`, the `search { filter { … } }` DSL) is kept intact
 * for JVM/Android/Apple consumers. This jsMain layer wraps it in export-safe shapes so a Vue/TS app
 * can consume the exact same SDK: `Array` instead of `ImmutableList`, epoch-millis `Double` instead
 * of `Instant`, enum `.name` strings instead of Kotlin enums (avoids the cross-boundary
 * enum-identity trap), and plain classes instead of sealed hierarchies and receiver-lambda DSLs.
 * Mirrors the `*View` facade convention spider-services' web/shared uses to expose KMP to Vue.
 */

/**
 * The outcome of an SDK call. Mirrors the Kotlin [SpiderResult]: a failed call is a value, not a
 * thrown exception, so JS callers branch on [isSuccess] rather than try/catch.
 */
@JsExport
class SpiderResultJs<T> internal constructor(
    val isSuccess: Boolean,
    val data: T?,
    val error: SpiderErrorJs?,
)

/** A failed call, flattened for JS: a stable [code] string, an optional message and HTTP status. */
@JsExport
class SpiderErrorJs internal constructor(
    val code: String,
    val message: String?,
    val httpStatus: Int?,
)

internal fun SpiderError.toJs(): SpiderErrorJs {
    val status = when (this) {
        is SpiderError.Server -> httpStatus
        is SpiderError.Unknown -> httpStatus
        else -> null
    }
    return SpiderErrorJs(code = code.wireName, message = cause?.message, httpStatus = status)
}

internal fun <D, T> SpiderResult<D>.toJs(map: (D) -> T): SpiderResultJs<T> = when (this) {
    is SpiderResult.Success -> SpiderResultJs(isSuccess = true, data = map(data), error = null)
    is SpiderResult.Error -> SpiderResultJs(isSuccess = false, data = null, error = error.toJs())
}

/**
 * An origin/destination for [SpiderRoutingJs]. Opaque handle around the sealed [RouteLocation];
 * build one with [stopLocation] or [coordinateLocation].
 */
@JsExport
class RouteLocationJs internal constructor(internal val domain: RouteLocation) {
    val kind: String = when (domain) {
        is RouteLocation.StopId -> "stop"
        is RouteLocation.Coordinates -> "coordinates"
    }
    val stopId: String? = (domain as? RouteLocation.StopId)?.id
    val lat: Double? = (domain as? RouteLocation.Coordinates)?.lat
    val lon: Double? = (domain as? RouteLocation.Coordinates)?.lon
}

/** A stop-id location (opaque feed-prefixed gtfsId, e.g. "1:U123"). */
@JsExport
fun stopLocation(id: String): RouteLocationJs = RouteLocationJs(RouteLocation.StopId(id))

/** A coordinate location (WGS84 degrees). */
@JsExport
fun coordinateLocation(lat: Double, lon: Double): RouteLocationJs =
    RouteLocationJs(RouteLocation.Coordinates(lat = lat, lon = lon))

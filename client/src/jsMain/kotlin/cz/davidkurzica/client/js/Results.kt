@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client.js

import cz.davidkurzica.client.Location as CoreLocation
import cz.davidkurzica.client.SpiderError as CoreSpiderError
import cz.davidkurzica.client.SpiderResult as CoreSpiderResult
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * JS/TS-facing facade for the Spider SDK.
 *
 * The rich Kotlin API in commonMain (`cz.davidkurzica.client`: sealed Location/RouteTime,
 * SpiderResult, immutable collections, `kotlin.time.Instant`/`Duration`, the `search { filter { … } }`
 * DSL) is kept intact for JVM/Android/Apple consumers. This jsMain layer, in the dedicated
 * `cz.davidkurzica.client.js` package, wraps it in export-safe shapes so a Vue/TS app consumes the
 * exact same SDK under clean names: `Array` instead of `ImmutableList`, epoch-millis `Double` instead
 * of `Instant`, enum `.name` strings instead of Kotlin enums (avoids the cross-boundary enum-identity
 * trap), and plain classes instead of sealed hierarchies and receiver-lambda DSLs. The sub-package
 * lets the facade reuse the core's names (SpiderClient, Route, …) without colliding with commonMain.
 */

/**
 * The outcome of an SDK call. Mirrors the Kotlin `SpiderResult`: a failed call is a value, not a
 * thrown exception, so JS callers branch on [isSuccess] rather than try/catch.
 */
@JsExport
class SpiderResult<T> internal constructor(
    val isSuccess: Boolean,
    val data: T?,
    val error: SpiderError?,
)

/** A failed call, flattened for JS: a stable [code] string, an optional message and HTTP status. */
@JsExport
class SpiderError internal constructor(
    val code: String,
    val message: String?,
    val httpStatus: Int?,
)

internal fun CoreSpiderError.toJs(): SpiderError {
    val status = when (this) {
        is CoreSpiderError.Server -> httpStatus
        is CoreSpiderError.Unknown -> httpStatus
        else -> null
    }
    return SpiderError(code = code.wireName, message = cause?.message, httpStatus = status)
}

internal fun <D, T> CoreSpiderResult<D>.toJs(map: (D) -> T): SpiderResult<T> = when (this) {
    is CoreSpiderResult.Success -> SpiderResult(isSuccess = true, data = map(data), error = null)
    is CoreSpiderResult.Error -> SpiderResult(isSuccess = false, data = null, error = error.toJs())
}

/**
 * An origin/destination for [SpiderRouting]. Opaque handle around the sealed core [CoreLocation];
 * build one with [Location.coordinate] or [Location.stop].
 */
@JsExport
class Location internal constructor(internal val domain: CoreLocation) {
    val kind: String = when (domain) {
        is CoreLocation.Stop -> "stop"
        is CoreLocation.Coordinate -> "coordinate"
    }
    val stopId: String? = (domain as? CoreLocation.Stop)?.id
    val latitude: Double? = (domain as? CoreLocation.Coordinate)?.latitude
    val longitude: Double? = (domain as? CoreLocation.Coordinate)?.longitude

    companion object {
        /** A coordinate location (WGS84 degrees). */
        fun coordinate(latitude: Double, longitude: Double): Location =
            Location(CoreLocation.Coordinate(latitude = latitude, longitude = longitude))

        /** A stop-id location (opaque feed-prefixed gtfsId, e.g. "1:U123"). */
        fun stop(id: String): Location = Location(CoreLocation.Stop(id))
    }
}

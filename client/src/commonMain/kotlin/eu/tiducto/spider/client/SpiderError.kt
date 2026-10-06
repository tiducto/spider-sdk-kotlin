package eu.tiducto.spider.client

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

enum class SpiderErrorCode(val wireName: String) {
    NETWORK("network"),
    TIMEOUT("timeout"),
    UNAUTHORIZED("unauthorized"),
    BAD_REQUEST("bad_request"),
    NOT_FOUND("not_found"),
    SERVER("server"),
    RATE_LIMITED("rate_limited"),
    QUERY_RETIRED("query_retired"),
    PLANNING_LIMIT_REACHED("planning_limit_reached"),
    AGREEMENT_INACTIVE("agreement_inactive"),
    DECODING("decoding"),
    UNKNOWN("unknown"),
}

sealed interface SpiderError {
    val cause: Throwable?
    val httpStatus: Int?

    val message: String
        get() = cause?.message ?: code.wireName

    val serverCode: String?
        get() = when (val cause = cause) {
            is SpiderTransportException.Http -> cause.serverCode
            is SpiderTransportException.PlanLimit -> cause.code
            else -> null
        }

    val code: SpiderErrorCode
        get() = when (this) {
            is Network -> SpiderErrorCode.NETWORK
            is Timeout -> SpiderErrorCode.TIMEOUT
            is Unauthorized -> SpiderErrorCode.UNAUTHORIZED
            is BadRequest -> SpiderErrorCode.BAD_REQUEST
            is NotFound -> SpiderErrorCode.NOT_FOUND
            is Server -> SpiderErrorCode.SERVER
            is RateLimited -> SpiderErrorCode.RATE_LIMITED
            is QueryRetired -> SpiderErrorCode.QUERY_RETIRED
            is PlanningLimitReached -> SpiderErrorCode.PLANNING_LIMIT_REACHED
            is AgreementInactive -> SpiderErrorCode.AGREEMENT_INACTIVE
            is Decoding -> SpiderErrorCode.DECODING
            is Unknown -> SpiderErrorCode.UNKNOWN
        }

    data class Network(override val cause: Throwable? = null) : SpiderError {
        override val httpStatus: Int? get() = null
    }

    data class Timeout(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    /** The key was missing or rejected (401/403). */
    data class Unauthorized(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    /**
     * The request is invalid: a required value is missing, a value is out of range, or an input is
     * malformed (such as `via`). [field] names the offending input as a dot path from the request body root
     * (such as `preferences.transit.transfer.maximumTransfers`); [message] names only the field, never the
     * limit. The SDK returns this without sending a request when a value breaks a fixed platform limit; the API
     * returns it for limits the environment sets.
     */
    data class BadRequest(
        val field: String? = null,
        override val message: String,
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    data class NotFound(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    data class Server(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    data class RateLimited(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    /**
     * The API part this SDK version calls is retired (HTTP 410); upgrade the SDK. This is the API's state,
     * not a problem with the key or the request.
     */
    data class QueryRetired(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    /**
     * The project has reached the trip-planning limit its plan includes, so only trip planning (`plan` and
     * `planStream`) is refused. Departures, trips, stop search and realtime still answer. [message] is the API's
     * own message, or `trip planning limit reached` when the body has none.
     */
    data class PlanningLimitReached(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError {
        override val message: String
            get() = (cause as? SpiderTransportException.PlanLimit)?.detail ?: PLANNING_LIMIT_REACHED_MESSAGE
    }

    /**
     * The project has no active agreement, so every call made with the key is refused. [message] is the API's
     * own message, or `agreement is not active` when the body has none.
     */
    data class AgreementInactive(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError {
        override val message: String
            get() = (cause as? SpiderTransportException.PlanLimit)?.detail ?: AGREEMENT_INACTIVE_MESSAGE
    }

    data class Decoding(override val cause: Throwable? = null) : SpiderError {
        override val httpStatus: Int? get() = null
    }

    data class Unknown(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError
}

internal sealed class SpiderTransportException(message: String) : RuntimeException(message) {
    // [detail] is the server's own message, without the request prefix [message] carries.
    class Http(
        val status: Int,
        message: String,
        val serverCode: String? = null,
        val detail: String? = null,
        val field: String? = null,
    ) : SpiderTransportException(message)
    // A plan limit refused the key: [code] is the body's `error`, [detail] the message the error reports.
    class PlanLimit(val status: Int, message: String, val code: String, val detail: String) :
        SpiderTransportException(message)
    class NoData(message: String) : SpiderTransportException(message)
    class Upstream(message: String) : SpiderTransportException(message)
    class BadRequest(val field: String?, message: String) : SpiderTransportException(message)
}

internal fun Throwable.toSpiderError(): SpiderError = when (this) {
    is SpiderTransportException.PlanLimit -> when (code) {
        PLANNING_LIMIT_REACHED_SERVER_CODE -> SpiderError.PlanningLimitReached(status, this)
        else -> SpiderError.AgreementInactive(status, this)
    }
    is SpiderTransportException.Http -> when (serverCode) {
        QUERY_RETIRED_SERVER_CODE -> SpiderError.QueryRetired(status, this)
        else -> when (status) {
            400 -> SpiderError.BadRequest(
                field = field ?: detail?.let(::fieldOfBadRequest),
                message = detail ?: message ?: SpiderErrorCode.BAD_REQUEST.wireName,
                httpStatus = status,
                cause = this,
            )
            401, 403 -> SpiderError.Unauthorized(status, this)
            404 -> SpiderError.NotFound(status, this)
            408, 504 -> SpiderError.Timeout(status, this)
            429 -> SpiderError.RateLimited(status, this)
            in 500..599 -> SpiderError.Server(status, this)
            else -> SpiderError.Unknown(httpStatus = status, cause = this)
        }
    }
    is SpiderTransportException.NoData -> SpiderError.NotFound(cause = this)
    is SpiderTransportException.BadRequest ->
        SpiderError.BadRequest(field = field, message = message ?: SpiderErrorCode.BAD_REQUEST.wireName, cause = this)
    is SpiderTransportException.Upstream -> SpiderError.Server(cause = this)
    is SerializationException -> SpiderError.Decoding(this)
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
        SpiderError.Timeout(cause = this)
    is IOException -> SpiderError.Network(this)
    else -> SpiderError.Unknown(cause = this)
}

internal const val QUERY_RETIRED_SERVER_CODE: String = "query_retired"

// The gateway's codes when a plan limit refuses the key (403, before any upstream call). Only the body's `code`
// (or, without one, its `error`) decides, whatever the status a proxy passes on; a 403 without one stays Unauthorized.
internal const val PLANNING_LIMIT_REACHED_SERVER_CODE: String = "planning_limit_reached"
internal const val AGREEMENT_INACTIVE_SERVER_CODE: String = "agreement_inactive"
private const val PLANNING_LIMIT_REACHED_MESSAGE = "trip planning limit reached"
private const val AGREEMENT_INACTIVE_MESSAGE = "agreement is not active"

internal val ErrorEnvelope.planLimitCode: String?
    get() = (code ?: error)?.takeIf { it == PLANNING_LIMIT_REACHED_SERVER_CODE || it == AGREEMENT_INACTIVE_SERVER_CODE }

// The refusal carries the body's message when it has one, else the fixed wording for [code]. [where] prefixes
// the transport message the same way the surface's other HTTP failures do.
internal fun planLimitFailure(where: String, status: Int, code: String, message: String?): SpiderTransportException {
    val detail = message?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (code == PLANNING_LIMIT_REACHED_SERVER_CODE) PLANNING_LIMIT_REACHED_MESSAGE else AGREEMENT_INACTIVE_MESSAGE
    return SpiderTransportException.PlanLimit(status, "$where → $status: $detail", code, detail)
}

// A fixed platform limit, checked before any request. Like the API's own BAD_REQUEST, the message names
// only the field.
internal fun requireInRange(field: String, inRange: Boolean) {
    if (!inRange) throw SpiderTransportException.BadRequest(field, "$field is out of range")
}

// The platform words a validation 400 as "<field> is out of range|required|invalid|not allowed" on every surface.
private val BAD_REQUEST_MESSAGE = Regex("""([A-Za-z_][A-Za-z0-9_.]*) is (?:out of range|required|invalid|not allowed)""")

internal fun fieldOfBadRequest(message: String): String? =
    BAD_REQUEST_MESSAGE.matchEntire(message.trim())?.groupValues?.get(1)

internal data class ErrorEnvelope(
    val code: String?,
    val message: String?,
    val error: String? = null,
    val field: String? = null,
)

internal fun parseErrorEnvelope(body: String): ErrorEnvelope = runCatching {
    val obj = Json.parseToJsonElement(body).jsonObject
    fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    ErrorEnvelope(string("code"), string("message"), string("error"), string("field"))
}.getOrDefault(ErrorEnvelope(null, null))

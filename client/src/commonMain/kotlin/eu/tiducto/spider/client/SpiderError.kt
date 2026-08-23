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
    DECODING("decoding"),
    UNKNOWN("unknown"),
}

sealed interface SpiderError {
    val cause: Throwable?
    val httpStatus: Int?

    val message: String
        get() = cause?.message ?: code.wireName

    val serverCode: String?
        get() = (cause as? SpiderTransportException.Http)?.serverCode

    val code: SpiderErrorCode
        get() = when (this) {
            is Network -> SpiderErrorCode.NETWORK
            is Timeout -> SpiderErrorCode.TIMEOUT
            is Unauthorized -> SpiderErrorCode.UNAUTHORIZED
            is BadRequest -> SpiderErrorCode.BAD_REQUEST
            is NotFound -> SpiderErrorCode.NOT_FOUND
            is Server -> SpiderErrorCode.SERVER
            is RateLimited -> SpiderErrorCode.RATE_LIMITED
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

    data class Unauthorized(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError

    /**
     * The server rejected the request as invalid (a GraphQL top-level `BAD_REQUEST` error): an over-cap
     * `searchWindow`, a malformed `via`, or a missing required field. [field] names the offending input
     * when the server reports one; [message] is the server's human-readable explanation.
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

    data class Decoding(override val cause: Throwable? = null) : SpiderError {
        override val httpStatus: Int? get() = null
    }

    data class Unknown(
        override val httpStatus: Int? = null,
        override val cause: Throwable? = null,
    ) : SpiderError
}

internal sealed class SpiderTransportException(message: String) : RuntimeException(message) {
    class Http(val status: Int, message: String, val serverCode: String? = null) : SpiderTransportException(message)
    class NoData(message: String) : SpiderTransportException(message)
    class Upstream(message: String) : SpiderTransportException(message)
    // A GraphQL top-level BAD_REQUEST error (validation failure from the gateway/router).
    class BadRequest(val field: String?, message: String) : SpiderTransportException(message)
}

internal fun Throwable.toSpiderError(): SpiderError = when (this) {
    is SpiderTransportException.Http -> when (status) {
        401, 403 -> SpiderError.Unauthorized(status, this)
        404 -> SpiderError.NotFound(status, this)
        408, 504 -> SpiderError.Timeout(status, this)
        429 -> SpiderError.RateLimited(status, this)
        in 500..599 -> SpiderError.Server(status, this)
        else -> SpiderError.Unknown(httpStatus = status, cause = this)
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

internal data class ErrorEnvelope(val code: String?, val message: String?)

internal fun parseErrorEnvelope(body: String): ErrorEnvelope = runCatching {
    val obj = Json.parseToJsonElement(body).jsonObject
    fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    ErrorEnvelope(string("code"), string("message"))
}.getOrDefault(ErrorEnvelope(null, null))

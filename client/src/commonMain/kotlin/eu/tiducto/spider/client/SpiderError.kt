package eu.tiducto.spider.client

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

enum class SpiderErrorCode(val wireName: String) {
    NETWORK("network"),
    TIMEOUT("timeout"),
    UNAUTHORIZED("unauthorized"),
    NOT_FOUND("not_found"),
    SERVER("server"),
    RATE_LIMITED("rate_limited"),
    DECODING("decoding"),
    UNKNOWN("unknown"),
}

sealed interface SpiderError {
    val cause: Throwable?

    val code: SpiderErrorCode
        get() = when (this) {
            is Network -> SpiderErrorCode.NETWORK
            is Timeout -> SpiderErrorCode.TIMEOUT
            is Unauthorized -> SpiderErrorCode.UNAUTHORIZED
            is NotFound -> SpiderErrorCode.NOT_FOUND
            is Server -> SpiderErrorCode.SERVER
            is RateLimited -> SpiderErrorCode.RATE_LIMITED
            is Decoding -> SpiderErrorCode.DECODING
            is Unknown -> SpiderErrorCode.UNKNOWN
        }

    data class Network(override val cause: Throwable? = null) : SpiderError
    data class Timeout(override val cause: Throwable? = null) : SpiderError
    data class Unauthorized(override val cause: Throwable? = null) : SpiderError
    data class NotFound(override val cause: Throwable? = null) : SpiderError
    data class Server(val httpStatus: Int? = null, override val cause: Throwable? = null) : SpiderError
    data class RateLimited(override val cause: Throwable? = null) : SpiderError
    data class Decoding(override val cause: Throwable? = null) : SpiderError
    data class Unknown(val httpStatus: Int? = null, override val cause: Throwable? = null) : SpiderError
}

internal sealed class SpiderTransportException(message: String) : RuntimeException(message) {
    class Http(val status: Int, message: String) : SpiderTransportException(message)
    class NoData(message: String) : SpiderTransportException(message)
    class Upstream(message: String) : SpiderTransportException(message)
}

internal fun Throwable.toSpiderError(): SpiderError = when (this) {
    is SpiderTransportException.Http -> when (status) {
        401, 403 -> SpiderError.Unauthorized(this)
        404 -> SpiderError.NotFound(this)
        408, 504 -> SpiderError.Timeout(this)
        429 -> SpiderError.RateLimited(this)
        in 500..599 -> SpiderError.Server(status, this)
        else -> SpiderError.Unknown(httpStatus = status, cause = this)
    }
    is SpiderTransportException.NoData -> SpiderError.NotFound(this)
    is SpiderTransportException.Upstream -> SpiderError.Server(cause = this)
    is SerializationException -> SpiderError.Decoding(this)
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
        SpiderError.Timeout(this)
    is IOException -> SpiderError.Network(this)
    else -> SpiderError.Unknown(cause = this)
}

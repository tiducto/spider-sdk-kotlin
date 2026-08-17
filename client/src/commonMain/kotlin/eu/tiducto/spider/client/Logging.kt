package eu.tiducto.spider.client

import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import kotlin.coroutines.cancellation.CancellationException
import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.client.plugins.logging.Logging as KtorLogging

enum class SpiderLogLevel {
    None,
    Error,
    Info,
    Headers,
    Body,
}

class LoggingConfig {
    var level: SpiderLogLevel = SpiderLogLevel.Info
}

internal class SpiderLog(
    val level: SpiderLogLevel,
    private val kermit: KermitLogger,
) {
    fun i(tag: String, message: () -> String) = kermit.i(tag = tag, message = message)
    fun d(tag: String, message: () -> String) = kermit.d(tag = tag, message = message)
    fun e(tag: String, throwable: Throwable, message: () -> String) =
        kermit.e(throwable = throwable, tag = tag, message = message)
}

internal fun LoggingConfig.buildLog(): SpiderLog {
    val writers = if (level == SpiderLogLevel.None) emptyList() else listOf(platformLogWriter())
    val minSeverity = when (level) {
        SpiderLogLevel.None -> Severity.Assert
        SpiderLogLevel.Error -> Severity.Error
        SpiderLogLevel.Info, SpiderLogLevel.Headers, SpiderLogLevel.Body -> Severity.Debug
    }
    return SpiderLog(level, KermitLogger(StaticConfig(minSeverity, writers), tag = "Spider"))
}

private fun SpiderLogLevel.toKtorLevel(): LogLevel = when (this) {
    SpiderLogLevel.None, SpiderLogLevel.Error -> LogLevel.NONE
    SpiderLogLevel.Info -> LogLevel.INFO
    SpiderLogLevel.Headers -> LogLevel.HEADERS
    SpiderLogLevel.Body -> LogLevel.ALL
}

internal fun HttpClientConfig<*>.installSpiderLogging(log: SpiderLog, tag: String) {
    val ktorLevel = log.level.toKtorLevel()
    if (ktorLevel == LogLevel.NONE) return
    install(KtorLogging) {
        level = ktorLevel
        logger = object : KtorLogger {
            override fun log(message: String) {
                log.d(tag = tag) { message }
            }
        }
    }
}

context(log: SpiderLog)
internal inline fun <T> spiderCatch(
    tag: String,
    noinline message: () -> String,
    block: () -> T,
): SpiderResult<T> = try {
    SpiderResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    log.e(tag = tag, throwable = e, message = message)
    SpiderResult.Error(e.toSpiderError())
}

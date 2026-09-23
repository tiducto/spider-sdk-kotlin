package eu.tiducto.spider.client

import kotlin.time.Duration

class SpiderClient(
    private val baseUrl: String,
    private val apiKey: String,
    block: SpiderClientBuilder.() -> Unit = {},
) {
    private val config = SpiderClientBuilder().apply(block)

    /**
     * The single wire-contract version this client speaks across every surface (routing, stops, and
     * realtime move together). Sent on every request; a gateway that declares an incompatible version
     * crashes the call with [SpiderContractMismatchError] rather than returning a [SpiderResult.Error].
     */
    val contractVersion: String get() = SpiderContract.VERSION

    /** Trip planning, departures, and single-trip lookup. */
    val routing: SpiderRouting by lazy {
        SpiderRouting(baseUrl, apiKey, config.routing.retry, config.logging)
    }

    /** Stop text search, lookup by id, and geographic (nearest / bounding-box) queries. */
    val stops: SpiderStops by lazy {
        SpiderStops(baseUrl, apiKey, config.stops.retry, config.logging)
    }

    /** Live vehicle positions, delays, and service alerts. */
    val realtime: SpiderRealtime by lazy {
        SpiderRealtime(baseUrl, apiKey, config.realtime.retry, config.logging)
    }

    /**
     * Pre-warms the network connection to the per-env API host so the first real call rides an
     * already-open connection.
     *
     * Cold TLS/connection setup — radio wake, DNS, TCP handshake, TLS handshake — to the gateway is
     * ~0.6s on mobile and otherwise lands on the first trip-planning call, nearly doubling its latency.
     * This issues one keyless `GET {baseUrl}/ping` through the [routing] surface's own HTTP client — the
     * same connection pool `plan`/`planStream` reuse — so the opened connection is already pooled when the
     * first real request arrives, and returns how long the probe took.
     *
     * Best-effort and **never throws**: any transport error or non-2xx response (including a `404` before
     * the gateway `/ping` route is deployed) still warms the connection and returns the measured elapsed
     * time. Recommended at app start and again on return-to-foreground, since mobile radios drop idle
     * connections. Safe to fire-and-forget — launch it without awaiting; the returned [Duration] is for
     * optional diagnostics only.
     */
    suspend fun warmup(): Duration = routing.warmup()

    init {
        config.logging.buildLog().i(tag = "SpiderClient") {
            "Initialized baseUrl=$baseUrl apiKey=set contract=${SpiderContract.VERSION}"
        }
    }
}

/**
 * Configuration for a [SpiderClient]. Every surface — [SpiderClient.routing], [SpiderClient.stops],
 * [SpiderClient.realtime] — is available with no setup; each surface is built lazily on first use.
 * The blocks here are optional and only needed to tune a surface (e.g. [RoutingConfig.autoRetry]) or
 * turn on [logging]. Received by the trailing lambda of the [SpiderClient] constructor.
 */
class SpiderClientBuilder internal constructor() {
    internal var logging: LoggingConfig = LoggingConfig()
    internal val routing: RoutingConfig = RoutingConfig()
    internal val stops: StopsConfig = StopsConfig()
    internal val realtime: RealtimeConfig = RealtimeConfig()

    /** Client-wide request logging (off by default). */
    fun logging(block: LoggingConfig.() -> Unit = {}) {
        logging = LoggingConfig().apply(block)
    }

    /** Configure the trip-planning surface reached at `client.routing`. */
    fun routing(block: RoutingConfig.() -> Unit) {
        routing.apply(block)
    }

    /** Configure the stop-search surface reached at `client.stops`. */
    fun stops(block: StopsConfig.() -> Unit) {
        stops.apply(block)
    }

    /** Configure the realtime surface reached at `client.realtime`. */
    fun realtime(block: RealtimeConfig.() -> Unit) {
        realtime.apply(block)
    }
}

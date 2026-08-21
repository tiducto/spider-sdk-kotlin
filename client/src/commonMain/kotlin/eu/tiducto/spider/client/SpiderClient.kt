package eu.tiducto.spider.client

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

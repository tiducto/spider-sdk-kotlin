package eu.tiducto.spider.client

class SpiderClient(
    baseUrl: String,
    apiKey: String,
    block: SpiderClientBuilder.() -> Unit = {},
) {
    private val features: Map<SpiderFeature<*, *>, Any>

    init {
        val builder = SpiderClientBuilder(baseUrl, apiKey).apply(block)
        features = builder.buildInstalled()
        builder.logging.buildLog().i(tag = "SpiderClient") {
            "Initialized baseUrl=$baseUrl apiKey=set contract=${SpiderContract.VERSION} " +
                "features=${features.keys.joinToString { it::class.simpleName ?: "?" }}"
        }
    }

    /**
     * The single wire-contract version this client speaks across every installed feature (routing, stops,
     * Realtime move together). Sent on every request; a gateway that declares an incompatible version
     * crashes the call with [SpiderContractMismatchError] rather than returning a [SpiderResult.Error].
     */
    val contractVersion: String get() = SpiderContract.VERSION

    val routing: SpiderRouting get() = feature(Routing)
    val stops: SpiderStops get() = feature(Stops)
    val realtime: SpiderRealtime get() = feature(Realtime)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> feature(key: SpiderFeature<*, T>): T =
        (features[key] as? T)
            ?: error("Feature ${key::class.simpleName} is not installed — add install(${key::class.simpleName}) to the SpiderClient { } block")
}

interface SpiderFeature<TConfig : Any, TClient : Any> {
    fun newConfig(): TConfig
    fun build(baseUrl: String, apiKey: String, config: TConfig, logging: LoggingConfig): TClient
}

class SpiderClientBuilder internal constructor(
    private val baseUrl: String,
    private val apiKey: String,
) {
    internal var logging: LoggingConfig = LoggingConfig()
    private val pending = mutableListOf<() -> Pair<SpiderFeature<*, *>, Any>>()

    fun logging(block: LoggingConfig.() -> Unit = {}) {
        logging = LoggingConfig().apply(block)
    }

    fun <C : Any, T : Any> install(feature: SpiderFeature<C, T>, block: C.() -> Unit = {}) {
        val config = feature.newConfig().apply(block)
        pending += { feature to feature.build(baseUrl, apiKey, config, logging) }
    }

    internal fun buildInstalled(): Map<SpiderFeature<*, *>, Any> = pending.associate { it() }
}

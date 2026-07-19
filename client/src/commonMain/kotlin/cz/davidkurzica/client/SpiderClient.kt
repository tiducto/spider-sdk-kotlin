package cz.davidkurzica.client

import co.touchlab.kermit.Logger

class SpiderClient(
    baseUrl: String,
    apiKey: String,
    block: SpiderClientBuilder.() -> Unit = {},
) {
    private val features: Map<SpiderFeature<*, *>, Any> =
        SpiderClientBuilder(baseUrl, apiKey).apply(block).installed.toMap()

    init {
        Logger.i(tag = "SpiderClient") {
            "Initialized baseUrl=$baseUrl apiKey=set contract=${SpiderContract.VERSION} " +
                "features=${features.keys.joinToString { it::class.simpleName ?: "?" }}"
        }
    }

    /**
     * The single wire-contract version this client speaks across every installed feature (routing, Meili,
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
    fun build(baseUrl: String, apiKey: String, config: TConfig): TClient
}

class SpiderClientBuilder internal constructor(
    private val baseUrl: String,
    private val apiKey: String,
) {
    internal val installed = mutableMapOf<SpiderFeature<*, *>, Any>()

    fun <C : Any, T : Any> install(feature: SpiderFeature<C, T>, block: C.() -> Unit = {}) {
        val config = feature.newConfig().apply(block)
        installed[feature] = feature.build(baseUrl, apiKey, config)
    }
}

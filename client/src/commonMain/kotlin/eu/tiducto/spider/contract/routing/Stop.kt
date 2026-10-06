package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Stop(
    val gtfsId: String,
    val wheelchairBoarding: WheelchairBoarding? = null,
    /** Null when the feed has none. */
    val platformCode: String? = null,
    /** Null when the feed has none. */
    val zoneId: String? = null
)

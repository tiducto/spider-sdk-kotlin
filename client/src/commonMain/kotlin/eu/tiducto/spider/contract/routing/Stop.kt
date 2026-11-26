package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Stop(
    val gtfsId: String,
    val wheelchairBoarding: WheelchairBoarding,
    /** Null when the feed has none. */
    val platformCode: String?,
    /** Null when the feed has none. */
    val zoneId: String?,
    /** The station this stop belongs to, as OTP's `Stop.parentStation`; null when the stop has none. */
    val parentStation: ParentStation?
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One journey, leg by leg. Times are in the feed's time zone.
 */
@Serializable
internal data class Itinerary(
    /** Changes between vehicles. Staying on board as the vehicle carries on as another trip (`interlineWithPreviousLeg`) is not counted. */
    val numberOfTransfers: Int,
    val legs: List<Leg>,
    val start: String? = null,
    val end: String? = null,
    /** Seconds from `start` to `end`. */
    val duration: Long? = null,
    /** Seconds spent waiting at stops. */
    val waitingTime: Long? = null
)

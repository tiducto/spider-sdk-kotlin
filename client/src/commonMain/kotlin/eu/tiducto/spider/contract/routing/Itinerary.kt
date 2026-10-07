package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One journey, leg by leg. Times are in the feed's time zone.
 */
@Serializable
internal data class Itinerary(
    val start: String,
    val end: String,
    /** Seconds from `start` to `end`. */
    val duration: Long,
    /** Seconds spent waiting at stops. */
    val waitingTime: Long,
    /** Changes between vehicles. Staying on board as the vehicle carries on as another trip (`interlineWithPreviousLeg`) is not counted. */
    val numberOfTransfers: Int,
    val legs: List<Leg>
)

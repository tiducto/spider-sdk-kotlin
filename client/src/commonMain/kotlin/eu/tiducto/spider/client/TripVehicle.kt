package eu.tiducto.spider.client

import kotlinx.serialization.Serializable

/**
 * The vehicle running a trip run, as the feed's realtime trip update names it, with its features. The same type on
 * [TripUpdate.vehicle], [Leg.vehicle], [Departure.vehicle] and [TripDetails.vehicle], so a board or an itinerary
 * shows the vehicle's icons without a second call.
 *
 * [id] is feed-prefixed (`<feedId>:<id>`), the same string as [LiveVehicle.vehicleId]; [label] is the vehicle's
 * user-visible label. [wheelchairAccessible] is the vehicle's realtime access, separate from a trip's timetable
 * [Departure.wheelchairAccessible] / [TripDetails.wheelchairAccessible]: GTFS-RT `WHEELCHAIR_ACCESSIBLE` is
 * [WheelchairBoarding.POSSIBLE], `WHEELCHAIR_INACCESSIBLE` [WheelchairBoarding.NOT_POSSIBLE], and no information
 * null, as on [LiveVehicle.wheelchairAccessible]. [airConditioning], [wifi] and [cardPayment] are the vehicle's
 * features. Every member is null when the feed doesn't say.
 */
@Serializable
data class TripVehicle(
    val id: String?,
    val label: String?,
    val wheelchairAccessible: WheelchairBoarding?,
    val airConditioning: Boolean?,
    val wifi: Boolean?,
    val cardPayment: Boolean?,
)

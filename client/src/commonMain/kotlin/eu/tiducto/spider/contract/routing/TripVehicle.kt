package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * The vehicle running a trip run, as the realtime trip update names it (GTFS-RT `TripUpdate.vehicle`), with its features. A Spider extension: OTP 2.10 has no vehicle on a leg, a departure or a trip. Its `wheelchairAccessible` is the vehicle's realtime access, separate from a trip's `wheelchairAccessible` (`StopDeparturesTrip`, `TripTimetable`), which stays the timetable's.
 */
@Serializable
internal data class TripVehicle(
    /** GTFS-RT `VehicleDescriptor.id`, feed-prefixed (`<feedId>:<id>`) as realtime `Vehicle.vehicleId`, so it names the same vehicle there; null when the feed gives none. */
    val id: String?,
    /** The vehicle's user-visible label, as the producer gives it; null when it gives none. */
    val label: String?,
    /** GTFS-RT `VehicleDescriptor.wheelchair_accessible`, mapped as OTP 2.10's `GtfsRealtimeMapper.mapWheelchairAccessible` maps it: `WHEELCHAIR_ACCESSIBLE` is `POSSIBLE`, `WHEELCHAIR_INACCESSIBLE` `NOT_POSSIBLE` and `UNKNOWN` `NO_INFORMATION`. Unset (`NO_VALUE`), which OTP maps to nothing, is `NO_INFORMATION` too. */
    val wheelchairAccessible: WheelchairBoarding,
    /** Whether the vehicle is air-conditioned; null when unknown. */
    val airConditioning: Boolean?,
    /** Whether the vehicle offers Wi-Fi; null when unknown. */
    val wifi: Boolean?,
    /** Whether the vehicle accepts card payment on board; null when unknown. */
    val cardPayment: Boolean?
)

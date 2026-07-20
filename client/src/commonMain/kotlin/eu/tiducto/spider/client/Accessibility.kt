package eu.tiducto.spider.client

import kotlinx.serialization.Serializable

/**
 * Whether wheelchair boarding is supported at a stop. Mirrors GTFS
 * `wheelchair_boarding` (and upstream's `WheelchairBoarding`). Upstream's
 * `NO_INFORMATION` is folded into `null` at the mapping boundary so callers
 * can treat "absent" and "unknown" the same way.
 */
@Serializable
enum class WheelchairBoarding { Possible, NotPossible }

/**
 * Whether bikes are allowed on a trip. Mirrors GTFS `bikes_allowed` (and
 * upstream's `BikesAllowed`). Upstream's `NO_INFORMATION` is folded into `null`
 * at the mapping boundary.
 */
@Serializable
enum class BikesAllowed { Allowed, NotAllowed }

package eu.tiducto.spider.client

import kotlinx.serialization.Serializable

/**
 * Whether a rider in a wheelchair can board, at a stop or on a trip. Mirrors GTFS `wheelchair_boarding` /
 * `wheelchair_accessible`. No information is `null`; [UNKNOWN] is a value this SDK version doesn't recognise.
 */
@Serializable
enum class WheelchairBoarding { POSSIBLE, NOT_POSSIBLE, UNKNOWN }

/**
 * Whether bikes are allowed on a trip. Mirrors GTFS `bikes_allowed`. No information is `null`; [UNKNOWN] is a
 * value this SDK version doesn't recognise.
 */
@Serializable
enum class BikesAllowed { ALLOWED, NOT_ALLOWED, UNKNOWN }

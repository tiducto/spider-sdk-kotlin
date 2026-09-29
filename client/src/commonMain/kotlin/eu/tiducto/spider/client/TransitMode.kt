package eu.tiducto.spider.client

import kotlinx.serialization.Serializable

@Serializable
enum class TransitMode {
    WALK,
    BUS,
    COACH,
    TROLLEYBUS,
    CARPOOL,
    TRAM,
    RAIL,
    SUBWAY,
    MONORAIL,
    FERRY,
    AIRPLANE,
    TAXI,
    CABLE_CAR,
    GONDOLA,
    FUNICULAR,
    SNOW_AND_ICE,
    UNKNOWN,
}

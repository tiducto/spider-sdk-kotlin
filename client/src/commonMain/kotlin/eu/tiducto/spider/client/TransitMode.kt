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
    UNKNOWN,
}

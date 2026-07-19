package cz.davidkurzica.contract.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ModeSerializer::class)
enum class Mode(val value: String) {
    @SerialName("AIRPLANE") AIRPLANE("AIRPLANE"),
    @SerialName("BICYCLE") BICYCLE("BICYCLE"),
    @SerialName("BUS") BUS("BUS"),
    @SerialName("CABLE_CAR") CABLE_CAR("CABLE_CAR"),
    @SerialName("CAR") CAR("CAR"),
    @SerialName("CARPOOL") CARPOOL("CARPOOL"),
    @SerialName("COACH") COACH("COACH"),
    @SerialName("FERRY") FERRY("FERRY"),
    @SerialName("FLEX") FLEX("FLEX"),
    @SerialName("FLEXIBLE") FLEXIBLE("FLEXIBLE"),
    @SerialName("FUNICULAR") FUNICULAR("FUNICULAR"),
    @SerialName("GONDOLA") GONDOLA("GONDOLA"),
    @SerialName("LEG_SWITCH") LEG_SWITCH("LEG_SWITCH"),
    @SerialName("MONORAIL") MONORAIL("MONORAIL"),
    @SerialName("RAIL") RAIL("RAIL"),
    @SerialName("SCOOTER") SCOOTER("SCOOTER"),
    @SerialName("SUBWAY") SUBWAY("SUBWAY"),
    @SerialName("TAXI") TAXI("TAXI"),
    @SerialName("TRAM") TRAM("TRAM"),
    @SerialName("TRANSIT") TRANSIT("TRANSIT"),
    @SerialName("TROLLEYBUS") TROLLEYBUS("TROLLEYBUS"),
    @SerialName("WALK") WALK("WALK"),
    @SerialName("unknown_default_open_api") UNKNOWN_DEFAULT_OPEN_API("unknown_default_open_api");
}

internal object ModeSerializer : KSerializer<Mode> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): Mode {
        val raw = decoder.decodeString()
        return Mode.entries.firstOrNull { it.value == raw } ?: Mode.UNKNOWN_DEFAULT_OPEN_API
    }
    override fun serialize(encoder: Encoder, value: Mode) {
        encoder.encodeString(value.value)
    }
}

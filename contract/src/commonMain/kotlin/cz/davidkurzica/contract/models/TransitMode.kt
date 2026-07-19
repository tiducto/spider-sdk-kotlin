package cz.davidkurzica.contract.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = TransitModeSerializer::class)
enum class TransitMode(val value: String) {
    @SerialName("AIRPLANE") AIRPLANE("AIRPLANE"),
    @SerialName("BUS") BUS("BUS"),
    @SerialName("CABLE_CAR") CABLE_CAR("CABLE_CAR"),
    @SerialName("CARPOOL") CARPOOL("CARPOOL"),
    @SerialName("COACH") COACH("COACH"),
    @SerialName("FERRY") FERRY("FERRY"),
    @SerialName("FUNICULAR") FUNICULAR("FUNICULAR"),
    @SerialName("GONDOLA") GONDOLA("GONDOLA"),
    @SerialName("MONORAIL") MONORAIL("MONORAIL"),
    @SerialName("RAIL") RAIL("RAIL"),
    @SerialName("SNOW_AND_ICE") SNOW_AND_ICE("SNOW_AND_ICE"),
    @SerialName("SUBWAY") SUBWAY("SUBWAY"),
    @SerialName("TAXI") TAXI("TAXI"),
    @SerialName("TRAM") TRAM("TRAM"),
    @SerialName("TROLLEYBUS") TROLLEYBUS("TROLLEYBUS"),
    @SerialName("unknown_default_open_api") UNKNOWN_DEFAULT_OPEN_API("unknown_default_open_api");
}

internal object TransitModeSerializer : KSerializer<TransitMode> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): TransitMode {
        val raw = decoder.decodeString()
        return TransitMode.entries.firstOrNull { it.value == raw } ?: TransitMode.UNKNOWN_DEFAULT_OPEN_API
    }
    override fun serialize(encoder: Encoder, value: TransitMode) {
        encoder.encodeString(value.value)
    }
}

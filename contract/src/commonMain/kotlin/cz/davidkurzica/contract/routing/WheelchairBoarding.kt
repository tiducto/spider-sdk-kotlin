package cz.davidkurzica.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = WheelchairBoardingSerializer::class)
enum class WheelchairBoarding(val value: String) {
    @SerialName("NOT_POSSIBLE") NOT_POSSIBLE("NOT_POSSIBLE"),
    @SerialName("NO_INFORMATION") NO_INFORMATION("NO_INFORMATION"),
    @SerialName("POSSIBLE") POSSIBLE("POSSIBLE"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object WheelchairBoardingSerializer : KSerializer<WheelchairBoarding> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): WheelchairBoarding {
        val raw = decoder.decodeString()
        return WheelchairBoarding.entries.firstOrNull { it.value == raw } ?: WheelchairBoarding.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: WheelchairBoarding) {
        encoder.encodeString(value.value)
    }
}

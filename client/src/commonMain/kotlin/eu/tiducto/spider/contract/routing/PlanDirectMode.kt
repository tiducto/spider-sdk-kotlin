package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PlanDirectModeSerializer::class)
internal enum class PlanDirectMode(val value: String) {
    @SerialName("BICYCLE") BICYCLE("BICYCLE"),
    @SerialName("BICYCLE_PARKING") BICYCLE_PARKING("BICYCLE_PARKING"),
    @SerialName("BICYCLE_RENTAL") BICYCLE_RENTAL("BICYCLE_RENTAL"),
    @SerialName("CAR") CAR("CAR"),
    @SerialName("CAR_PARKING") CAR_PARKING("CAR_PARKING"),
    @SerialName("CAR_RENTAL") CAR_RENTAL("CAR_RENTAL"),
    @SerialName("FLEX") FLEX("FLEX"),
    @SerialName("SCOOTER_RENTAL") SCOOTER_RENTAL("SCOOTER_RENTAL"),
    @SerialName("WALK") WALK("WALK"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object PlanDirectModeSerializer : KSerializer<PlanDirectMode> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): PlanDirectMode {
        val raw = decoder.decodeString()
        return PlanDirectMode.entries.firstOrNull { it.value == raw } ?: PlanDirectMode.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: PlanDirectMode) {
        encoder.encodeString(value.value)
    }
}

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PlanEgressModeSerializer::class)
internal enum class PlanEgressMode(val value: String) {
    @SerialName("BICYCLE") BICYCLE("BICYCLE"),
    @SerialName("BICYCLE_RENTAL") BICYCLE_RENTAL("BICYCLE_RENTAL"),
    @SerialName("CAR") CAR("CAR"),
    @SerialName("CAR_PICKUP") CAR_PICKUP("CAR_PICKUP"),
    @SerialName("CAR_RENTAL") CAR_RENTAL("CAR_RENTAL"),
    @SerialName("FLEX") FLEX("FLEX"),
    @SerialName("SCOOTER_RENTAL") SCOOTER_RENTAL("SCOOTER_RENTAL"),
    @SerialName("WALK") WALK("WALK"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object PlanEgressModeSerializer : KSerializer<PlanEgressMode> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): PlanEgressMode {
        val raw = decoder.decodeString()
        return PlanEgressMode.entries.firstOrNull { it.value == raw } ?: PlanEgressMode.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: PlanEgressMode) {
        encoder.encodeString(value.value)
    }
}

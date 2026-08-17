package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PlanTransferModeSerializer::class)
internal enum class PlanTransferMode(val value: String) {
    @SerialName("BICYCLE") BICYCLE("BICYCLE"),
    @SerialName("CAR") CAR("CAR"),
    @SerialName("WALK") WALK("WALK"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object PlanTransferModeSerializer : KSerializer<PlanTransferMode> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): PlanTransferMode {
        val raw = decoder.decodeString()
        return PlanTransferMode.entries.firstOrNull { it.value == raw } ?: PlanTransferMode.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: PlanTransferMode) {
        encoder.encodeString(value.value)
    }
}

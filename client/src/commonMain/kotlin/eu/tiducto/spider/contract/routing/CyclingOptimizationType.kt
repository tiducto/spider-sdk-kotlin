package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CyclingOptimizationTypeSerializer::class)
internal enum class CyclingOptimizationType(val value: String) {
    @SerialName("FLAT_STREETS") FLAT_STREETS("FLAT_STREETS"),
    @SerialName("SAFEST_STREETS") SAFEST_STREETS("SAFEST_STREETS"),
    @SerialName("SAFE_STREETS") SAFE_STREETS("SAFE_STREETS"),
    @SerialName("SHORTEST_DURATION") SHORTEST_DURATION("SHORTEST_DURATION"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object CyclingOptimizationTypeSerializer : KSerializer<CyclingOptimizationType> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): CyclingOptimizationType {
        val raw = decoder.decodeString()
        return CyclingOptimizationType.entries.firstOrNull { it.value == raw } ?: CyclingOptimizationType.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: CyclingOptimizationType) {
        encoder.encodeString(value.value)
    }
}

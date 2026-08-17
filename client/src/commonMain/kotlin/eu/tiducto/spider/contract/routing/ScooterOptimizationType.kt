package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ScooterOptimizationTypeSerializer::class)
internal enum class ScooterOptimizationType(val value: String) {
    @SerialName("FLAT_STREETS") FLAT_STREETS("FLAT_STREETS"),
    @SerialName("SAFEST_STREETS") SAFEST_STREETS("SAFEST_STREETS"),
    @SerialName("SAFE_STREETS") SAFE_STREETS("SAFE_STREETS"),
    @SerialName("SHORTEST_DURATION") SHORTEST_DURATION("SHORTEST_DURATION"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object ScooterOptimizationTypeSerializer : KSerializer<ScooterOptimizationType> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): ScooterOptimizationType {
        val raw = decoder.decodeString()
        return ScooterOptimizationType.entries.firstOrNull { it.value == raw } ?: ScooterOptimizationType.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: ScooterOptimizationType) {
        encoder.encodeString(value.value)
    }
}

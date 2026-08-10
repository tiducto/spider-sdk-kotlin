package eu.tiducto.spider.contract.routing

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
internal data class PlanConnectionRequest(
    /** Persisted-query id (lowercase hex SHA-256 of the canonical query). */
    val id: Id,
    val variables: PlanConnectionVariables
) {

    @Serializable(with = IdSerializer::class)
    enum class Id(val value: String) {
        @SerialName("a0cc636086f0cb10bea736a4977961afaa245a26cb3f8d23352a74c1f6ba9857") A0CC636086F0CB10BEA736A4977961AFAA245A26CB3F8D23352A74C1F6BA9857("a0cc636086f0cb10bea736a4977961afaa245a26cb3f8d23352a74c1f6ba9857"),
        @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
    }

    internal object IdSerializer : KSerializer<Id> {
        override val descriptor = String.serializer().descriptor
        override fun deserialize(decoder: Decoder): Id {
            val raw = decoder.decodeString()
            return Id.entries.firstOrNull { it.value == raw } ?: Id.UNKNOWN
        }
        override fun serialize(encoder: Encoder, value: Id) {
            encoder.encodeString(value.value)
        }
    }
}

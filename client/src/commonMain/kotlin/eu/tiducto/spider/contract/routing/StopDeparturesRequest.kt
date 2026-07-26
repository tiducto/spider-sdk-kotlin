package eu.tiducto.spider.contract.routing

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
internal data class StopDeparturesRequest(
    /** Persisted-query id (lowercase hex SHA-256 of the canonical query). */
    val id: Id,
    val variables: StopDeparturesVariables
) {

    @Serializable(with = IdSerializer::class)
    enum class Id(val value: String) {
        @SerialName("70a644fe3c6b2cbf5b2d70cef8230c1428bea6357ae1766772162d86469563d0") _70A644FE3C6B2CBF5B2D70CEF8230C1428BEA6357AE1766772162D86469563D0("70a644fe3c6b2cbf5b2d70cef8230c1428bea6357ae1766772162d86469563d0"),
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

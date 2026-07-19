package cz.davidkurzica.contract.routing

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PlanConnectionRequest(
    /** Persisted-query id (lowercase hex SHA-256 of the canonical query). */
    val id: Id,
    val variables: PlanConnectionVariables
) {

    @Serializable(with = IdSerializer::class)
    enum class Id(val value: String) {
        @SerialName("f19608964d423831b485ccc878cb25eff56c720585d4423ee617c864e2b3102e") F19608964D423831B485CCC878CB25EFF56C720585D4423EE617C864E2B3102E("f19608964d423831b485ccc878cb25eff56c720585d4423ee617c864e2b3102e"),
        @SerialName("unknown_default_open_api") UNKNOWN_DEFAULT_OPEN_API("unknown_default_open_api");
    }

    internal object IdSerializer : KSerializer<Id> {
        override val descriptor = String.serializer().descriptor
        override fun deserialize(decoder: Decoder): Id {
            val raw = decoder.decodeString()
            return Id.entries.firstOrNull { it.value == raw } ?: Id.UNKNOWN_DEFAULT_OPEN_API
        }
        override fun serialize(encoder: Encoder, value: Id) {
            encoder.encodeString(value.value)
        }
    }
}

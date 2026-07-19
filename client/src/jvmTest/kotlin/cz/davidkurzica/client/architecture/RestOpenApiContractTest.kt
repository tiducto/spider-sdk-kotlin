package cz.davidkurzica.client.architecture

import cz.davidkurzica.contract.meili.MeiliError
import cz.davidkurzica.contract.meili.MeiliStop
import cz.davidkurzica.contract.meili.SearchRequest
import cz.davidkurzica.contract.meili.SearchResponse
import cz.davidkurzica.contract.realtime.ActivePeriodDto
import cz.davidkurzica.contract.realtime.AlertDto
import cz.davidkurzica.contract.realtime.AlertsResponseDto
import cz.davidkurzica.contract.realtime.DelayDto
import cz.davidkurzica.contract.realtime.DelaysResponseDto
import cz.davidkurzica.contract.realtime.InformedEntityDto
import cz.davidkurzica.contract.realtime.StopTimeUpdateDto
import cz.davidkurzica.contract.realtime.VehicleByTripResponseDto
import cz.davidkurzica.contract.realtime.VehicleDto
import cz.davidkurzica.contract.realtime.VehiclesResponseDto
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Cross-repo pin: the hand-written REST wire types (`cz.davidkurzica.contract.meili` +
 * `.realtime`) must match the published `rest-openapi.json` contract (from `tiducto/spider-contract`,
 * kept in sync by `scripts/generate-contract.sh`). These are two mirrors of one contract — this test
 * turns any drift between them into a red build, the REST analog of `RoutingWireContractTest`.
 *
 * A *check*, not codegen: it compares field-sets, so it never touches the generator. jvmTest because it
 * reads the spec off the classpath.
 */
class RestOpenApiContractTest {

    private val components =
        (this::class.java.getResource("/rest-openapi.json")?.readText()
            ?: fail("rest-openapi.json fixture missing under client/src/jvmTest/resources — refresh via scripts/generate-contract.sh"))
            .let { Json.parseToJsonElement(it).jsonObject }
            .getValue("components").jsonObject.getValue("schemas").jsonObject

    // spec component -> SDK serializer descriptor. Spec names drop the `Dto` suffix / rename the Meili
    // envelope; the field-SETS must match (nullability differences are fine — both sides tolerate absence).
    private val pairs: List<Pair<String, SerialDescriptor>> = listOf(
        "StopSearchRequest" to SearchRequest.serializer().descriptor,
        "StopSearchResponse" to SearchResponse.serializer(MeiliStop.serializer()).descriptor,
        "MeiliStop" to MeiliStop.serializer().descriptor,
        "MeiliError" to MeiliError.serializer().descriptor,
        "VehiclesResponse" to VehiclesResponseDto.serializer().descriptor,
        "VehicleByTripResponse" to VehicleByTripResponseDto.serializer().descriptor,
        "Vehicle" to VehicleDto.serializer().descriptor,
        "DelaysResponse" to DelaysResponseDto.serializer().descriptor,
        "Delay" to DelayDto.serializer().descriptor,
        "StopTimeUpdate" to StopTimeUpdateDto.serializer().descriptor,
        "AlertsResponse" to AlertsResponseDto.serializer().descriptor,
        "Alert" to AlertDto.serializer().descriptor,
        "ActivePeriod" to ActivePeriodDto.serializer().descriptor,
        "InformedEntity" to InformedEntityDto.serializer().descriptor,
    )

    private fun specFields(component: String): Set<String> =
        components[component]?.jsonObject?.get("properties")?.jsonObject?.keys
            ?: fail("rest-openapi.json is missing component `$component`")

    private fun sdkFields(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

    @Test
    fun `hand-written REST wire types match the published rest-openapi contract`() {
        val mismatches = pairs.mapNotNull { (component, descriptor) ->
            val spec = specFields(component)
            val sdk = sdkFields(descriptor)
            if (spec == sdk) null
            else "$component: spec-only=${(spec - sdk).sorted()} sdk-only=${(sdk - spec).sorted()}"
        }
        assertEquals(
            emptyList<String>(),
            mismatches,
            "REST wire types drifted from rest-openapi.json — reconcile the SDK types with the contract.",
        )
    }
}

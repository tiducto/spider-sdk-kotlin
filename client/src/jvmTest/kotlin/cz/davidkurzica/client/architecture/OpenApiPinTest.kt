package cz.davidkurzica.client.architecture

import cz.davidkurzica.contract.stops.StopHit
import cz.davidkurzica.contract.stops.StopSearchError
import cz.davidkurzica.contract.stops.StopSearchRequest
import cz.davidkurzica.contract.stops.StopSearchResponse
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
 * Cross-repo pin: the hand-written wire types (`cz.davidkurzica.contract.stops` + `.realtime`) must match
 * their published per-surface OpenAPI docs (from `tiducto/spider-contract`, kept in sync by
 * `scripts/generate-contract.sh`). Field-set comparison, never codegen; jvmTest so it reads the specs off
 * the classpath. The routing analog is `RoutingWireContractTest`.
 */
class OpenApiPinTest {

    private fun schemas(resource: String) =
        (this::class.java.getResource(resource)?.readText()
            ?: fail("$resource fixture missing under client/src/jvmTest/resources — refresh via scripts/generate-contract.sh"))
            .let { Json.parseToJsonElement(it).jsonObject }
            .getValue("components").jsonObject.getValue("schemas").jsonObject

    private fun sdkFields(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

    private fun check(resource: String, pairs: List<Pair<String, SerialDescriptor>>) {
        val schemas = schemas(resource)
        val mismatches = pairs.mapNotNull { (component, descriptor) ->
            val spec = schemas[component]?.jsonObject?.get("properties")?.jsonObject?.keys
                ?: fail("$resource is missing component `$component`")
            val sdk = sdkFields(descriptor)
            if (spec == sdk) null
            else "$component: spec-only=${(spec - sdk).sorted()} sdk-only=${(sdk - spec).sorted()}"
        }
        assertEquals(
            emptyList<String>(),
            mismatches,
            "wire types drifted from $resource — reconcile the SDK types with the contract.",
        )
    }

    @Test
    fun `stops wire types match stops-openapi`() = check(
        "/stops-openapi.json",
        listOf(
            "StopSearchRequest" to StopSearchRequest.serializer().descriptor,
            "StopSearchResponse" to StopSearchResponse.serializer(StopHit.serializer()).descriptor,
            "StopHit" to StopHit.serializer().descriptor,
            "StopSearchError" to StopSearchError.serializer().descriptor,
        ),
    )

    @Test
    fun `realtime wire types match realtime-openapi`() = check(
        "/realtime-openapi.json",
        listOf(
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
        ),
    )
}

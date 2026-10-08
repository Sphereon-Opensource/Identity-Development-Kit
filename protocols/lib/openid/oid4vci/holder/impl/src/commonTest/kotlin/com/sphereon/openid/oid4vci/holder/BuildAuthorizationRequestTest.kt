/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.holder

import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.Oid4vciAuthorizationDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the authorization_details JSON building logic used in
 * BuildAuthorizationRequestCommandImpl.
 *
 * Validates that credential_identifiers, locations, and optional fields
 * are correctly included/omitted in the authorization_details JSON structure
 * per RFC 9396 and OID4VCI 1.1.
 *
 * Uses the same JSON building approach as the implementation to verify structure.
 */
class BuildAuthorizationRequestTest {
    private val json = Oid4vciJson.lenient

    /**
     * Replicates the buildAuthorizationDetails logic from BuildAuthorizationRequestCommandImpl
     * for isolated testing.
     */
    private fun buildAuthorizationDetails(
        credentialConfigurationIds: List<String>,
        credentialIdentifiers: Map<String, List<String>>? = null,
        locations: List<String>? = null,
    ): String {
        val array =
            buildJsonArray {
                for (configId in credentialConfigurationIds) {
                    add(
                        buildJsonObject {
                            put("type", JsonPrimitive("openid_credential"))
                            put("credential_configuration_id", JsonPrimitive(configId))
                            credentialIdentifiers?.get(configId)?.let { ids ->
                                putJsonArray("credential_identifiers") { ids.forEach { add(JsonPrimitive(it)) } }
                            }
                            locations?.let { locs ->
                                putJsonArray("locations") { locs.forEach { add(JsonPrimitive(it)) } }
                            }
                        },
                    )
                }
            }
        return json.encodeToString(JsonArray.serializer(), array)
    }

    // ========================================================================
    // buildAuthorizationDetails output includes credential_identifiers
    // ========================================================================

    @Test
    fun authorizationDetailsIncludesCredentialIdentifiers() {
        val result =
            buildAuthorizationDetails(
                credentialConfigurationIds = listOf("UniversityDegreeCredential"),
                credentialIdentifiers = mapOf("UniversityDegreeCredential" to listOf("degree-id-1", "degree-id-2")),
            )

        val array = json.decodeFromString(JsonArray.serializer(), result)
        assertEquals(1, array.size)

        val entry = array[0].jsonObject
        assertEquals("openid_credential", entry["type"]?.jsonPrimitive?.content)
        assertEquals("UniversityDegreeCredential", entry["credential_configuration_id"]?.jsonPrimitive?.content)

        val ids = entry["credential_identifiers"]?.jsonArray
        assertNotNull(ids, "credential_identifiers must be present")
        assertEquals(2, ids.size)
        assertEquals("degree-id-1", ids[0].jsonPrimitive.content)
        assertEquals("degree-id-2", ids[1].jsonPrimitive.content)
    }

    // ========================================================================
    // buildAuthorizationDetails output includes locations
    // ========================================================================

    @Test
    fun authorizationDetailsIncludesLocations() {
        val result =
            buildAuthorizationDetails(
                credentialConfigurationIds = listOf("PIDCredential"),
                locations = listOf("https://issuer.example.com", "https://backup.example.com"),
            )

        val array = json.decodeFromString(JsonArray.serializer(), result)
        assertEquals(1, array.size)

        val entry = array[0].jsonObject
        val locs = entry["locations"]?.jsonArray
        assertNotNull(locs, "locations must be present")
        assertEquals(2, locs.size)
        assertEquals("https://issuer.example.com", locs[0].jsonPrimitive.content)
        assertEquals("https://backup.example.com", locs[1].jsonPrimitive.content)
    }

    // ========================================================================
    // buildAuthorizationDetails omits optional fields when null
    // ========================================================================

    @Test
    fun authorizationDetailsOmitsOptionalFieldsWhenNull() {
        val result =
            buildAuthorizationDetails(
                credentialConfigurationIds = listOf("Degree"),
                credentialIdentifiers = null,
                locations = null,
            )

        val array = json.decodeFromString(JsonArray.serializer(), result)
        assertEquals(1, array.size)

        val entry = array[0].jsonObject
        assertTrue(entry.containsKey("type"), "type must be present")
        assertTrue(entry.containsKey("credential_configuration_id"), "credential_configuration_id must be present")
        assertFalse(entry.containsKey("credential_identifiers"), "null credential_identifiers must not appear")
        assertFalse(entry.containsKey("locations"), "null locations must not appear")
    }

    // ========================================================================
    // JSON structure matches RFC 9396 format
    // ========================================================================

    @Test
    fun jsonStructureMatchesRfc9396Format() {
        val result =
            buildAuthorizationDetails(
                credentialConfigurationIds = listOf("UniversityDegreeCredential", "DriverLicense"),
                credentialIdentifiers =
                    mapOf(
                        "UniversityDegreeCredential" to listOf("uni-id-1"),
                    ),
                locations = listOf("https://issuer.example.com"),
            )

        val array = json.decodeFromString(JsonArray.serializer(), result)
        assertEquals(2, array.size, "One entry per credential_configuration_id")

        // First entry should have credential_identifiers (matched by configId)
        val first = array[0].jsonObject
        assertEquals("openid_credential", first["type"]?.jsonPrimitive?.content)
        assertEquals("UniversityDegreeCredential", first["credential_configuration_id"]?.jsonPrimitive?.content)
        assertNotNull(first["credential_identifiers"], "UniversityDegreeCredential should have identifiers")
        assertNotNull(first["locations"], "locations should be present on all entries")

        // Second entry should NOT have credential_identifiers (no match in map)
        val second = array[1].jsonObject
        assertEquals("openid_credential", second["type"]?.jsonPrimitive?.content)
        assertEquals("DriverLicense", second["credential_configuration_id"]?.jsonPrimitive?.content)
        assertFalse(second.containsKey("credential_identifiers"), "DriverLicense should not have identifiers")
        assertNotNull(second["locations"], "locations should be present on all entries")
    }

    // ========================================================================
    // Credential identifiers not matched to any configId are ignored
    // ========================================================================

    @Test
    fun unmatchedCredentialIdentifiersAreIgnored() {
        val result =
            buildAuthorizationDetails(
                credentialConfigurationIds = listOf("Degree"),
                credentialIdentifiers = mapOf("OtherConfig" to listOf("id-1")),
            )

        val array = json.decodeFromString(JsonArray.serializer(), result)
        val entry = array[0].jsonObject

        assertFalse(
            entry.containsKey("credential_identifiers"),
            "Identifiers for non-matching configId should not appear",
        )
    }

    // ========================================================================
    // Each authorization detail can be deserialized as Oid4vciAuthorizationDetail
    // ========================================================================

    @Test
    fun eachEntryDeserializesAsOid4vciAuthorizationDetail() {
        val result =
            buildAuthorizationDetails(
                credentialConfigurationIds = listOf("UniversityDegreeCredential"),
                credentialIdentifiers = mapOf("UniversityDegreeCredential" to listOf("id-1")),
                locations = listOf("https://issuer.example.com"),
            )

        val array = json.decodeFromString(JsonArray.serializer(), result)
        val detail = json.decodeFromJsonElement(Oid4vciAuthorizationDetail.serializer(), array[0])

        assertEquals("openid_credential", detail.type)
        assertEquals("UniversityDegreeCredential", detail.credentialConfigurationId)
        assertEquals(listOf("id-1"), detail.credentialIdentifiers)
        assertEquals(listOf("https://issuer.example.com"), detail.locations)
    }
}

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

package com.sphereon.openid.oid4vci.common.impl.serialization

import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.ClaimsDescriptionObject
import com.sphereon.openid.oid4vci.common.model.Oid4vciAuthorizationDetail
import com.sphereon.openid.oid4vci.common.model.claimsAsDescriptionObjects
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Serialization round-trip tests for [Oid4vciAuthorizationDetail].
 *
 * Validates snake_case wire format, round-trip fidelity, and support for
 * OID4VCI 1.1 fields (credential_identifiers, locations, claims).
 */
class AuthorizationDetailSerializationTest {
    private val json = Oid4vciJson.lenient
    private val jsonNoDefaults = Oid4vciJson.lenientNoDefaults

    // ========================================================================
    // Round-trip with credentialIdentifiers and locations
    // ========================================================================

    @Test
    fun roundTripWithCredentialIdentifiersAndLocations() {
        val original =
            Oid4vciAuthorizationDetail(
                credentialConfigurationId = "UniversityDegreeCredential",
                credentialIdentifiers = listOf("id-1", "id-2"),
                locations = listOf("https://issuer.example.com"),
            )

        val encoded = json.encodeToString(Oid4vciAuthorizationDetail.serializer(), original)
        val decoded = json.decodeFromString(Oid4vciAuthorizationDetail.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals("openid_credential", decoded.type)
        assertEquals("UniversityDegreeCredential", decoded.credentialConfigurationId)
        assertEquals(listOf("id-1", "id-2"), decoded.credentialIdentifiers)
        assertEquals(listOf("https://issuer.example.com"), decoded.locations)
    }

    // ========================================================================
    // Round-trip with claims as 1.1 ClaimsDescriptionObject list
    // ========================================================================

    @Test
    fun roundTripWithClaimsAsDescriptionObjectList() {
        val claimsJson =
            json.parseToJsonElement(
                """[{"path": ["$.credentialSubject.name"]}, {"path": ["$.credentialSubject.degree"], "values": ["Master"]}]""",
            )

        val original =
            Oid4vciAuthorizationDetail(
                credentialConfigurationId = "UniversityDegreeCredential",
                claims = claimsJson,
            )

        val encoded = json.encodeToString(Oid4vciAuthorizationDetail.serializer(), original)
        val decoded = json.decodeFromString(Oid4vciAuthorizationDetail.serializer(), encoded)

        assertEquals(original, decoded)
        assertNotNull(decoded.claims)

        val descriptionObjects = decoded.claimsAsDescriptionObjects()
        assertNotNull(descriptionObjects)
        assertEquals(2, descriptionObjects.size)
        assertEquals("$.credentialSubject.name", descriptionObjects[0].path[0].jsonPrimitive.content)
        assertNull(descriptionObjects[0].values)
        assertEquals("$.credentialSubject.degree", descriptionObjects[1].path[0].jsonPrimitive.content)
        assertNotNull(descriptionObjects[1].values)
        assertEquals("Master", descriptionObjects[1].values!![0].jsonPrimitive.content)
    }

    // ========================================================================
    // Wire format: verify credential_identifiers key is snake_case
    // ========================================================================

    @Test
    fun wireFormatUsesSnakeCaseKeys() {
        val detail =
            Oid4vciAuthorizationDetail(
                credentialConfigurationId = "PIDCredential",
                credentialIdentifiers = listOf("pid-id-1"),
                locations = listOf("https://issuer.example.com"),
            )

        val encoded = json.encodeToString(Oid4vciAuthorizationDetail.serializer(), detail)
        val wireObj = json.parseToJsonElement(encoded).jsonObject

        // Must use snake_case wire keys
        assertTrue(wireObj.containsKey("credential_configuration_id"), "must have snake_case 'credential_configuration_id'")
        assertTrue(wireObj.containsKey("credential_identifiers"), "must have snake_case 'credential_identifiers'")
        assertTrue(wireObj.containsKey("locations"), "must have 'locations'")

        // Must NOT use camelCase
        assertTrue(!wireObj.containsKey("credentialConfigurationId"), "must NOT use camelCase 'credentialConfigurationId'")
        assertTrue(!wireObj.containsKey("credentialIdentifiers"), "must NOT use camelCase 'credentialIdentifiers'")

        // type has default "openid_credential" so may be omitted with encodeDefaults=false;
        // when present, it must be the correct value
        wireObj["type"]?.let { assertEquals("openid_credential", it.jsonPrimitive.content) }
        assertEquals("PIDCredential", wireObj["credential_configuration_id"]?.jsonPrimitive?.content)

        val ids = wireObj["credential_identifiers"]?.jsonArray
        assertNotNull(ids)
        assertEquals(1, ids.size)
        assertEquals("pid-id-1", ids[0].jsonPrimitive.content)
    }

    // ========================================================================
    // Deserialization from spec-like JSON strings
    // ========================================================================

    @Test
    fun deserializationFromSpecLikeJson() {
        val raw =
            """
            {
              "type": "openid_credential",
              "credential_configuration_id": "org.iso.18013.5.1.mDL",
              "credential_identifiers": ["mDL-id-1", "mDL-id-2"],
              "locations": ["https://mdl-issuer.example.com", "https://backup.example.com"]
            }
            """.trimIndent()

        val decoded = json.decodeFromString(Oid4vciAuthorizationDetail.serializer(), raw)

        assertEquals("openid_credential", decoded.type)
        assertEquals("org.iso.18013.5.1.mDL", decoded.credentialConfigurationId)
        assertEquals(listOf("mDL-id-1", "mDL-id-2"), decoded.credentialIdentifiers)
        assertEquals(listOf("https://mdl-issuer.example.com", "https://backup.example.com"), decoded.locations)
        assertNull(decoded.claims)
    }

    @Test
    fun deserializationFromMinimalSpecJson() {
        val raw =
            """
            {
              "type": "openid_credential",
              "credential_configuration_id": "UniversityDegreeCredential"
            }
            """.trimIndent()

        val decoded = json.decodeFromString(Oid4vciAuthorizationDetail.serializer(), raw)

        assertEquals("openid_credential", decoded.type)
        assertEquals("UniversityDegreeCredential", decoded.credentialConfigurationId)
        assertNull(decoded.credentialIdentifiers)
        assertNull(decoded.locations)
        assertNull(decoded.claims)
    }

    // ========================================================================
    // Optional fields omitted when null (encodeDefaults = false)
    // ========================================================================

    @Test
    fun optionalFieldsOmittedWhenNull() {
        val detail =
            Oid4vciAuthorizationDetail(
                credentialConfigurationId = "Degree",
            )

        val encoded = jsonNoDefaults.encodeToString(Oid4vciAuthorizationDetail.serializer(), detail)
        val wireObj = jsonNoDefaults.parseToJsonElement(encoded).jsonObject

        assertTrue(wireObj.containsKey("credential_configuration_id"), "must have required field")
        // type has a default value ("openid_credential") and encodeDefaults=false omits it
        assertTrue(!wireObj.containsKey("claims"), "null claims must not appear")
        assertTrue(!wireObj.containsKey("locations"), "null locations must not appear")
        assertTrue(!wireObj.containsKey("credential_identifiers"), "null credential_identifiers must not appear")
    }
}

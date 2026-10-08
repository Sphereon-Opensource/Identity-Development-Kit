/*
 * © 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.software.registry.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SoftwareInstanceSerializationTest {

    private val json = Json

    private fun instance(partyId: String? = null): SoftwareInstance = SoftwareInstance(
        instanceId = "acme",
        tenantId = "tenant-acme",
        capabilityType = SoftwareCapabilityType.OAUTH2_AUTHORIZATION_SERVER,
        displayName = "Acme AS",
        lifecycleStatus = SoftwareLifecycleStatus.ACTIVE,
        managementMode = SoftwareManagementMode.MANAGED,
        configKeyPrefix = "oauth2.servers.acme",
        partyId = partyId,
    )

    @Test
    fun partyId_roundTrips() {
        val original = instance(partyId = "3f7f2b6e-6f1e-4a9a-9a3e-2f6f0f1a2b3c")
        val decoded = json.decodeFromString<SoftwareInstance>(json.encodeToString(original))
        assertEquals(original, decoded)
        assertEquals("3f7f2b6e-6f1e-4a9a-9a3e-2f6f0f1a2b3c", decoded.partyId)
    }

    @Test
    fun partyId_absentInJson_staysNull() {
        val payload = """
            {
              "instanceId": "acme",
              "tenantId": "tenant-acme",
              "capabilityType": "OAUTH2_AUTHORIZATION_SERVER",
              "displayName": "Acme AS",
              "lifecycleStatus": "ACTIVE",
              "managementMode": "MANAGED",
              "configKeyPrefix": "oauth2.servers.acme"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<SoftwareInstance>(payload)
        assertNull(decoded.partyId)
        assertEquals(instance(partyId = null), decoded)
    }

    @Test
    fun partyId_defaultOmittedFromEncodedShape() {
        val encoded = json.encodeToString(instance(partyId = null))
        assertFalse(encoded.contains("partyId"), "null partyId must not change the serialized shape: $encoded")
    }
}

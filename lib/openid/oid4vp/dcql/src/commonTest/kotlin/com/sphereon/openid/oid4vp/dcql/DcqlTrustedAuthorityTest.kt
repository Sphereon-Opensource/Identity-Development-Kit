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
 */

package com.sphereon.openid.oid4vp.dcql

import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DcqlTrustedAuthorityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseAuthorityKeyIdentifier() {
        val jsonString =
            """
            {
              "type": "authority_key_identifier",
              "values": ["qZWNjNDU2Nzg5MGFiY2RlZjAxMjM0NTY3ODkwYWJjZGVm"]
            }
            """.trimIndent()

        val authority = json.decodeFromString<DcqlTrustedAuthority>(jsonString)

        assertNotNull(authority)
        assertEquals("authority_key_identifier", authority.type)
        assertEquals(1, authority.values.size)
        assertEquals("qZWNjNDU2Nzg5MGFiY2RlZjAxMjM0NTY3ODkwYWJjZGVm", authority.values[0])
    }

    @Test
    fun parseEtsiTrustedList() {
        val jsonString =
            """
            {
              "type": "etsi_trusted_list",
              "values": [
                "https://eidas.europa.eu/TL/EN_TL.xml",
                "https://trust.example.eu/provider/12345"
              ]
            }
            """.trimIndent()

        val authority = json.decodeFromString<DcqlTrustedAuthority>(jsonString)

        assertNotNull(authority)
        assertEquals("etsi_trusted_list", authority.type)
        assertEquals(2, authority.values.size)
        assertEquals("https://eidas.europa.eu/TL/EN_TL.xml", authority.values[0])
        assertEquals("https://trust.example.eu/provider/12345", authority.values[1])
    }

    @Test
    fun parseOpenIdFederation() {
        val jsonString =
            """
            {
              "type": "openid_federation",
              "values": [
                "https://federation.example.com",
                "https://trust-anchor.edu"
              ]
            }
            """.trimIndent()

        val authority = json.decodeFromString<DcqlTrustedAuthority>(jsonString)

        assertNotNull(authority)
        assertEquals("openid_federation", authority.type)
        assertEquals(2, authority.values.size)
        assertEquals("https://federation.example.com", authority.values[0])
        assertEquals("https://trust-anchor.edu", authority.values[1])
    }

    @Test
    fun serializeAuthorityKeyIdentifier() {
        val authority =
            DcqlTrustedAuthority(
                type = "authority_key_identifier",
                values = listOf("qZWNjNDU2Nzg5MGFiY2RlZjAxMjM0NTY3ODkwYWJjZGVm"),
            )

        val jsonString = json.encodeToString(DcqlTrustedAuthority.serializer(), authority)

        assertTrue(jsonString.contains("\"type\":\"authority_key_identifier\""))
        assertTrue(jsonString.contains("\"values\""))
        assertTrue(jsonString.contains("qZWNjNDU2Nzg5MGFiY2RlZjAxMjM0NTY3ODkwYWJjZGVm"))
    }

    @Test
    fun serializeEtsiTrustedList() {
        val authority =
            DcqlTrustedAuthority(
                type = "etsi_trusted_list",
                values =
                    listOf(
                        "https://eidas.europa.eu/TL/EN_TL.xml",
                        "https://trust.example.eu/provider/12345",
                    ),
            )

        val jsonString = json.encodeToString(DcqlTrustedAuthority.serializer(), authority)

        assertTrue(jsonString.contains("\"type\":\"etsi_trusted_list\""))
        assertTrue(jsonString.contains("\"values\""))
        assertTrue(jsonString.contains("https://eidas.europa.eu/TL/EN_TL.xml"))
    }

    @Test
    fun serializeOpenIdFederation() {
        val authority =
            DcqlTrustedAuthority(
                type = "openid_federation",
                values =
                    listOf(
                        "https://federation.example.com",
                        "https://trust-anchor.edu",
                    ),
            )

        val jsonString = json.encodeToString(DcqlTrustedAuthority.serializer(), authority)

        assertTrue(jsonString.contains("\"type\":\"openid_federation\""))
        assertTrue(jsonString.contains("\"values\""))
        assertTrue(jsonString.contains("https://federation.example.com"))
    }

    // Deserialization validation tests

    @Test
    fun rejectInvalidTypeOnDeserialization() {
        val jsonString =
            """
            {
              "type": "invalid_type",
              "values": ["some_value"]
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DcqlTrustedAuthority>(jsonString)
        }
    }

    @Test
    fun rejectEmptyValuesOnDeserialization() {
        val jsonString =
            """
            {
              "type": "authority_key_identifier",
              "values": []
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DcqlTrustedAuthority>(jsonString)
        }
    }

    @Test
    fun rejectEmptyStringValuesOnDeserialization() {
        val jsonString =
            """
            {
              "type": "authority_key_identifier",
              "values": [""]
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DcqlTrustedAuthority>(jsonString)
        }
    }

    @Test
    fun rejectNonHttpsUrlsForEtsiOnDeserialization() {
        val jsonString =
            """
            {
              "type": "etsi_trusted_list",
              "values": ["http://eidas.europa.eu/TL/EN_TL.xml"]
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DcqlTrustedAuthority>(jsonString)
        }
    }

    @Test
    fun rejectNonHttpsUrlsForOpenIdFederationOnDeserialization() {
        val jsonString =
            """
            {
              "type": "openid_federation",
              "values": ["http://federation.example.com"]
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DcqlTrustedAuthority>(jsonString)
        }
    }

    @Test
    fun rejectEmptyTypeOnDeserialization() {
        val jsonString =
            """
            {
              "type": "",
              "values": ["some_value"]
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DcqlTrustedAuthority>(jsonString)
        }
    }

    @Test
    fun parseTrustedAuthoritiesInCredentialQuery() {
        val jsonString =
            """
            {
              "id": "identity_credential",
              "format": "dc+sd-jwt",
              "trusted_authorities": [
                {
                  "type": "openid_federation",
                  "values": ["https://federation.example.com"]
                },
                {
                  "type": "etsi_trusted_list",
                  "values": ["https://eidas.europa.eu/TL/EN_TL.xml"]
                }
              ]
            }
            """.trimIndent()

        val credential = json.decodeFromString<DcqlCredentialQuery>(jsonString)

        assertNotNull(credential.normalizedTrustedAuthorities)
        assertEquals(2, credential.normalizedTrustedAuthorities!!.size)

        val first = credential.normalizedTrustedAuthorities!![0]
        assertEquals("openid_federation", first.type)

        val second = credential.normalizedTrustedAuthorities!![1]
        assertEquals("etsi_trusted_list", second.type)
    }

    // Programmatic validation tests

    @Test
    fun rejectInvalidTypeProgrammatically() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(
                type = "invalid_type",
                values = listOf("some_value"),
            )
        }
    }

    @Test
    fun rejectEmptyTypeProgrammatically() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(
                type = "",
                values = listOf("some_value"),
            )
        }
    }

    @Test
    fun rejectEmptyValuesProgrammatically() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(
                type = "authority_key_identifier",
                values = emptyList(),
            )
        }
    }

    @Test
    fun rejectEmptyStringInValuesProgrammatically() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(
                type = "authority_key_identifier",
                values = listOf(""),
            )
        }
    }

    @Test
    fun rejectNonHttpsUrlsForEtsiProgrammatically() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(
                type = "etsi_trusted_list",
                values = listOf("http://eidas.europa.eu/TL/EN_TL.xml"),
            )
        }
    }

    @Test
    fun rejectNonHttpsUrlsForOpenIdFederationProgrammatically() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(
                type = "openid_federation",
                values = listOf("http://federation.example.com"),
            )
        }
    }

    @Test
    fun validateAuthorityKeyIdentifierProgrammatically() {
        val authority =
            DcqlTrustedAuthority(
                type = "authority_key_identifier",
                values = listOf("qZWNjNDU2Nzg5MGFiY2RlZjAxMjM0NTY3ODkwYWJjZGVm"),
            )

        val result = validateDcqlTrustedAuthority(authority)
        assertTrue(result is Valid)
    }

    @Test
    fun validateEtsiTrustedListProgrammatically() {
        val authority =
            DcqlTrustedAuthority(
                type = "etsi_trusted_list",
                values = listOf("https://eidas.europa.eu/TL/EN_TL.xml"),
            )

        val result = validateDcqlTrustedAuthority(authority)
        assertTrue(result is Valid)
    }

    @Test
    fun validateOpenIdFederationProgrammatically() {
        val authority =
            DcqlTrustedAuthority(
                type = "openid_federation",
                values = listOf("https://federation.example.com"),
            )

        val result = validateDcqlTrustedAuthority(authority)
        assertTrue(result is Valid)
    }

    // Automatic normalization tests

    @Test
    fun automaticallyCombinesDuplicateAuthorityTypesOnDeserialization() {
        val jsonString =
            """
            {
              "id": "identity_credential",
              "format": "dc+sd-jwt",
              "trusted_authorities": [
                {
                  "type": "openid_federation",
                  "values": ["https://fed1.example.com"]
                },
                {
                  "type": "etsi_trusted_list",
                  "values": ["https://eidas.europa.eu/TL/EN_TL.xml"]
                },
                {
                  "type": "openid_federation",
                  "values": ["https://fed2.example.com"]
                }
              ]
            }
            """.trimIndent()

        val credential = json.decodeFromString<DcqlCredentialQuery>(jsonString)

        assertNotNull(credential.normalizedTrustedAuthorities)
        assertEquals(2, credential.normalizedTrustedAuthorities!!.size)

        // First occurrence of openid_federation should be first
        val openidFed = credential.normalizedTrustedAuthorities!![0]
        assertEquals("openid_federation", openidFed.type)
        assertEquals(2, openidFed.values.size)
        assertTrue(openidFed.values.contains("https://fed1.example.com"))
        assertTrue(openidFed.values.contains("https://fed2.example.com"))

        // ETSI should be second
        val etsi = credential.normalizedTrustedAuthorities!![1]
        assertEquals("etsi_trusted_list", etsi.type)
        assertEquals(1, etsi.values.size)
        assertEquals("https://eidas.europa.eu/TL/EN_TL.xml", etsi.values[0])
    }

    @Test
    fun automaticallyRemovesDuplicateValuesWithinSameType() {
        val jsonString =
            """
            {
              "id": "identity_credential",
              "trusted_authorities": [
                {
                  "type": "openid_federation",
                  "values": ["https://fed.example.com", "https://fed2.example.com"]
                },
                {
                  "type": "openid_federation",
                  "values": ["https://fed.example.com", "https://fed3.example.com"]
                }
              ]
            }
            """.trimIndent()

        val credential = json.decodeFromString<DcqlCredentialQuery>(jsonString)

        assertNotNull(credential.normalizedTrustedAuthorities)
        assertEquals(1, credential.normalizedTrustedAuthorities!!.size)

        val openidFed = credential.normalizedTrustedAuthorities!![0]
        assertEquals("openid_federation", openidFed.type)
        assertEquals(3, openidFed.values.size)
        assertTrue(openidFed.values.contains("https://fed.example.com"))
        assertTrue(openidFed.values.contains("https://fed2.example.com"))
        assertTrue(openidFed.values.contains("https://fed3.example.com"))
    }

    @Test
    fun automaticallyMaintainsOrderOfFirstOccurrence() {
        val jsonString =
            """
            {
              "id": "identity_credential",
              "trusted_authorities": [
                {
                  "type": "etsi_trusted_list",
                  "values": ["https://etsi1.example.com"]
                },
                {
                  "type": "openid_federation",
                  "values": ["https://fed1.example.com"]
                },
                {
                  "type": "authority_key_identifier",
                  "values": ["aki123"]
                },
                {
                  "type": "openid_federation",
                  "values": ["https://fed2.example.com"]
                },
                {
                  "type": "etsi_trusted_list",
                  "values": ["https://etsi2.example.com"]
                }
              ]
            }
            """.trimIndent()

        val credential = json.decodeFromString<DcqlCredentialQuery>(jsonString)

        assertNotNull(credential.normalizedTrustedAuthorities)
        assertEquals(3, credential.normalizedTrustedAuthorities!!.size)

        // Order should be: ETSI, OpenID, AKI (first occurrence order)
        assertEquals("etsi_trusted_list", credential.normalizedTrustedAuthorities!![0].type)
        assertEquals(2, credential.normalizedTrustedAuthorities!![0].values.size)

        assertEquals("openid_federation", credential.normalizedTrustedAuthorities!![1].type)
        assertEquals(2, credential.normalizedTrustedAuthorities!![1].values.size)

        assertEquals("authority_key_identifier", credential.normalizedTrustedAuthorities!![2].type)
        assertEquals(1, credential.normalizedTrustedAuthorities!![2].values.size)
    }

    @Test
    fun automaticallyHandlesNoDuplicateTypes() {
        val jsonString =
            """
            {
              "id": "identity_credential",
              "trusted_authorities": [
                {
                  "type": "openid_federation",
                  "values": ["https://fed.example.com"]
                },
                {
                  "type": "etsi_trusted_list",
                  "values": ["https://eidas.europa.eu/TL/EN_TL.xml"]
                }
              ]
            }
            """.trimIndent()

        val credential = json.decodeFromString<DcqlCredentialQuery>(jsonString)

        assertNotNull(credential.normalizedTrustedAuthorities)
        assertEquals(2, credential.normalizedTrustedAuthorities!!.size)

        assertEquals("openid_federation", credential.normalizedTrustedAuthorities!![0].type)
        assertEquals(1, credential.normalizedTrustedAuthorities!![0].values.size)

        assertEquals("etsi_trusted_list", credential.normalizedTrustedAuthorities!![1].type)
        assertEquals(1, credential.normalizedTrustedAuthorities!![1].values.size)
    }

    @Test
    fun automaticallyHandlesNullTrustedAuthorities() {
        val jsonString =
            """
            {
              "id": "identity_credential",
              "format": "dc+sd-jwt"
            }
            """.trimIndent()

        val credential = json.decodeFromString<DcqlCredentialQuery>(jsonString)

        assertEquals(null, credential.normalizedTrustedAuthorities)
    }

    @Test
    fun automaticallyHandlesEmptyTrustedAuthoritiesList() {
        val jsonString =
            """
            {
              "id": "identity_credential",
              "format": "dc+sd-jwt",
              "trusted_authorities": []
            }
            """.trimIndent()

        val credential = json.decodeFromString<DcqlCredentialQuery>(jsonString)

        assertNotNull(credential.normalizedTrustedAuthorities)
        assertEquals(0, credential.normalizedTrustedAuthorities!!.size)
    }

    @Test
    fun automaticallyCombinesDuplicateTypesOnProgrammaticConstruction() {
        val credential =
            DcqlCredentialQuery(
                id = "identity_credential",
                trusted_authorities =
                    listOf(
                        DcqlTrustedAuthority("openid_federation", listOf("https://fed1.com")),
                        DcqlTrustedAuthority("openid_federation", listOf("https://fed2.com")),
                        DcqlTrustedAuthority("openid_federation", listOf("https://fed3.com")),
                    ),
            )

        assertNotNull(credential.normalizedTrustedAuthorities)
        assertEquals(1, credential.normalizedTrustedAuthorities!!.size)
        assertEquals("openid_federation", credential.normalizedTrustedAuthorities!![0].type)
        assertEquals(3, credential.normalizedTrustedAuthorities!![0].values.size)
        assertTrue(credential.normalizedTrustedAuthorities!![0].values.contains("https://fed1.com"))
        assertTrue(credential.normalizedTrustedAuthorities!![0].values.contains("https://fed2.com"))
        assertTrue(credential.normalizedTrustedAuthorities!![0].values.contains("https://fed3.com"))
    }
}

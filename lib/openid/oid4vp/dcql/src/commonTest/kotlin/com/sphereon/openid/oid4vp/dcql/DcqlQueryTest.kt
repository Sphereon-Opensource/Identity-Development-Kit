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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DcqlQueryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseSimpleCredentialQuery() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "my_credential",
                  "format": "dc+sd-jwt",
                  "meta": {
                    "vct_values": ["https://credentials.example.com/identity_credential"]
                  },
                  "claims": [
                    {"path": ["last_name"]},
                    {"path": ["first_name"]}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        assertNotNull(query.credentials)
        assertEquals(1, query.credentials!!.size)

        val credential = query.credentials!!.first()
        assertEquals("my_credential", credential.id)
        assertEquals("dc+sd-jwt", credential.format)
        assertNotNull(credential.meta)
        assertEquals(2, credential.claims?.size)

        val claims = credential.claims!!
        assertEquals(listOf("last_name"), claims[0].path)
        assertEquals(listOf("first_name"), claims[1].path)
    }

    @Test
    fun parseCredentialQueryWithNestedClaims() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "address_credential",
                  "claims": [
                    {"path": ["address", "street_address"]},
                    {"path": ["address", "locality"]},
                    {"path": ["address", "postal_code"]}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        val claims = query.credentials!!.first().claims!!
        assertEquals(listOf("address", "street_address"), claims[0].path)
        assertEquals(listOf("address", "locality"), claims[1].path)
        assertEquals(listOf("address", "postal_code"), claims[2].path)
    }

    @Test
    fun parseCredentialQueryWithClaimValues() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "age_credential",
                  "claims": [
                    {"path": ["over_18"], "values": [true]},
                    {"path": ["over_21"], "values": [true]}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        val claims = query.credentials!!.first().claims!!
        assertEquals(listOf("over_18"), claims[0].path)
        assertNotNull(claims[0].values)
        assertEquals(1, claims[0].values!!.size)
    }

    @Test
    fun parseCredentialQueryWithIntentToRetain() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "email_credential",
                  "claims": [
                    {"path": ["email"], "intent_to_retain": true}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        val claim =
            query.credentials!!
                .first()
                .claims!!
                .first()
        assertEquals(listOf("email"), claim.path)
        assertEquals(true, claim.intent_to_retain)
    }

    @Test
    fun parseCredentialSetQuery() {
        val jsonString =
            """
            {
              "credential_sets": [
                {
                  "required": true,
                  "options": [
                    {"credential_ids": ["passport"]},
                    {"credential_ids": ["drivers_license"]},
                    {"credential_ids": ["national_id"]}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        assertNotNull(query.credential_sets)
        assertEquals(1, query.credential_sets!!.size)

        val credentialSet = query.credential_sets!!.first()
        assertEquals(true, credentialSet.required)
        assertEquals(3, credentialSet.options.size)
        assertEquals(listOf("passport"), credentialSet.options[0].credential_ids)
        assertEquals(listOf("drivers_license"), credentialSet.options[1].credential_ids)
        assertEquals(listOf("national_id"), credentialSet.options[2].credential_ids)
    }

    @Test
    fun parseCredentialSetWithMultipleCredentialsPerOption() {
        val jsonString =
            """
            {
              "credential_sets": [
                {
                  "options": [
                    {"credential_ids": ["university_id", "transcript"]},
                    {"credential_ids": ["diploma"]}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        val options = query.credential_sets!!.first().options
        assertEquals(listOf("university_id", "transcript"), options[0].credential_ids)
        assertEquals(listOf("diploma"), options[1].credential_ids)
    }

    @Test
    fun parseQueryWithBothCredentialsAndCredentialSets() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "identity",
                  "claims": [{"path": ["name"]}]
                }
              ],
              "credential_sets": [
                {
                  "options": [
                    {"credential_ids": ["passport"]},
                    {"credential_ids": ["drivers_license"]}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        assertNotNull(query.credentials)
        assertNotNull(query.credential_sets)
        assertEquals(1, query.credentials!!.size)
        assertEquals(1, query.credential_sets!!.size)
    }

    @Test
    fun parseClaimSets() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "identity",
                  "claim_sets": [
                    {
                      "id": "basic_identity",
                      "claims": ["first_name", "last_name", "birth_date"]
                    },
                    {
                      "id": "contact_info",
                      "claims": ["email", "phone"]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        val claimSets = query.credentials!!.first().claim_sets!!
        assertEquals(2, claimSets.size)
        assertEquals("basic_identity", claimSets[0].id)
        assertEquals(listOf("first_name", "last_name", "birth_date"), claimSets[0].claims)
        assertEquals("contact_info", claimSets[1].id)
        assertEquals(listOf("email", "phone"), claimSets[1].claims)
    }

    @Test
    fun failToCreateQueryWithoutCredentialsOrCredentialSets() {
        assertFailsWith<IllegalArgumentException> {
            DcqlQuery(credentials = null, credential_sets = null)
        }
    }

    @Test
    fun serializeAndDeserializeQuery() {
        val originalQuery =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "test_credential",
                            format = "dc+sd-jwt",
                            claims =
                                listOf(
                                    DcqlClaimQuery(path = listOf("name")),
                                    DcqlClaimQuery(path = listOf("email")),
                                ),
                        ),
                    ),
            )

        val jsonString = json.encodeToString(DcqlQuery.serializer(), originalQuery)
        val deserializedQuery = json.decodeFromString<DcqlQuery>(jsonString)

        assertEquals(originalQuery, deserializedQuery)
    }

    @Test
    fun serializeQueryWithMetaAsJsonObject() {
        val meta =
            buildJsonObject {
                putJsonArray("vct_values") {
                    add(JsonPrimitive("https://example.com/credential"))
                }
            }

        val query =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "test",
                            format = "dc+sd-jwt",
                            meta = meta,
                        ),
                    ),
            )

        val jsonString = json.encodeToString(DcqlQuery.serializer(), query)
        val deserializedQuery = json.decodeFromString<DcqlQuery>(jsonString)

        assertEquals(query.credentials!!.first().meta, deserializedQuery.credentials!!.first().meta)
    }

    @Test
    fun parseOpenID4VPSpecExampleSimpleIdentityCredential() {
        // Example from OpenID4VP 1.0 Section 6
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "identity_credential",
                  "format": "dc+sd-jwt",
                  "meta": {
                    "vct_values": ["https://credentials.example.com/identity_credential"]
                  },
                  "claims": [
                    {"path": ["given_name"]},
                    {"path": ["family_name"]},
                    {"path": ["birthdate"]},
                    {"path": ["place_of_birth"]},
                    {"path": ["nationalities"]}
                  ]
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        assertEquals("identity_credential", query.credentials!!.first().id)
        assertEquals(
            5,
            query.credentials!!
                .first()
                .claims!!
                .size,
        )
    }

    @Test
    fun parseCredentialQueryWithRequireCryptographicHolderBinding() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "test_credential",
                  "format": "dc+sd-jwt",
                  "require_cryptographic_holder_binding": false
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        assertEquals(false, query.credentials!!.first().require_cryptographic_holder_binding)
    }

    @Test
    fun parseCredentialQueryWithMultipleTrue() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "test_credential",
                  "format": "dc+sd-jwt",
                  "multiple": true
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        assertEquals(true, query.credentials!!.first().multiple)
    }

    @Test
    fun parseCredentialQueryWithTrustedAuthorities() {
        val jsonString =
            """
            {
              "credentials": [
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
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        val credential = query.credentials!!.first()
        assertNotNull(credential.trusted_authorities)
        assertEquals(2, credential.trusted_authorities!!.size)

        val first = credential.trusted_authorities!![0]
        assertEquals("openid_federation", first.type)
        assertEquals("https://federation.example.com", first.values[0])

        val second = credential.trusted_authorities!![1]
        assertEquals("etsi_trusted_list", second.type)
    }

    @Test
    fun serializeCredentialQueryWithAllNewFields() {
        val query =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "test_credential",
                            format = "dc+sd-jwt",
                            require_cryptographic_holder_binding = false,
                            multiple = true,
                            trusted_authorities =
                                listOf(
                                    DcqlTrustedAuthority(type = "openid_federation", values = listOf("https://federation.example.com")),
                                    DcqlTrustedAuthority(type = "authority_key_identifier", values = listOf("qZWNjNDU2Nzg5MGFiY2RlZjAxMjM0NTY3ODkwYWJjZGVm")),
                                ),
                            claims =
                                listOf(
                                    DcqlClaimQuery(path = listOf("name")),
                                ),
                        ),
                    ),
            )

        val jsonString = json.encodeToString(DcqlQuery.serializer(), query)
        val deserializedQuery = json.decodeFromString<DcqlQuery>(jsonString)

        val credential = deserializedQuery.credentials!!.first()
        assertEquals(false, credential.require_cryptographic_holder_binding)
        assertEquals(true, credential.multiple)
        assertNotNull(credential.trusted_authorities)
        assertEquals(2, credential.trusted_authorities!!.size)
    }

    @Test
    fun defaultValuesForNewFieldsWhenNotSpecifiedInJSON() {
        val jsonString =
            """
            {
              "credentials": [
                {
                  "id": "test_credential",
                  "format": "dc+sd-jwt"
                }
              ]
            }
            """.trimIndent()

        val query = json.decodeFromString<DcqlQuery>(jsonString)

        val credential = query.credentials!!.first()
        assertEquals(true, credential.require_cryptographic_holder_binding) // Default is true
        assertEquals(false, credential.multiple) // Default is false
        assertEquals(null, credential.trusted_authorities) // Default is null
    }
}

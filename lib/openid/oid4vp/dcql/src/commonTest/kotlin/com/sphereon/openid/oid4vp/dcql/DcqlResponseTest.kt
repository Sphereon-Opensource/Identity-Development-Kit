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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DcqlResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseResponseWithCredentialMatches() {
        val jsonString =
            """
            {
              "credential_matches": [
                {
                  "credential_id": "identity_credential",
                  "claims_satisfied": ["given_name", "family_name", "birthdate"]
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<DcqlResponse>(jsonString)

        assertNotNull(response.credential_matches)
        assertEquals(1, response.credential_matches!!.size)

        val match = response.credential_matches!!.first()
        assertEquals("identity_credential", match.credential_id)
        assertNotNull(match.claims_satisfied)
        assertEquals(3, match.claims_satisfied!!.size)
        assertEquals(listOf("given_name", "family_name", "birthdate"), match.claims_satisfied)
    }

    @Test
    fun parseResponseWithCredentialSetMatches() {
        val jsonString =
            """
            {
              "credential_set_matches": [
                {
                  "credential_set_id": "0",
                  "credential_id": "passport"
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<DcqlResponse>(jsonString)

        assertNotNull(response.credential_set_matches)
        assertEquals(1, response.credential_set_matches!!.size)

        val match = response.credential_set_matches!!.first()
        assertEquals("0", match.credential_set_id)
        assertEquals("passport", match.credential_id)
    }

    @Test
    fun parseResponseWithBothMatchTypes() {
        val jsonString =
            """
            {
              "credential_matches": [
                {
                  "credential_id": "identity",
                  "claims_satisfied": ["name"]
                }
              ],
              "credential_set_matches": [
                {
                  "credential_set_id": "0",
                  "credential_id": "passport"
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<DcqlResponse>(jsonString)

        assertNotNull(response.credential_matches)
        assertNotNull(response.credential_set_matches)
        assertEquals(1, response.credential_matches!!.size)
        assertEquals(1, response.credential_set_matches!!.size)
    }

    @Test
    fun parseCredentialMatchWithoutClaimsSatisfied() {
        val jsonString =
            """
            {
              "credential_matches": [
                {
                  "credential_id": "test_credential"
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<DcqlResponse>(jsonString)

        val match = response.credential_matches!!.first()
        assertEquals("test_credential", match.credential_id)
        assertEquals(null, match.claims_satisfied)
    }

    @Test
    fun serializeAndDeserializeResponse() {
        val originalResponse =
            DcqlResponse(
                credential_matches =
                    listOf(
                        DcqlCredentialMatch(
                            credential_id = "identity",
                            claims_satisfied = listOf("name", "email"),
                        ),
                    ),
                credential_set_matches =
                    listOf(
                        DcqlCredentialSetMatch(
                            credential_set_id = "0",
                            credential_id = "passport",
                        ),
                    ),
            )

        val jsonString = json.encodeToString(DcqlResponse.serializer(), originalResponse)
        val deserializedResponse = json.decodeFromString<DcqlResponse>(jsonString)

        assertEquals(originalResponse, deserializedResponse)
    }

    @Test
    fun parseMultipleCredentialMatches() {
        val jsonString =
            """
            {
              "credential_matches": [
                {
                  "credential_id": "identity",
                  "claims_satisfied": ["name", "birthdate"]
                },
                {
                  "credential_id": "address",
                  "claims_satisfied": ["street_address", "locality"]
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<DcqlResponse>(jsonString)

        assertEquals(2, response.credential_matches!!.size)
        assertEquals("identity", response.credential_matches!![0].credential_id)
        assertEquals("address", response.credential_matches!![1].credential_id)
    }

    @Test
    fun parseMultipleCredentialSetMatchesForSameSet() {
        // When an option has multiple credential_ids, there will be multiple matches
        val jsonString =
            """
            {
              "credential_set_matches": [
                {
                  "credential_set_id": "0",
                  "credential_id": "university_id"
                },
                {
                  "credential_set_id": "0",
                  "credential_id": "transcript"
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<DcqlResponse>(jsonString)

        assertEquals(2, response.credential_set_matches!!.size)
        assertEquals("0", response.credential_set_matches!![0].credential_set_id)
        assertEquals("0", response.credential_set_matches!![1].credential_set_id)
        assertEquals("university_id", response.credential_set_matches!![0].credential_id)
        assertEquals("transcript", response.credential_set_matches!![1].credential_id)
    }

    @Test
    fun parseOpenID4VPSpecExampleResponse() {
        // Example from OpenID4VP 1.0 Section 6.5
        val jsonString =
            """
            {
              "credential_matches": [
                {
                  "credential_id": "identity_credential",
                  "claims_satisfied": [
                    "given_name",
                    "family_name",
                    "birthdate",
                    "place_of_birth",
                    "nationalities"
                  ]
                }
              ]
            }
            """.trimIndent()

        val response = json.decodeFromString<DcqlResponse>(jsonString)

        assertEquals(1, response.credential_matches!!.size)
        assertEquals("identity_credential", response.credential_matches!!.first().credential_id)
        assertEquals(
            5,
            response.credential_matches!!
                .first()
                .claims_satisfied!!
                .size,
        )
    }
}

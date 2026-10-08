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

package com.sphereon.openid.oid4vp.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VpTokenTest {
    @Test
    fun createVpTokenWithSingleQueryAndPresentation() {
        val token = vpTokenOf("driver_license_query", "eyJhbGciOiJFUzI1NiJ9...")

        assertEquals(1, token.presentations.size)
        assertEquals(1, token.presentationCount)
        assertNotNull(token.getPresentation("driver_license_query"))
        assertEquals("eyJhbGciOiJFUzI1NiJ9...", token.getSinglePresentation("driver_license_query"))
    }

    @Test
    fun createVpTokenWithMultipleQueries() {
        val token =
            VpToken.fromStrings(
                mapOf(
                    "driver_license_query" to listOf("eyJhbGc1..."),
                    "employment_query" to listOf("eyJhbGc2..."),
                ),
            )

        assertEquals(2, token.presentations.size)
        assertEquals(2, token.presentationCount)
        assertEquals(setOf("driver_license_query", "employment_query"), token.queryIds)
    }

    @Test
    fun createVpTokenWithMultiplePresentationsForSameQuery() {
        val token =
            VpToken.fromStrings(
                mapOf(
                    "employment_query" to listOf("eyJhbGc1...", "eyJhbGc2...", "eyJhbGc3..."),
                ),
            )

        assertEquals(1, token.presentations.size)
        assertEquals(3, token.presentationCount)
        assertEquals(3, token.getPresentation("employment_query")?.size)
    }

    @Test
    fun rejectEmptyPresentationsMap() {
        assertFailsWith<IllegalArgumentException> {
            VpToken(emptyMap())
        }
    }

    @Test
    fun rejectBlankQueryId() {
        assertFailsWith<IllegalArgumentException> {
            VpToken.fromStrings(mapOf("" to listOf("eyJhbGc...")))
        }
    }

    @Test
    fun rejectEmptyPresentationsList() {
        assertFailsWith<IllegalArgumentException> {
            VpToken.fromStrings(mapOf("query1" to emptyList()))
        }
    }

    @Test
    fun rejectBlankPresentation() {
        assertFailsWith<IllegalArgumentException> {
            VpToken.fromStrings(mapOf("query1" to listOf("eyJhbGc...", "")))
        }
    }

    @Test
    fun parseVpTokenFromDcqlJsonObjectWithSinglePresentations() {
        val json =
            buildJsonObject {
                putJsonArray("driver_license_query") {
                    add(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9..."))
                }
                putJsonArray("age_verification_query") {
                    add(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9_2..."))
                }
            }
        val token = VpToken.fromJson(json)

        assertEquals(2, token.presentations.size)
        assertEquals("eyJhbGciOiJFUzI1NiJ9...", token.getSinglePresentation("driver_license_query"))
        assertEquals("eyJhbGciOiJFUzI1NiJ9_2...", token.getSinglePresentation("age_verification_query"))
    }

    @Test
    fun parseVpTokenFromDcqlJsonObjectWithArrayPresentations() {
        val json =
            buildJsonObject {
                putJsonArray("driver_license_query") {
                    add(JsonPrimitive("eyJhbGc1..."))
                }
                putJsonArray("employment_query") {
                    add(JsonPrimitive("eyJhbGc2..."))
                    add(JsonPrimitive("eyJhbGc3..."))
                }
            }
        val token = VpToken.fromJson(json)

        assertEquals(2, token.presentations.size)
        assertEquals(3, token.presentationCount)
        assertEquals(1, token.getPresentation("driver_license_query")?.size)
        assertEquals(2, token.getPresentation("employment_query")?.size)
    }

    @Test
    fun rejectScalarPresentationValues() {
        val compactScalar = buildJsonObject { put("query1", "eyJhbGc...") }
        val objectScalar =
            buildJsonObject {
                put("query1", buildJsonObject { put("type", "VerifiablePresentation") })
            }

        assertFailsWith<IllegalArgumentException> { VpToken.fromJson(compactScalar) }
        assertFailsWith<IllegalArgumentException> { VpToken.fromJson(objectScalar) }
    }

    @Test
    fun rejectNonObjectJson() {
        assertFailsWith<IllegalArgumentException> {
            VpToken.fromJson(JsonPrimitive("eyJhbGc..."))
        }

        assertFailsWith<IllegalArgumentException> {
            VpToken.fromJson(JsonArray(listOf(JsonPrimitive("eyJhbGc..."))))
        }
    }

    @Test
    fun rejectNonStringPresentationInJsonArray() {
        val json =
            buildJsonObject {
                putJsonArray("query1") {
                    add(JsonPrimitive("eyJhbGc..."))
                    add(JsonPrimitive(456))
                }
            }
        assertFailsWith<IllegalArgumentException> {
            VpToken.fromJson(json)
        }
    }

    /**
     * OID4VP §8.1: an `ldp_vc` Presentation value is a JSON object, not a string.
     * Parsing such a vp_token MUST NOT crash and MUST preserve the object shape.
     */
    @Test
    fun parseVpTokenWithLdpJsonObjectPresentation() {
        val ldpPresentation =
            buildJsonObject {
                put("@context", "https://www.w3.org/ns/credentials/v2")
                put("type", "VerifiablePresentation")
                put("proof", buildJsonObject { put("type", "DataIntegrityProof") })
            }
        val json =
            buildJsonObject {
                putJsonArray("compact_query") {
                    add(JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.payload.sig"))
                }
                putJsonArray("ldp_query") { add(ldpPresentation) }
            }

        val token = VpToken.fromJson(json)

        assertEquals(2, token.presentations.size)
        assertEquals(2, token.presentationCount)
        // String presentation round-trips as its raw content.
        assertEquals("eyJhbGciOiJFUzI1NiJ9.payload.sig", token.getSinglePresentation("compact_query"))
        // Object presentation is preserved as a JsonObject (no crash, no jsonPrimitive cast).
        val ldpElement = token.getSinglePresentationElement("ldp_query")
        assertIs<JsonObject>(ldpElement)
        assertEquals(ldpPresentation, ldpElement)
        // The string-view accessor renders the object as compact JSON instead of throwing.
        assertNotNull(token.getSinglePresentation("ldp_query"))
        // toJson round-trips the object shape unchanged.
        val roundTripped = VpToken.fromJson(VpToken.run { token.toJson() })
        assertEquals(ldpPresentation, roundTripped.getSinglePresentationElement("ldp_query"))
    }

    /**
     * A single `ldp_vc` remains an object Presentation, but section 8.1 still requires the
     * credential-query value containing it to be an array.
     */
    @Test
    fun parseVpTokenWithSingleLdpObjectValue() {
        val ldpPresentation =
            buildJsonObject {
                put("type", "VerifiablePresentation")
            }
        val json =
            buildJsonObject {
                putJsonArray("ldp_query") { add(ldpPresentation) }
            }

        val token = VpToken.fromJson(json)

        assertEquals(1, token.presentationCount)
        assertIs<JsonObject>(token.getSinglePresentationElement("ldp_query"))
    }

    @Test
    fun convertVpTokenToJsonWithSinglePresentations() {
        val token =
            VpToken.fromStrings(
                mapOf(
                    "query1" to listOf("eyJhbGc1..."),
                    "query2" to listOf("eyJhbGc2..."),
                ),
            )
        val json = VpToken.run { token.toJson() }

        assertTrue(json is JsonObject)
        assertEquals(2, json.size)
        assertIs<JsonArray>(json["query1"])
        assertIs<JsonArray>(json["query2"])
        assertEquals(1, (json["query1"] as JsonArray).size)
        assertEquals(1, (json["query2"] as JsonArray).size)
    }

    @Test
    fun convertVpTokenToJsonWithMultiplePresentations() {
        val token =
            VpToken.fromStrings(
                mapOf(
                    "query1" to listOf("eyJhbGc1..."),
                    "query2" to listOf("eyJhbGc2...", "eyJhbGc3..."),
                ),
            )
        val json = VpToken.run { token.toJson() }

        assertTrue(json is JsonObject)
        // Both singleton and multiple presentation values are arrays.
        assertTrue(json["query1"] is JsonArray)
        assertTrue(json["query2"] is JsonArray)
        assertEquals(1, (json["query1"] as JsonArray).size)
        assertEquals(2, (json["query2"] as JsonArray).size)
    }

    @Test
    fun roundtripVpTokenThroughJson() {
        val original =
            VpToken.fromStrings(
                mapOf(
                    "driver_license" to listOf("eyJhbGc1..."),
                    "employment" to listOf("eyJhbGc2...", "eyJhbGc3..."),
                ),
            )
        val json = VpToken.run { original.toJson() }
        val parsed = VpToken.fromJson(json)

        assertEquals(original.presentationElements, parsed.presentationElements)
        assertEquals(original.presentationCount, parsed.presentationCount)
        assertEquals(original.queryIds, parsed.queryIds)
    }

    @Test
    fun builderCreatesCorrectVpToken() {
        val token =
            buildVpToken {
                presentation("query1", "eyJhbGc1...")
                presentation("query2", "eyJhbGc2...")
                presentation("query2", "eyJhbGc3...") // Add second to same query
            }

        assertEquals(2, token.presentations.size)
        assertEquals(3, token.presentationCount)
        assertEquals(1, token.getPresentation("query1")?.size)
        assertEquals(2, token.getPresentation("query2")?.size)
    }

    @Test
    fun allPresentationsReturnsFlatList() {
        val token =
            VpToken.fromStrings(
                mapOf(
                    "query1" to listOf("pres1"),
                    "query2" to listOf("pres2", "pres3"),
                ),
            )

        assertEquals(3, token.allPresentations.size)
        assertTrue(token.allPresentations.containsAll(listOf("pres1", "pres2", "pres3")))
    }

    @Test
    fun getSinglePresentationReturnsNullForUnknownQuery() {
        val token = vpTokenOf("query1", "pres1")
        assertNull(token.getSinglePresentation("unknown_query"))
    }

    @Test
    fun vpTokenOfWithPairsGroupsByQueryId() {
        val token =
            vpTokenOf(
                "query1" to "pres1",
                "query2" to "pres2",
                "query1" to "pres3", // Same query, different presentation
            )

        assertEquals(2, token.presentations.size)
        assertEquals(3, token.presentationCount)
        assertEquals(2, token.getPresentation("query1")?.size)
    }
}

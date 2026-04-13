/*
 * © 2025 Sphereon International B.V.
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
        val token = VpToken(mapOf(
            "driver_license_query" to listOf("eyJhbGc1..."),
            "employment_query" to listOf("eyJhbGc2...")
        ))
        
        assertEquals(2, token.presentations.size)
        assertEquals(2, token.presentationCount)
        assertEquals(setOf("driver_license_query", "employment_query"), token.queryIds)
    }

    @Test
    fun createVpTokenWithMultiplePresentationsForSameQuery() {
        val token = VpToken(mapOf(
            "employment_query" to listOf("eyJhbGc1...", "eyJhbGc2...", "eyJhbGc3...")
        ))
        
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
            VpToken(mapOf("" to listOf("eyJhbGc...")))
        }
    }

    @Test
    fun rejectEmptyPresentationsList() {
        assertFailsWith<IllegalArgumentException> {
            VpToken(mapOf("query1" to emptyList()))
        }
    }

    @Test
    fun rejectBlankPresentation() {
        assertFailsWith<IllegalArgumentException> {
            VpToken(mapOf("query1" to listOf("eyJhbGc...", "")))
        }
    }

    @Test
    fun parseVpTokenFromDcqlJsonObjectWithSinglePresentations() {
        val json = buildJsonObject {
            put("driver_license_query", "eyJhbGciOiJFUzI1NiJ9...")
            put("age_verification_query", "eyJhbGciOiJFUzI1NiJ9_2...")
        }
        val token = VpToken.fromJson(json)

        assertEquals(2, token.presentations.size)
        assertEquals("eyJhbGciOiJFUzI1NiJ9...", token.getSinglePresentation("driver_license_query"))
        assertEquals("eyJhbGciOiJFUzI1NiJ9_2...", token.getSinglePresentation("age_verification_query"))
    }

    @Test
    fun parseVpTokenFromDcqlJsonObjectWithArrayPresentations() {
        val json = buildJsonObject {
            put("driver_license_query", "eyJhbGc1...")
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
        val json = buildJsonObject {
            putJsonArray("query1") {
                add(JsonPrimitive("eyJhbGc..."))
                add(JsonPrimitive(456))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            VpToken.fromJson(json)
        }
    }

    @Test
    fun convertVpTokenToJsonWithSinglePresentations() {
        val token = VpToken(mapOf(
            "query1" to listOf("eyJhbGc1..."),
            "query2" to listOf("eyJhbGc2...")
        ))
        val json = VpToken.run { token.toJson() }

        assertTrue(json is JsonObject)
        assertEquals(2, json.size)
        // Single presentations serialize as strings
        assertTrue(json["query1"] is JsonPrimitive)
        assertTrue(json["query2"] is JsonPrimitive)
    }

    @Test
    fun convertVpTokenToJsonWithMultiplePresentations() {
        val token = VpToken(mapOf(
            "query1" to listOf("eyJhbGc1..."),
            "query2" to listOf("eyJhbGc2...", "eyJhbGc3...")
        ))
        val json = VpToken.run { token.toJson() }

        assertTrue(json is JsonObject)
        // Single presentation as string, multiple as array
        assertTrue(json["query1"] is JsonPrimitive)
        assertTrue(json["query2"] is JsonArray)
        assertEquals(2, (json["query2"] as JsonArray).size)
    }

    @Test
    fun roundtripVpTokenThroughJson() {
        val original = VpToken(mapOf(
            "driver_license" to listOf("eyJhbGc1..."),
            "employment" to listOf("eyJhbGc2...", "eyJhbGc3...")
        ))
        val json = VpToken.run { original.toJson() }
        val parsed = VpToken.fromJson(json)

        assertEquals(original.presentations, parsed.presentations)
        assertEquals(original.presentationCount, parsed.presentationCount)
        assertEquals(original.queryIds, parsed.queryIds)
    }

    @Test
    fun builderCreatesCorrectVpToken() {
        val token = buildVpToken {
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
        val token = VpToken(mapOf(
            "query1" to listOf("pres1"),
            "query2" to listOf("pres2", "pres3")
        ))

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
        val token = vpTokenOf(
            "query1" to "pres1",
            "query2" to "pres2",
            "query1" to "pres3" // Same query, different presentation
        )

        assertEquals(2, token.presentations.size)
        assertEquals(3, token.presentationCount)
        assertEquals(2, token.getPresentation("query1")?.size)
    }
}

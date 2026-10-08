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

package com.sphereon.crypto.jose.jwe

import com.sphereon.crypto.core.json.cryptoJsonSerializer
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for JweHeader serialization and deserialization.
 *
 * These tests verify that the JweHeader class correctly handles:
 * - Setting and getting header parameters
 * - Serializing to JSON
 * - Deserializing from JSON
 * - Round-trip serialization (serialize -> deserialize -> serialize)
 */
class JweHeaderTest {
    @Test
    fun testSetAndGetAlgAndEnc() {
        val header = JweHeader()

        // Set algorithm and encryption
        header.alg = "RSA-OAEP"
        header.enc = "A256GCM"

        // Verify getters work
        assertEquals("RSA-OAEP", header.alg, "alg should be RSA-OAEP")
        assertEquals("A256GCM", header.enc, "enc should be A256GCM")
    }

    @Test
    fun testSetMultipleParameters() {
        val header = JweHeader()

        // Set various parameters
        header.alg = "RSA-OAEP-256"
        header.enc = "A128GCM"
        header.kid = "test-key-id"
        header.zip = "DEF"

        // Verify all parameters
        assertEquals("RSA-OAEP-256", header.alg)
        assertEquals("A128GCM", header.enc)
        assertEquals("test-key-id", header.kid)
        assertEquals("DEF", header.zip)
    }

    @Test
    fun testMapInterfaceAccess() {
        val header = JweHeader()

        // Set via properties
        header.alg = "RSA-OAEP"
        header.enc = "A256GCM"

        // Access via Map interface
        assertNotNull(header["alg"], "alg should be accessible via Map interface")
        assertNotNull(header["enc"], "enc should be accessible via Map interface")
        assertTrue(header.containsKey("alg"), "Header should contain 'alg' key")
        assertTrue(header.containsKey("enc"), "Header should contain 'enc' key")
        assertEquals(2, header.size, "Header should have 2 entries")
    }

    @Test
    fun testSerializeToJson() {
        val header = JweHeader()
        header.alg = "RSA-OAEP"
        header.enc = "A256GCM"
        header.kid = "my-key-id"

        // Serialize to JSON
        val jsonString = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), header.underlying)

        // Verify JSON contains expected fields
        assertTrue(jsonString.contains("\"alg\""), "JSON should contain alg field")
        assertTrue(jsonString.contains("\"RSA-OAEP\""), "JSON should contain RSA-OAEP value")
        assertTrue(jsonString.contains("\"enc\""), "JSON should contain enc field")
        assertTrue(jsonString.contains("\"A256GCM\""), "JSON should contain A256GCM value")
        assertTrue(jsonString.contains("\"kid\""), "JSON should contain kid field")
        assertTrue(jsonString.contains("\"my-key-id\""), "JSON should contain my-key-id value")
    }

    @Test
    fun testDeserializeFromJson() {
        // JSON string representing a JWE header
        val jsonString = """{"alg":"RSA-OAEP","enc":"A256GCM","kid":"test-key"}"""

        // Deserialize
        val header = JweHeader.fromJson(jsonString)

        // Verify parameters
        assertEquals("RSA-OAEP", header.alg)
        assertEquals("A256GCM", header.enc)
        assertEquals("test-key", header.kid)
    }

    @Test
    fun testDeserializeFromJsonObject() {
        // Create JsonObject
        val jsonString = """{"alg":"RSA-OAEP-256","enc":"A128GCM","zip":"DEF"}"""
        val jsonObject = cryptoJsonSerializer.decodeFromString<JsonObject>(jsonString)

        // Deserialize
        val header = JweHeader.fromJson(jsonObject)

        // Verify parameters
        assertEquals("RSA-OAEP-256", header.alg)
        assertEquals("A128GCM", header.enc)
        assertEquals("DEF", header.zip)
    }

    @Test
    fun testRoundTripSerialization() {
        // Create header with various parameters
        val originalHeader = JweHeader()
        originalHeader.alg = "RSA-OAEP"
        originalHeader.enc = "A256GCM"
        originalHeader.kid = "round-trip-test"
        originalHeader.zip = "DEF"

        // Serialize to JSON string
        val jsonString = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), originalHeader.underlying)

        // Deserialize back to JweHeader
        val deserializedHeader = JweHeader.fromJson(jsonString)

        // Verify all parameters match
        assertEquals(originalHeader.alg, deserializedHeader.alg, "alg should match after round-trip")
        assertEquals(originalHeader.enc, deserializedHeader.enc, "enc should match after round-trip")
        assertEquals(originalHeader.kid, deserializedHeader.kid, "kid should match after round-trip")
        assertEquals(originalHeader.zip, deserializedHeader.zip, "zip should match after round-trip")
        assertEquals(originalHeader.size, deserializedHeader.size, "size should match after round-trip")
    }

    @Test
    fun testRoundTripWithModification() {
        // Start with JSON
        val jsonString = """{"alg":"RSA-OAEP","enc":"A256GCM"}"""
        val header = JweHeader.fromJson(jsonString)

        // Modify
        header.kid = "new-kid"
        header.zip = "DEF"

        // Serialize
        val modifiedJson = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), header.underlying)

        // Deserialize again
        val finalHeader = JweHeader.fromJson(modifiedJson)

        // Verify original and new parameters
        assertEquals("RSA-OAEP", finalHeader.alg)
        assertEquals("A256GCM", finalHeader.enc)
        assertEquals("new-kid", finalHeader.kid)
        assertEquals("DEF", finalHeader.zip)
    }

    @Test
    fun testUnderlyingJsonObjectUpdatesCorrectly() {
        val header = JweHeader()

        // Initially empty
        assertTrue(header.underlying.isEmpty(), "Initial underlying should be empty")

        // Set first parameter
        header.alg = "RSA-OAEP"
        assertEquals(1, header.underlying.size, "Underlying should have 1 entry after setting alg")
        assertTrue(header.underlying.containsKey("alg"), "Underlying should contain alg key")

        // Set second parameter
        header.enc = "A256GCM"
        assertEquals(2, header.underlying.size, "Underlying should have 2 entries after setting enc")
        assertTrue(header.underlying.containsKey("enc"), "Underlying should contain enc key")

        // Verify both parameters are in underlying
        assertEquals("RSA-OAEP", header.underlying["alg"]?.let { it.toString().trim('"') })
        assertEquals("A256GCM", header.underlying["enc"]?.let { it.toString().trim('"') })
    }

    @Test
    fun testMapInterfaceConsistencyWithUnderlying() {
        val header = JweHeader()

        header.alg = "RSA-OAEP"
        header.enc = "A256GCM"
        header.kid = "test-key"

        // Map interface methods should reflect current underlying state
        assertEquals(header.underlying.size, header.size, "Size should match underlying")
        assertEquals(header.underlying.keys, header.keys, "Keys should match underlying")
        assertEquals(header.underlying.isEmpty(), header.isEmpty(), "isEmpty should match underlying")

        // Verify containsKey matches underlying
        header.underlying.keys.forEach { key ->
            assertTrue(header.containsKey(key), "containsKey should return true for key: $key")
        }
    }

    @Test
    fun testComplexHeaderWithAllStandardFields() {
        val header = JweHeader()

        // Set all standard JWE header fields
        header.alg = "ECDH-ES+A128KW"
        header.enc = "A256CBC-HS512"
        header.zip = "DEF"
        header.kid = "complex-key-id"
        header.jku = "https://example.com/keys"
        header.x5u = "https://example.com/cert"
        header.typ = "JWE"
        header.cty = "application/json"

        // Serialize
        val jsonString = cryptoJsonSerializer.encodeToString(JsonObject.serializer(), header.underlying)

        // Deserialize
        val deserializedHeader = JweHeader.fromJson(jsonString)

        // Verify all fields
        assertEquals("ECDH-ES+A128KW", deserializedHeader.alg)
        assertEquals("A256CBC-HS512", deserializedHeader.enc)
        assertEquals("DEF", deserializedHeader.zip)
        assertEquals("complex-key-id", deserializedHeader.kid)
        assertEquals("https://example.com/keys", deserializedHeader.jku)
        assertEquals("https://example.com/cert", deserializedHeader.x5u)
        assertEquals("JWE", deserializedHeader.typ)
        assertEquals("application/json", deserializedHeader.cty)
    }
}

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

package com.sphereon.crypto.jose.jws

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for JWS utility functions
 */
class JwsUtilsTest {

    @Test
    @kotlin.js.JsName("payloadToBytes_should_convert_JSON_object_to_UTF8_bytes")
    fun `payloadToBytes should convert JSON object to UTF-8 bytes`() {
        val payload = JsonObject(mapOf("sub" to JsonPrimitive("1234567890"), "name" to JsonPrimitive("John Doe")))
        val bytes = JwsUtils.payloadToBytes(payload)

        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        // Should contain JSON serialization
        val str = bytes.decodeToString()
        assertTrue(str.contains("sub"))
        assertTrue(str.contains("1234567890"))
    }

    @Test
    @kotlin.js.JsName("payloadToBytes_should_convert_string_to_UTF8_bytes")
    fun `payloadToBytes should convert string to UTF-8 bytes`() {
        val payload = "Hello World"
        val bytes = JwsUtils.payloadToBytes(payload)

        assertNotNull(bytes)
        assertEquals("Hello World", bytes.decodeToString())
    }

    @Test
    @kotlin.js.JsName("payloadToBytes_should_pass_through_byte_array")
    fun `payloadToBytes should pass through byte array`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = JwsUtils.payloadToBytes(payload)

        assertNotNull(bytes)
        assertEquals(5, bytes.size)
        assertEquals(1, bytes[0])
        assertEquals(5, bytes[4])
    }

    @Test
    @kotlin.js.JsName("encodeBytesToBase64Url_should_produce_valid_base64url_encoding")
    fun `encodeBytesToBase64Url should produce valid base64url encoding`() {
        val input = "Hello World".encodeToByteArray()
        val encoded = JwsUtils.encodeBytesToBase64Url(input)

        assertNotNull(encoded)
        // Base64URL should not contain + / or =
        assertTrue(!encoded.contains("+"))
        assertTrue(!encoded.contains("/"))
        assertTrue(!encoded.contains("="))
    }

    @Test
    @kotlin.js.JsName("encodeJsonToBase64Url_should_encode_JSON_object")
    fun `encodeJsonToBase64Url should encode JSON object`() {
        val json = JsonObject(mapOf("alg" to JsonPrimitive("ES256"), "typ" to JsonPrimitive("JWT")))
        val encoded = JwsUtils.encodeJsonToBase64Url(json)

        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
        // Should be valid base64url
        assertTrue(!encoded.contains("+"))
        assertTrue(!encoded.contains("/"))
        assertTrue(!encoded.contains("="))
    }

    @Test
    @kotlin.js.JsName("decodeBase64UrlToJson_should_decode_to_JSON_object")
    fun `decodeBase64UrlToJson should decode to JSON object`() {
        val json = JsonObject(mapOf("alg" to JsonPrimitive("ES256")))
        val encoded = JwsUtils.encodeJsonToBase64Url(json)
        val decoded = JwsUtils.decodeBase64UrlToJson(encoded)

        assertNotNull(decoded)
        assertEquals("ES256", decoded["alg"]?.let { (it as? JsonPrimitive)?.content })
    }

    @Test
    @kotlin.js.JsName("createSigningInput_should_concatenate_header_and_payload_with_dot")
    fun `createSigningInput should concatenate header and payload with dot`() {
        val header = "eyJhbGciOiJFUzI1NiJ9"
        val payload = "eyJzdWIiOiIxMjM0NTY3ODkwIn0"

        val signingInput = JwsUtils.createSigningInput(header, payload)
        val expected = "$header.$payload"

        assertEquals(expected, signingInput.decodeToString())
    }

    @Test
    @kotlin.js.JsName("toGeneral_should_convert_compact_JWS_to_general_format")
    fun `toGeneral should convert compact JWS to general format`() {
        // Create a simple compact JWS (header.payload.signature)
        val header = JwsUtils.encodeJsonToBase64Url(JsonObject(mapOf("alg" to JsonPrimitive("ES256"))))
        val payload = JwsUtils.encodeBytesToBase64Url("test payload".encodeToByteArray())
        val signature = JwsUtils.encodeBytesToBase64Url(byteArrayOf(1, 2, 3, 4))

        val compact = JwsCompact("$header.$payload.$signature")
        val general = JwsUtils.toGeneral(compact)

        assertNotNull(general)
        assertEquals(payload, general.payload)
        assertEquals(1, general.signatures.size)
        assertEquals(header, general.signatures[0].protected)
        assertEquals(signature, general.signatures[0].signature)
    }

    @Test
    @kotlin.js.JsName("toGeneral_should_pass_through_general_JWS_unchanged")
    fun `toGeneral should pass through general JWS unchanged`() {
        val general = JwsJsonGeneral(
            payload = "eyJzdWIiOiIxMjM0In0",
            signatures = listOf(
                JwsJsonSignature(
                    protected = "eyJhbGciOiJFUzI1NiJ9",
                    signature = "AQIDBA"
                )
            )
        )

        val result = JwsUtils.toGeneral(general)

        assertEquals(general.payload, result.payload)
        assertEquals(general.signatures.size, result.signatures.size)
    }

    @Test
    @kotlin.js.JsName("toGeneral_should_convert_flattened_JWS_to_general_format")
    fun `toGeneral should convert flattened JWS to general format`() {
        val flattened = JwsJsonFlattened(
            payload = "eyJzdWIiOiIxMjM0In0",
            protected = "eyJhbGciOiJFUzI1NiJ9",
            signature = "AQIDBA"
        )

        val general = JwsUtils.toGeneral(flattened)

        assertEquals(flattened.payload, general.payload)
        assertEquals(1, general.signatures.size)
        assertEquals(flattened.protected, general.signatures[0].protected)
        assertEquals(flattened.signature, general.signatures[0].signature)
    }
}

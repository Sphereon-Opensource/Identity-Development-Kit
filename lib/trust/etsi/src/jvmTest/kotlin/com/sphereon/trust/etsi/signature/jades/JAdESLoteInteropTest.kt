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

package com.sphereon.trust.etsi.signature.jades

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Interoperability test for the trimmed-down EDK [JAdESValidator] against a real
 * JAdES Baseline-B signed trust list (ETSI TS 119 612 / WP4 "List of Trusted
 * Entries"), produced by the Python `jades_signer` reference implementation
 * (webuild-consortium/wp4-trust-group).
 *
 * The trust list embeds its signature under a top-level `signature` key as the
 * JWS JSON Serialization parts (RFC 7515 §7.2.2):
 *
 *     "signature": {
 *         "protected": "<BASE64URL(UTF8(JWS Protected Header))>",
 *         "signature": "<BASE64URL(JWS Signature)>"
 *     }
 *
 * The JWS payload is the document itself with the `signature` key removed,
 * serialized with `jwcrypto`'s `json_encode` (compact separators, recursively
 * sorted keys). We reconstruct the compact JWS `protected.payload.signature` and
 * feed it to the validator, which recomputes the signing input and verifies the
 * ES256 signature against the embedded `x5c` certificate.
 */
class JAdESLoteInteropTest {
    private val testContext = EtsiTestContext("jades-lote-interop", this)
    private val validator: JAdESValidator = testContext.jadesValidator

    private fun loadLote(): JsonObject {
        val text =
            requireNotNull(this::class.java.getResourceAsStream("/wrpac-providers-lote.json")) {
                "Missing test resource wrpac-providers-lote.json"
            }.readBytes().decodeToString()
        return Json.parseToJsonElement(text).jsonObject
    }

    /**
     * Canonical JSON serialization equivalent to `jwcrypto.common.json_encode`:
     * `json.dumps(obj, separators=(",", ":"), sort_keys=True)`. Object keys are
     * sorted recursively, scalars keep their parsed token, strings are JSON-escaped.
     */
    private fun canonicalize(element: JsonElement): String =
        when (element) {
            is JsonObject -> {
                element.entries
                    .sortedBy { it.key }
                    .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
                        Json.encodeToString(JsonElement.serializer(), JsonPrimitive(key)) + ":" + canonicalize(value)
                    }
            }

            is JsonArray -> {
                element.joinToString(separator = ",", prefix = "[", postfix = "]") { canonicalize(it) }
            }

            is JsonPrimitive -> {
                if (element.isString) Json.encodeToString(JsonElement.serializer(), element) else element.content
            }
        }

    private fun reconstructCompactJws(lote: JsonObject): String {
        val signature = lote.getValue("signature").jsonObject
        val protectedB64 = signature.getValue("protected").jsonPrimitive.content
        val signatureB64 = signature.getValue("signature").jsonPrimitive.content
        val payload = JsonObject(lote.filterKeys { it != "signature" })
        val payloadB64 = canonicalize(payload).encodeToByteArray().encodeTo(Encoding.BASE64URL)
        return "$protectedB64.$payloadB64.$signatureB64"
    }

    @Test
    fun verifiesRealLotESignature() =
        runTest {
            val lote = loadLote()
            val compactJws = reconstructCompactJws(lote)

            val result = validator.validate(compactJws.encodeToByteArray())

            assertTrue(result.signatureValid, "JAdES signature must verify. Errors: ${result.errors}")
            assertTrue(result.valid, "Overall validation must pass. Errors: ${result.errors}")
            assertTrue(result.certificateChain?.isNotEmpty() == true, "Signing certificate chain must be extracted")
        }

    @Test
    fun rejectsTamperedLotEPayload() =
        runTest {
            val lote = loadLote()
            val signature = lote.getValue("signature").jsonObject
            val protectedB64 = signature.getValue("protected").jsonPrimitive.content
            val signatureB64 = signature.getValue("signature").jsonPrimitive.content

            // Flip one byte of the payload so the signing input no longer matches.
            val payload = JsonObject(lote.filterKeys { it != "signature" })
            val tampered = canonicalize(payload).replaceFirst("WP4", "WPX")
            val payloadB64 = tampered.encodeToByteArray().encodeTo(Encoding.BASE64URL)
            val compactJws = "$protectedB64.$payloadB64.$signatureB64"

            val result = validator.validate(compactJws.encodeToByteArray())

            assertFalse(result.signatureValid, "Tampered payload must not verify")
        }
}

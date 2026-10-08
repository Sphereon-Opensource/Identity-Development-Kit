/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.sdjwt

import com.sphereon.core.api.encodeToBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RFC 9901 array-element disclosures: [salt, value] two-element form referenced from the
 * payload via {"...": "<digest>"} markers inside JSON arrays. The OIDF conformance suite's
 * PID credential uses these (e.g. nationalities), which is what originally exposed the
 * three-element-only parsing.
 */
class ArrayElementDisclosureTest {
    private fun encodeRawDisclosure(vararg elements: String): String {
        val json = elements.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        return json.encodeToByteArray().encodeToBase64Url()
    }

    @Test
    fun parsesTwoElementArrayDisclosureWithNullKey() {
        val encoded = encodeRawDisclosure("MbMa2aYpXYCAp3Jyvzhaqw", "FR")

        val disclosure = SdJwtCodec.parseDisclosure(encoded)

        assertNull(disclosure.key, "array-element disclosure has no claim name")
        assertEquals("MbMa2aYpXYCAp3Jyvzhaqw", disclosure.salt)
        assertEquals("FR", disclosure.value.jsonPrimitive.content)
    }

    @Test
    fun parsesThreeElementObjectDisclosureUnchanged() {
        val encoded = encodeRawDisclosure("salt-value", "given_name", "Erika")

        val disclosure = SdJwtCodec.parseDisclosure(encoded)

        assertEquals("given_name", disclosure.key)
        assertEquals("Erika", disclosure.value.jsonPrimitive.content)
    }

    @Test
    fun extractDigestsFindsArrayElementMarkers() {
        val payload =
            buildJsonObject {
                put("vct", "eu.europa.ec.eudi.pid.1")
                putJsonArray("nationalities") {
                    add(buildJsonObject { put(SdJwt.SD_ARRAY_ELEMENT_CLAIM, "digest-a") })
                    add(buildJsonObject { put(SdJwt.SD_ARRAY_ELEMENT_CLAIM, "digest-b") })
                }
                putJsonArray(SdJwt.SD_CLAIM) { add(Json.parseToJsonElement("\"digest-c\"")) }
            }

        val digests = DisclosureDigestUtil.extractDigests(payload)

        assertTrue(digests.containsAll(setOf("digest-a", "digest-b", "digest-c")), "got $digests")
    }

    @Test
    fun reconstructionUnveilsDisclosedArrayElementsAndDropsUndisclosedMarkers() {
        // Two markers in the payload array; only one disclosure attached. The disclosed element
        // must be unveiled in place, the undisclosed marker dropped entirely, and an object
        // element carrying a nested _sd must resolve its own object-property disclosure.
        val arrayDisclosureEncoded = encodeRawDisclosure("salt-arr", "FR")
        val arrayDigest = requireNotNull(SdJwtCodec.parseDisclosure(arrayDisclosureEncoded).digest)
        val nestedObjectDisclosureEncoded = encodeRawDisclosure("salt-obj", "region", "Bavaria")
        val nestedDigest = requireNotNull(SdJwtCodec.parseDisclosure(nestedObjectDisclosureEncoded).digest)

        val header = """{"alg":"ES256","typ":"dc+sd-jwt"}"""
        val body =
            """
            {
              "vct": "eu.europa.ec.eudi.pid.1",
              "nationalities": [
                {"${SdJwt.SD_ARRAY_ELEMENT_CLAIM}": "$arrayDigest"},
                {"${SdJwt.SD_ARRAY_ELEMENT_CLAIM}": "digest-of-an-undisclosed-element"}
              ],
              "address": {"${SdJwt.SD_CLAIM}": ["$nestedDigest"], "country": "DE"}
            }
            """.trimIndent()
        val sdJwtString =
            header.encodeToByteArray().encodeToBase64Url() + "." +
                body.encodeToByteArray().encodeToBase64Url() + ".signature" +
                "~$arrayDisclosureEncoded~$nestedObjectDisclosureEncoded~"

        val parsed = SdJwtCodec.parse(sdJwtString)
        assertTrue(parsed.isOk, "parse failed: ${if (parsed.isErr) parsed.error.message.defaultMessage else ""}")
        val full = parsed.value.payload.fullPayload

        val nationalities = full.getValue("nationalities").jsonArray
        assertEquals(1, nationalities.size, "undisclosed marker must be dropped, got $nationalities")
        assertEquals("FR", nationalities.single().jsonPrimitive.content)

        val address = full.getValue("address")
        assertEquals("Bavaria", address.jsonObject.getValue("region").jsonPrimitive.content)
        assertEquals("DE", address.jsonObject.getValue("country").jsonPrimitive.content)
    }

    @Test
    fun disclosureDigestVerificationCoversArrayElementDisclosure() {
        // End-to-end shape of the verifier's digest check: the encoded 2-element disclosure's
        // digest appears as an array marker and must round-trip through parse + digest calc.
        val encoded = encodeRawDisclosure("salt-x", "DE")
        val disclosure = SdJwtCodec.parseDisclosure(encoded)
        val digest = requireNotNull(disclosure.digest)

        val payload =
            buildJsonObject {
                putJsonArray("nationalities") {
                    add(buildJsonObject { put(SdJwt.SD_ARRAY_ELEMENT_CLAIM, digest) })
                }
            }

        assertTrue(DisclosureDigestUtil.extractDigests(payload).contains(digest))
    }
}

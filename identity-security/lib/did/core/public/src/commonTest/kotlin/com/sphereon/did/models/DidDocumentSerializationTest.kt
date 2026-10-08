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

package com.sphereon.did.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 1 (IDK-18) coverage for the lossless DID 1.1 domain model:
 *  - `controller` single-string/multi-list polymorphism
 *  - service `type` single-string/multi-list polymorphism
 *  - service `serviceEndpoint` string/object/array shapes
 *  - VM and document extension bags
 */
class DidDocumentSerializationTest {
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = false
        }

    // ---- controller: String | String[] ----

    @Test
    fun controllerSingleElementCollapsesToString() {
        val doc = DidDocument(id = "did:example:1", controller = listOf("did:example:ctrl"))
        val out = json.encodeToString(DidDocument.serializer(), doc)
        val tree = json.parseToJsonElement(out).jsonObject
        assertEquals("did:example:ctrl", tree["controller"]?.jsonPrimitive?.content)
    }

    @Test
    fun controllerMultiElementEmitsArray() {
        val doc =
            DidDocument(
                id = "did:example:1",
                controller = listOf("did:example:a", "did:example:b"),
            )
        val out = json.encodeToString(DidDocument.serializer(), doc)
        val tree = json.parseToJsonElement(out).jsonObject
        val arr = tree["controller"]?.jsonArray ?: error("controller should be array")
        assertEquals(2, arr.size)
        assertEquals("did:example:a", arr[0].jsonPrimitive.content)
    }

    @Test
    fun controllerAcceptsStringOnInput() {
        val input = """{"id":"did:example:1","controller":"did:example:ctrl"}"""
        val doc = json.decodeFromString(DidDocument.serializer(), input)
        assertEquals(listOf("did:example:ctrl"), doc.controller)
    }

    @Test
    fun controllerAcceptsArrayOnInput() {
        val input = """{"id":"did:example:1","controller":["did:example:a","did:example:b"]}"""
        val doc = json.decodeFromString(DidDocument.serializer(), input)
        assertEquals(listOf("did:example:a", "did:example:b"), doc.controller)
    }

    @Test
    fun controllerDefaultsToEmptyWhenAbsent() {
        val input = """{"id":"did:example:1"}"""
        val doc = json.decodeFromString(DidDocument.serializer(), input)
        assertTrue(doc.controller.isEmpty())
        assertEquals("did:example:1", doc.getPrimaryController())
    }

    // ---- service.type: String | String[] ----

    @Test
    fun serviceTypeSingleCollapses() {
        val svc =
            DidService(
                id = "did:x#svc-1",
                type = listOf("LinkedDomains"),
                serviceEndpoint = JsonPrimitive("https://example.com"),
            )
        val out = json.encodeToString(DidService.serializer(), svc)
        val tree = json.parseToJsonElement(out).jsonObject
        assertEquals("LinkedDomains", tree["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun serviceTypeMultiEmitsArray() {
        val svc =
            DidService(
                id = "did:x#svc-1",
                type = listOf("LinkedDomains", "CredentialRegistry"),
                serviceEndpoint = JsonPrimitive("https://example.com"),
            )
        val out = json.encodeToString(DidService.serializer(), svc)
        val tree = json.parseToJsonElement(out).jsonObject
        assertEquals(2, tree["type"]?.jsonArray?.size)
    }

    // ---- service.serviceEndpoint: string | object | array ----

    @Test
    fun serviceEndpointAsStringRoundTrips() {
        val input = """{"id":"did:x#s","type":"X","serviceEndpoint":"https://e.example"}"""
        val svc = json.decodeFromString(DidService.serializer(), input)
        assertEquals("https://e.example", svc.serviceEndpointAsStringOrNull())
        val re = json.encodeToString(DidService.serializer(), svc)
        assertEquals(
            "https://e.example",
            json
                .parseToJsonElement(re)
                .jsonObject["serviceEndpoint"]
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun serviceEndpointAsObjectRoundTrips() {
        val input = """{"id":"did:x#s","type":"X","serviceEndpoint":{"uri":"https://e.example","accept":["x"]}}"""
        val svc = json.decodeFromString(DidService.serializer(), input)
        val obj = svc.serviceEndpoint as? JsonObject ?: error("expected object endpoint")
        assertEquals("https://e.example", obj["uri"]?.jsonPrimitive?.content)
        assertEquals(1, obj["accept"]?.jsonArray?.size)
    }

    @Test
    fun serviceEndpointAsMixedArrayRoundTrips() {
        val input = """{"id":"did:x#s","type":"X","serviceEndpoint":["https://a","https://b",{"uri":"https://c"}]}"""
        val svc = json.decodeFromString(DidService.serializer(), input)
        val arr = svc.serviceEndpoint as? JsonArray ?: error("expected array endpoint")
        assertEquals(3, arr.size)
        assertTrue(arr[2] is JsonObject)
    }

    // ---- VM extension bag ----

    @Test
    fun verificationMethodExtensionBagRoundTrips() {
        val input = """{"id":"did:x#k1","type":"JsonWebKey2020","controller":"did:x","blockchainAccountId":"eip155:1:0xabc","sphereon:kid":"abc","custom":123}"""
        val vm = json.decodeFromString(VerificationMethod.serializer(), input)
        assertEquals("abc", vm.extensions["sphereon:kid"]?.jsonPrimitive?.content)
        assertNotNull(vm.extensions["custom"])
        val re = json.parseToJsonElement(json.encodeToString(VerificationMethod.serializer(), vm)).jsonObject
        assertEquals("abc", re["sphereon:kid"]?.jsonPrimitive?.content)
        assertTrue("extensions" !in re)
    }

    @Test
    fun verificationMethodLegacyPublicKeyEncodingsRoundTrip() {
        // DID-Core 1.0 documents commonly carry publicKeyBase58 (Ed25519VerificationKey2018) or
        // publicKeyHex / publicKeyPem. These are not in the W3C-known VM keys, so they must
        // round-trip through the [extensions] map without tripping the constructor invariant.
        val input = """{"id":"did:legacy:1#k1","type":"Ed25519VerificationKey2018","controller":"did:legacy:1","publicKeyBase58":"H3C2AVvLMv6gmMNam3uVAjZpfkcJCwDwnZn6z3wXmqPV"}"""
        val vm = json.decodeFromString(VerificationMethod.serializer(), input)
        assertNotNull(vm.extensions["publicKeyBase58"])
        val re = json.parseToJsonElement(json.encodeToString(VerificationMethod.serializer(), vm)).jsonObject
        assertEquals(
            "H3C2AVvLMv6gmMNam3uVAjZpfkcJCwDwnZn6z3wXmqPV",
            re["publicKeyBase58"]?.jsonPrimitive?.content,
        )
        assertTrue("extensions" !in re)
    }

    @Test
    fun verificationMethodLifecycleFieldsRoundTrip() {
        val expires = "2027-01-01T00:00:00Z"
        val input = """{"id":"did:x#k1","type":"JsonWebKey2020","controller":"did:x","expiresAt":"$expires","blockchainAccountId":"eip155:1:0xabc"}"""
        val vm = json.decodeFromString(VerificationMethod.serializer(), input)
        assertNotNull(vm.expiresAt)
        assertEquals("eip155:1:0xabc", vm.blockchainAccountId)
    }

    // ---- Document extension bag ----

    @Test
    fun documentExtensionBagRoundTrips() {
        val doc =
            DidDocument(
                id = "did:example:1",
                extensions =
                    mapOf(
                        "customKey" to JsonPrimitive("customValue"),
                        "nested" to buildJsonObject { put("k", JsonPrimitive("v")) },
                        "list" to
                            buildJsonArray {
                                add(JsonPrimitive(1))
                                add(JsonPrimitive(2))
                            },
                    ),
            )
        val out = json.encodeToString(DidDocument.serializer(), doc)
        val tree = json.parseToJsonElement(out).jsonObject
        assertEquals("customValue", tree["customKey"]?.jsonPrimitive?.content)
        assertTrue("extensions" !in tree)
        val re = json.decodeFromString(DidDocument.serializer(), out)
        assertEquals(3, re.extensions.size)
        assertEquals("customValue", re.extensions["customKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun documentExtensionsDefaultEmpty() {
        val doc = json.decodeFromString(DidDocument.serializer(), """{"id":"did:example:1"}""")
        assertTrue(doc.extensions.isEmpty())
    }

    @Test
    fun documentTopLevelUnknownKeysAreCapturedAsExtensions() {
        val input = """{"id":"did:example:1","keyAgreementSuites":["A"],"proof":{"type":"DataIntegrityProof"}}"""
        val doc = json.decodeFromString(DidDocument.serializer(), input)
        assertEquals(2, doc.extensions.size)
        assertEquals(
            "A",
            doc.extensions["keyAgreementSuites"]
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content
        )
        assertEquals(
            "DataIntegrityProof",
            doc.extensions["proof"]
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun serviceExtensionBagUsesTopLevelKeys() {
        val input = """{"id":"did:x#s","type":"X","serviceEndpoint":"https://e.example","routingKeys":["k1"]}"""
        val svc = json.decodeFromString(DidService.serializer(), input)
        assertEquals(
            "k1",
            svc.extensions["routingKeys"]
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content
        )
        val re = json.parseToJsonElement(json.encodeToString(DidService.serializer(), svc)).jsonObject
        assertEquals(
            "k1",
            re["routingKeys"]
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content
        )
        assertTrue("extensions" !in re)
    }
}

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

package com.sphereon.jsonld.loader

import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.WellKnownContexts
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuiltInContextLinkedDataDocumentLoaderTest {
    private val vcCredentialsV2Iri = "https://www.w3.org/ns/credentials/v2"

    /**
     * Synthetic stand-in for the W3C VCDM 2.0 context. Real bundling is a
     * follow-up content-curation step; the loader's correctness does not
     * depend on the body's contents, only on a present-vs-absent IRI lookup.
     */
    private val syntheticVcContext: JsonObject =
        buildJsonObject {
            put("@version", "1.1")
            put("@protected", true)
        }

    private val registry =
        MapBackedBuiltInContextRegistry(
            mapOf(vcCredentialsV2Iri to syntheticVcContext),
        )

    private val loader = BuiltInContextLinkedDataDocumentLoader(registry)

    @Test
    fun resolvesPinnedVcdm11ContextOffline() =
        runTest {
            val default = DefaultBuiltInContextRegistry()
            val loaderOverDefault = BuiltInContextLinkedDataDocumentLoader(default)
            val result = loaderOverDefault.loadDocument(WellKnownContexts.VCDM_1_1)
            assertTrue(result.isOk, "VCDM 1.1 context must resolve from the built-in registry")
            val document = result.value
            assertEquals(WellKnownContexts.VCDM_1_1, document.documentUrl)
            assertTrue(document.content is JsonObject)
        }

    @Test
    fun loadsRegisteredContext() =
        runTest {
            val result = loader.loadDocument(vcCredentialsV2Iri)
            assertTrue(result.isOk, "registered IRI should load")
            val doc = result.value
            assertEquals(vcCredentialsV2Iri, doc.documentUrl)
            assertEquals(syntheticVcContext, doc.content)
            assertEquals("application/ld+json", doc.contentType)
        }

    @Test
    fun returnsBuiltInContextNotFoundForUnregisteredIri() =
        runTest {
            val result = loader.loadDocument("https://example.com/some-other-context")
            assertTrue(result.isErr, "unregistered IRI must fail")
            // The loader interface is typed `IdkResult<*, JsonLdError>`, so the
            // error is already the typed sealed variant; no IdkError envelope or
            // sourceAs<>() round-trip is necessary at this seam.
            val notFound =
                assertNotNull(
                    result.error as? JsonLdError.BuiltInContextNotFound,
                    "expected BuiltInContextNotFound, got ${result.error::class.simpleName}",
                )
            assertEquals("https://example.com/some-other-context", notFound.iri)
        }

    @Test
    fun defaultRegistryBundlesUntpCriticalPathContexts() {
        val default = DefaultBuiltInContextRegistry()
        val expected =
            setOf(
                WellKnownContexts.VCDM_1_1,
                "https://www.w3.org/ns/credentials/v2",
                "https://w3id.org/security/data-integrity/v1",
                "https://w3id.org/security/data-integrity/v2",
                "https://vocabulary.uncefact.org/untp/",
            )
        assertEquals(expected, default.listIris())
    }

    @Test
    fun defaultRegistryReturnsParsedContextForVcdm2() {
        val default = DefaultBuiltInContextRegistry()
        val doc = assertNotNull(default.get("https://www.w3.org/ns/credentials/v2"))
        // Canonical W3C VCDM 2.0 context defines `@protected: true` at the top.
        // Asserting the key exists rather than the entire body so we don't
        // pin ourselves to upstream wording that may evolve across spec
        // editions; the SHA-256 manifest captures the exact bytes.
        assertTrue(doc.containsKey("@context"))
    }

    @Test
    fun defaultRegistryReturnsParsedContextForUntp() =
        runTest {
            val default = DefaultBuiltInContextRegistry()
            val loaderOverDefault = BuiltInContextLinkedDataDocumentLoader(default)
            val result = loaderOverDefault.loadDocument("https://vocabulary.uncefact.org/untp/")
            assertTrue(result.isOk, "UNTP context must resolve from the default registry")
            assertEquals("https://vocabulary.uncefact.org/untp/", result.value.documentUrl)
            assertEquals("application/ld+json", result.value.contentType)
        }

    @Test
    fun mapBackedRegistryListsAllIris() {
        val a = "https://example.com/a"
        val b = "https://example.com/b"
        val r =
            MapBackedBuiltInContextRegistry(
                mapOf(
                    a to JsonObject(emptyMap()),
                    b to JsonObject(emptyMap()),
                ),
            )
        assertEquals(setOf(a, b), r.listIris())
    }

    @Test
    fun loadedDocumentSerializesAsJsonLdMediaType() =
        runTest {
            val result = loader.loadDocument(vcCredentialsV2Iri)
            assertTrue(result.isOk)
            val json =
                Json {
                    encodeDefaults = false
                    explicitNulls = false
                }
            val encoded =
                json.encodeToString(
                    com.sphereon.jsonld.LinkedDataDocument
                        .serializer(),
                    result.value,
                )
            assertTrue(encoded.contains("\"contentType\":\"application/ld+json\""))
        }
}

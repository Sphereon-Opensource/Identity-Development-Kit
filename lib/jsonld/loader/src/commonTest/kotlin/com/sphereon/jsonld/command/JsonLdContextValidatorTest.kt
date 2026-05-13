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

package com.sphereon.jsonld.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Algorithm-level tests for [JsonLdContextValidator]. Bypass DI by
 * instantiating the validator directly with a fake document loader.
 *
 * The DI-wrapped command [ValidateJsonLdContextServiceCommandImpl] delegates
 * to this same validator, so behaviour through the OID4VCI chain is
 * identical; the integration test for that path is Sprint A2 task 8.
 */
class JsonLdContextValidatorTest {
    private fun bundleLoader(map: Map<String, JsonObject>): LinkedDataDocumentLoader =
        LinkedDataDocumentLoader { iri ->
            map[iri]?.let {
                Ok(LinkedDataDocument(documentUrl = iri, content = it, contentType = "application/ld+json"))
            } ?: Err(JsonLdError.LoadingDocumentFailed(iri = iri, reason = "no fixture for $iri"))
        }

    @Test
    fun acceptsCleanRemoteContext() =
        runTest {
            val vc =
                buildJsonObject {
                    put("@context", buildJsonObject { put("name", "https://schema.org/name") })
                }
            val validator =
                JsonLdContextValidator(
                    bundleLoader(mapOf("https://www.w3.org/ns/credentials/v2" to vc)),
                )
            val result =
                validator.validate(
                    ValidateJsonLdContextInput(context = JsonPrimitive("https://www.w3.org/ns/credentials/v2")),
                )
            assertTrue(result.isOk)
            assertEquals(listOf("https://www.w3.org/ns/credentials/v2"), result.value.resolvedContextIris)
        }

    @Test
    fun walksArrayOfRemoteContexts() =
        runTest {
            val vc = buildJsonObject { put("@context", buildJsonObject { put("name", "https://schema.org/name") }) }
            val di = buildJsonObject { put("@context", buildJsonObject { put("proof", "https://w3id.org/security#proof") }) }
            val validator =
                JsonLdContextValidator(
                    bundleLoader(
                        mapOf(
                            "https://www.w3.org/ns/credentials/v2" to vc,
                            "https://w3id.org/security/data-integrity/v2" to di,
                        ),
                    ),
                )
            val context =
                buildJsonArray {
                    add(JsonPrimitive("https://www.w3.org/ns/credentials/v2"))
                    add(JsonPrimitive("https://w3id.org/security/data-integrity/v2"))
                }
            val result = validator.validate(ValidateJsonLdContextInput(context = context))
            assertTrue(result.isOk)
            assertEquals(
                listOf(
                    "https://www.w3.org/ns/credentials/v2",
                    "https://w3id.org/security/data-integrity/v2",
                ),
                result.value.resolvedContextIris,
            )
        }

    @Test
    fun rejectsEmbeddedVocab() =
        runTest {
            val context =
                buildJsonObject {
                    put("@vocab", "https://example.com/")
                    put("name", "https://schema.org/name")
                }
            val validator = JsonLdContextValidator(bundleLoader(emptyMap()))
            val result = validator.validate(ValidateJsonLdContextInput(context = context))
            assertTrue(result.isErr)
            val typed = assertNotNull(result.error as? JsonLdError.InvalidVocabMapping)
            assertEquals("$.@context", typed.location)
        }

    @Test
    fun rejectsVocabInsideRemoteContext() =
        runTest {
            val malicious =
                buildJsonObject {
                    put("@context", buildJsonObject { put("@vocab", "https://example.com/") })
                }
            val validator =
                JsonLdContextValidator(
                    bundleLoader(mapOf("https://example.com/bad-context" to malicious)),
                )
            val result =
                validator.validate(
                    ValidateJsonLdContextInput(context = JsonPrimitive("https://example.com/bad-context")),
                )
            assertTrue(result.isErr)
            val typed = assertNotNull(result.error as? JsonLdError.InvalidVocabMapping)
            assertTrue(
                typed.location.contains("<https://example.com/bad-context>"),
                "location must point at the offending remote: was ${typed.location}",
            )
        }

    @Test
    fun rejectsRemoteContextThatFailsToLoad() =
        runTest {
            val validator = JsonLdContextValidator(bundleLoader(emptyMap()))
            val result =
                validator.validate(
                    ValidateJsonLdContextInput(context = JsonPrimitive("https://no-such.example/ctx")),
                )
            assertTrue(result.isErr)
            assertNotNull(result.error as? JsonLdError.LoadingDocumentFailed)
        }

    @Test
    fun rejectsMalformedIri() =
        runTest {
            val validator = JsonLdContextValidator(bundleLoader(emptyMap()))
            val result =
                validator.validate(
                    ValidateJsonLdContextInput(context = JsonPrimitive("https://example.com/has space")),
                )
            assertTrue(result.isErr)
            assertNotNull(result.error as? JsonLdError.InvalidIri)
        }

    @Test
    fun resolvesRelativeReferenceAgainstBaseIri() =
        runTest {
            val baseDoc = buildJsonObject { put("@context", buildJsonObject { put("name", "https://schema.org/name") }) }
            val validator =
                JsonLdContextValidator(
                    bundleLoader(mapOf("https://example.com/contexts/base.jsonld" to baseDoc)),
                )
            val result =
                validator.validate(
                    ValidateJsonLdContextInput(
                        context = JsonPrimitive("./base.jsonld"),
                        baseIri = "https://example.com/contexts/root.jsonld",
                    ),
                )
            assertTrue(result.isOk)
            assertEquals(
                listOf("https://example.com/contexts/base.jsonld"),
                result.value.resolvedContextIris,
            )
        }
}

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

package com.sphereon.openid.oid4vc.common.vcdm

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.sourceAs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VcdmModelTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun futureVersionRoundTripsWithoutEnumChanges() {
        val version = VcdmVersion("2.1")

        assertEquals(version, json.decodeFromString(json.encodeToString(version)))
    }

    @Test
    fun documentRetainsUnknownExtensionTermsLosslessly() {
        val raw = buildJsonObject {
            put("@context", "https://www.w3.org/ns/credentials/v2")
            put("type", buildJsonArray { add(JsonPrimitive("VerifiableCredential")); add(JsonPrimitive("ExampleCredential")) })
            put("example:extension", buildJsonObject { put("nested", 42) })
        }
        val document = VcdmDocument(VcdmVersion.V2_0, VcdmDocumentKind.CREDENTIAL, raw)
        val decoded = json.decodeFromString<VcdmDocument>(json.encodeToString(document))

        assertEquals(raw, document.json)
        assertEquals(document.version, decoded.version)
        assertEquals(document.kind, decoded.kind)
        assertEquals(raw, decoded.json)
    }

    @Test
    fun validationResultRejectsContradictoryValidityState() {
        assertFailsWith<IllegalArgumentException> {
            VcdmValidationResult(
                valid = true,
                errors = listOf(VcdmError.MissingBaseContext()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            VcdmValidationResult(valid = false)
        }
    }

    @Test
    fun typedErrorsPreserveCodesCategorySeverityMetadataAndSource() {
        val errors = listOf<VcdmError>(
            VcdmError.UnsupportedVersion(listOf("credential.@context")),
            VcdmError.AmbiguousVersion(listOf("credential.@context", "credential.version")),
            VcdmError.MissingBaseContext(),
            VcdmError.ContradictoryDocumentShape("credentialSubject must be an object"),
            VcdmError.UnsupportedDocumentKind(listOf("ExampleDocument")),
        )
        val expectedMetadata = listOf(
            mapOf<String, Any?>("contexts" to listOf("credential.@context")),
            mapOf<String, Any?>("contexts" to listOf("credential.@context", "credential.version")),
            mapOf<String, Any?>("contexts" to emptyList<String>()),
            mapOf<String, Any?>("reason" to "credentialSubject must be an object"),
            mapOf<String, Any?>("types" to listOf("ExampleDocument")),
        )

        errors.zip(expectedMetadata).forEach { (typed, metadata) ->
            val wrapped = IdkError.fromDTO(typed)

            assertEquals(ErrorCategory.VALIDATION, wrapped.category)
            assertEquals(IdkError.Severity.ERROR, wrapped.severity)
            assertSame(typed, wrapped.source)
            assertEquals(typed.code, wrapped.code)
            assertEquals(metadata, wrapped.meta)
            assertEquals(metadata.keys, wrapped.meta.keys)
        }

        assertEquals("VCDM_UNSUPPORTED_VERSION", errors[0].code)
        assertEquals("VCDM_AMBIGUOUS_VERSION", errors[1].code)
        assertEquals("VCDM_MISSING_BASE_CONTEXT", errors[2].code)
        assertEquals("VCDM_CONTRADICTORY_DOCUMENT_SHAPE", errors[3].code)
        assertEquals("VCDM_UNSUPPORTED_DOCUMENT_KIND", errors[4].code)
    }

    @Test
    fun typedErrorSourceAsRecoversEachConcreteVariant() {
        val unsupportedVersion = VcdmError.UnsupportedVersion(listOf("a"))
        val ambiguousVersion = VcdmError.AmbiguousVersion(listOf("a", "b"))
        val missingBaseContext = VcdmError.MissingBaseContext()
        val contradictoryShape = VcdmError.ContradictoryDocumentShape("reason")
        val unsupportedKind = VcdmError.UnsupportedDocumentKind(listOf("Unknown"))

        assertSame(unsupportedVersion, IdkError.fromDTO(unsupportedVersion).sourceAs<VcdmError.UnsupportedVersion>())
        assertSame(ambiguousVersion, IdkError.fromDTO(ambiguousVersion).sourceAs<VcdmError.AmbiguousVersion>())
        assertSame(missingBaseContext, IdkError.fromDTO(missingBaseContext).sourceAs<VcdmError.MissingBaseContext>())
        assertSame(contradictoryShape, IdkError.fromDTO(contradictoryShape).sourceAs<VcdmError.ContradictoryDocumentShape>())
        assertSame(unsupportedKind, IdkError.fromDTO(unsupportedKind).sourceAs<VcdmError.UnsupportedDocumentKind>())
    }

    @Test
    fun vcdmProfilesRequireTheirBaseContextAsTheFirstContext() {
        val v2OutOfOrder = buildJsonObject {
            put("@context", buildJsonArray {
                add(JsonPrimitive("https://example.com/extension"))
                add(JsonPrimitive(VcdmProfiles.V2_0_CONTEXT))
            })
            put("type", buildJsonArray { add(JsonPrimitive("VerifiableCredential")) })
            put("issuer", JsonPrimitive("https://issuer.example"))
            put("credentialSubject", buildJsonObject { put("id", JsonPrimitive("https://subject.example")) })
        }
        val v2First = buildJsonObject {
            put("@context", buildJsonArray {
                add(JsonPrimitive(VcdmProfiles.V2_0_CONTEXT))
                add(JsonPrimitive("https://example.com/extension"))
            })
            put("type", buildJsonArray { add(JsonPrimitive("VerifiableCredential")) })
            put("issuer", JsonPrimitive("https://issuer.example"))
            put("credentialSubject", buildJsonObject { put("id", JsonPrimitive("https://subject.example")) })
        }

        assertTrue(VcdmProfiles.v2_0.validateCredential(v2OutOfOrder).valid.not())
        assertTrue(VcdmProfiles.v2_0.validateCredential(v2First).valid)

        val v1OutOfOrder = buildJsonObject {
            put("@context", buildJsonArray {
                add(JsonPrimitive("https://example.com/extension"))
                add(JsonPrimitive(VcdmProfiles.V1_1_CONTEXT))
            })
            put("type", buildJsonArray { add(JsonPrimitive("VerifiableCredential")) })
            put("issuer", JsonPrimitive("https://issuer.example"))
            put("issuanceDate", JsonPrimitive("2026-08-26T09:00:00Z"))
            put("credentialSubject", buildJsonObject { put("id", JsonPrimitive("https://subject.example")) })
        }
        val v1First = buildJsonObject {
            put("@context", buildJsonArray {
                add(JsonPrimitive(VcdmProfiles.V1_1_CONTEXT))
                add(JsonPrimitive("https://example.com/extension"))
            })
            put("type", buildJsonArray { add(JsonPrimitive("VerifiableCredential")) })
            put("issuer", JsonPrimitive("https://issuer.example"))
            put("issuanceDate", JsonPrimitive("2026-08-26T09:00:00Z"))
            put("credentialSubject", buildJsonObject { put("id", JsonPrimitive("https://subject.example")) })
        }
        assertTrue(VcdmProfiles.v1_1.validateCredential(v1OutOfOrder).valid.not())
        assertTrue(VcdmProfiles.v1_1.validateCredential(v1First).valid)
    }
}

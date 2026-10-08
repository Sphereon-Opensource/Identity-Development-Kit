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

package com.sphereon.jsonld

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.sourceAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Verifies that `JsonLdError` integrates with the IDK error model: each typed
 * variant survives `IdkError.fromDTO` and is recoverable via `sourceAs<>()`,
 * with the spec-mandated category mapping (validation vs. unavailable vs.
 * not-found) reaching the wire envelope.
 */
class JsonLdErrorTest {
    @Test
    fun loadingDocumentFailedRoundTripsThroughIdkError() {
        val typed =
            JsonLdError.LoadingDocumentFailed(
                iri = "https://vocabulary.uncefact.org/untp/",
                reason = "connect timeout",
            )
        val wrapped = IdkError.fromDTO(typed)

        assertEquals("JSONLD_LOADING_DOCUMENT_FAILED", wrapped.code)
        assertEquals(ErrorCategory.UNAVAILABLE, wrapped.category)
        assertSame(typed, wrapped.source, "fromDTO must preserve the typed instance")

        val recovered = assertNotNull(wrapped.sourceAs<JsonLdError.LoadingDocumentFailed>())
        assertEquals("https://vocabulary.uncefact.org/untp/", recovered.iri)
        assertEquals("connect timeout", recovered.reason)
    }

    @Test
    fun integrityPinMismatchIsFatalValidation() {
        val typed =
            JsonLdError.IntegrityPinMismatch(
                iri = "https://www.w3.org/ns/credentials/v2",
                expectedSha256 = "abc",
                actualSha256 = "def",
            )
        val wrapped = IdkError.fromDTO(typed)

        assertEquals(ErrorCategory.VALIDATION, wrapped.category)
        assertEquals(IdkError.Severity.FATAL, wrapped.severity)
        val recovered = assertNotNull(wrapped.sourceAs<JsonLdError.IntegrityPinMismatch>())
        assertEquals("abc", recovered.expectedSha256)
        assertEquals("def", recovered.actualSha256)
    }

    @Test
    fun documentNotAllowedIsFatalForbidden() {
        val typed =
            JsonLdError.DocumentNotAllowed(
                iri = "https://attacker.example/context",
                reason = "redirect target is not trusted",
            )
        val wrapped = IdkError.fromDTO(typed)

        assertEquals("JSONLD_DOCUMENT_NOT_ALLOWED", wrapped.code)
        assertEquals(ErrorCategory.FORBIDDEN, wrapped.category)
        assertEquals(IdkError.Severity.FATAL, wrapped.severity)
        val recovered = assertNotNull(wrapped.sourceAs<JsonLdError.DocumentNotAllowed>())
        assertEquals("https://attacker.example/context", recovered.iri)
    }

    @Test
    fun invalidVocabMappingDefaultsToUntpReason() {
        val typed = JsonLdError.InvalidVocabMapping(location = "$.@context[0]")
        assertEquals(
            "@vocab is forbidden by UNTP 0.7.0 Verifiable Credentials Profile",
            typed.reason,
        )
        val wrapped = IdkError.fromDTO(typed)
        assertEquals(ErrorCategory.VALIDATION, wrapped.category)
        assertEquals("$.@context[0]", wrapped.meta["location"])
    }

    @Test
    fun jsonSchemaValidationFailedExposesViolationsInMeta() {
        val violations =
            listOf(
                JsonLdError.JsonSchemaValidationFailed.SchemaViolation(
                    pointer = "/credentialSubject/identifier",
                    message = "expected string, got number",
                ),
                JsonLdError.JsonSchemaValidationFailed.SchemaViolation(
                    pointer = "/issuer/id",
                    message = "must match format 'uri'",
                ),
            )
        val typed =
            JsonLdError.JsonSchemaValidationFailed(
                schemaUri = "https://test.example/dpp.json",
                violations = violations,
            )
        val wrapped = IdkError.fromDTO(typed)

        @Suppress("UNCHECKED_CAST")
        val metaViolations = wrapped.meta["violations"] as List<Map<String, Any?>>
        assertEquals(2, metaViolations.size)
        assertEquals("/credentialSubject/identifier", metaViolations[0]["pointer"])
        assertEquals("expected string, got number", metaViolations[0]["message"])

        val recovered = assertNotNull(wrapped.sourceAs<JsonLdError.JsonSchemaValidationFailed>())
        assertEquals(2, recovered.violations.size)
    }

    @Test
    fun sourceAsReturnsNullForWrongVariant() {
        val typed = JsonLdError.BuiltInContextNotFound(iri = "https://example/missing")
        val wrapped = IdkError.fromDTO(typed)
        assertNotNull(wrapped.sourceAs<JsonLdError.BuiltInContextNotFound>())
        assertNull(wrapped.sourceAs<JsonLdError.LoadingDocumentFailed>())
    }
}

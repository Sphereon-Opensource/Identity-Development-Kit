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

package com.sphereon.openid.oid4vp.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for verifier attestation validation models.
 *
 * OpenID4VP 1.0 Final Section 12 defines the Verifier Attestation JWT format.
 */
class VerifyVerifierAttestationTest {
    // ==========================================================================
    // VerifyVerifierAttestationArgs Tests
    // ==========================================================================

    @Test
    fun verifyVerifierAttestationArgsConstructionWithAllParameters() {
        val args =
            VerifyVerifierAttestationArgs(
                attestationJwt =
                    "eyJ0eXAiOiJ2ZXJpZmllci1hdHRlc3RhdGlvbitqd3QiLCJhbGciOiJFUzI1NiJ9" +
                        ".eyJpc3MiOiJodHRwczovL2F0dGVzdGF0aW9uLXByb3ZpZGVyLmV4YW1wbGUuY29tIiwic3ViIjoidmVyaWZpZXIu" +
                        "ZXhhbXBsZSIsImV4cCI6MTcwNDA2NzIwMCwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2In19fQ" +
                        ".signature",
                expectedClientId = "verifier.example",
                trustedIssuers = listOf("https://attestation-provider.example.com"),
            )

        assertEquals("verifier.example", args.expectedClientId)
        assertEquals(1, args.trustedIssuers.size)
        assertEquals("https://attestation-provider.example.com", args.trustedIssuers[0])
        assertNull(args.jarSignerJwk)
    }

    @Test
    fun verifyVerifierAttestationArgsWithMultipleTrustedIssuers() {
        val args =
            VerifyVerifierAttestationArgs(
                attestationJwt = "header.payload.signature",
                expectedClientId = "my-verifier",
                trustedIssuers =
                    listOf(
                        "https://issuer1.example.com",
                        "https://issuer2.example.com",
                        "https://issuer3.example.com",
                    ),
            )

        assertEquals(3, args.trustedIssuers.size)
    }

    // ==========================================================================
    // VerifyVerifierAttestationResult Tests
    // ==========================================================================

    @Test
    fun verifyVerifierAttestationResultWithValidResult() {
        val result =
            VerifyVerifierAttestationResult(
                valid = true,
                issuer = "https://attestation-provider.example.com",
                subject = "verifier.example",
                expirationTime = 1704067200L,
                issuedAt = 1703980800L,
            )

        assertTrue(result.valid)
        assertEquals("https://attestation-provider.example.com", result.issuer)
        assertEquals("verifier.example", result.subject)
        assertEquals(1704067200L, result.expirationTime)
        assertEquals(1703980800L, result.issuedAt)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun verifyVerifierAttestationResultWithValidationErrors() {
        val result =
            VerifyVerifierAttestationResult(
                valid = false,
                issuer = "https://untrusted-issuer.example.com",
                subject = "wrong-subject",
                errors =
                    listOf(
                        VerifierAttestationValidationError(
                            type = ClientIdValidationErrorType.ATTESTATION_ISSUER_NOT_TRUSTED,
                            message = "Attestation issuer is not trusted",
                            details = "Issuer 'https://untrusted-issuer.example.com' is not in the trusted issuers list",
                        ),
                        VerifierAttestationValidationError(
                            type = ClientIdValidationErrorType.ATTESTATION_SUBJECT_MISMATCH,
                            message = "Subject does not match client_id",
                            details = "Expected 'verifier.example', got 'wrong-subject'",
                        ),
                    ),
            )

        assertFalse(result.valid)
        assertEquals(2, result.errors.size)
        assertEquals(ClientIdValidationErrorType.ATTESTATION_ISSUER_NOT_TRUSTED, result.errors[0].type)
        assertEquals(ClientIdValidationErrorType.ATTESTATION_SUBJECT_MISMATCH, result.errors[1].type)
    }

    @Test
    fun verifyVerifierAttestationResultWithExpiredAttestationError() {
        val result =
            VerifyVerifierAttestationResult(
                valid = false,
                issuer = "https://attestation-provider.example.com",
                subject = "verifier.example",
                expirationTime = 1609459200L, // January 1, 2021 - expired
                errors =
                    listOf(
                        VerifierAttestationValidationError(
                            type = ClientIdValidationErrorType.ATTESTATION_EXPIRED,
                            message = "Attestation JWT has expired",
                            details = "Expiration time: 1609459200",
                        ),
                    ),
            )

        assertFalse(result.valid)
        assertEquals(1, result.errors.size)
        assertEquals(ClientIdValidationErrorType.ATTESTATION_EXPIRED, result.errors[0].type)
    }

    @Test
    fun verifyVerifierAttestationResultWithMissingClaimsError() {
        val result =
            VerifyVerifierAttestationResult(
                valid = false,
                errors =
                    listOf(
                        VerifierAttestationValidationError(
                            type = ClientIdValidationErrorType.ATTESTATION_CLAIMS_MISSING,
                            message = "Missing required claims",
                            details = "Missing: iss, sub, cnf.jwk",
                        ),
                    ),
            )

        assertFalse(result.valid)
        assertEquals(ClientIdValidationErrorType.ATTESTATION_CLAIMS_MISSING, result.errors[0].type)
        assertTrue(result.errors[0].details?.contains("iss") == true)
    }

    @Test
    fun verifyVerifierAttestationResultWithCnfMismatchError() {
        val result =
            VerifyVerifierAttestationResult(
                valid = false,
                issuer = "https://attestation-provider.example.com",
                subject = "verifier.example",
                errors =
                    listOf(
                        VerifierAttestationValidationError(
                            type = ClientIdValidationErrorType.ATTESTATION_CNF_MISMATCH,
                            message = "Attestation cnf.jwk does not match JAR signer",
                            details = "The public key in the attestation does not match the key used to sign the JAR",
                        ),
                    ),
            )

        assertFalse(result.valid)
        assertEquals(ClientIdValidationErrorType.ATTESTATION_CNF_MISMATCH, result.errors[0].type)
    }

    // ==========================================================================
    // VerifierAttestationValidationError Tests
    // ==========================================================================

    @Test
    fun verifierAttestationValidationErrorConstruction() {
        val error =
            VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_SIGNATURE_INVALID,
                message = "Attestation JWT signature is invalid",
                details = "Signature verification failed with algorithm ES256",
            )

        assertEquals(ClientIdValidationErrorType.ATTESTATION_SIGNATURE_INVALID, error.type)
        assertTrue(error.message.contains("signature"))
        assertTrue(error.details?.contains("ES256") == true)
    }

    @Test
    fun verifierAttestationValidationErrorWithoutDetails() {
        val error =
            VerifierAttestationValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_JWT_MISSING,
                message = "Verifier attestation JWT is missing",
            )

        assertEquals(ClientIdValidationErrorType.ATTESTATION_JWT_MISSING, error.type)
        assertNull(error.details)
    }

    // ==========================================================================
    // ClientIdValidationErrorType Attestation Error Types Tests
    // ==========================================================================

    @Test
    fun allAttestationRelatedErrorTypesExist() {
        val attestationErrorTypes =
            listOf(
                ClientIdValidationErrorType.ATTESTATION_JWT_MISSING,
                ClientIdValidationErrorType.ATTESTATION_SIGNATURE_INVALID,
                ClientIdValidationErrorType.ATTESTATION_ISSUER_NOT_TRUSTED,
                ClientIdValidationErrorType.ATTESTATION_SUBJECT_MISMATCH,
                ClientIdValidationErrorType.ATTESTATION_EXPIRED,
                ClientIdValidationErrorType.ATTESTATION_CLAIMS_MISSING,
                ClientIdValidationErrorType.ATTESTATION_CNF_MISMATCH,
                ClientIdValidationErrorType.JAR_SIGNER_JWK_MISSING,
            )

        // Verify all error types are distinct
        assertEquals(attestationErrorTypes.size, attestationErrorTypes.toSet().size)
        assertEquals(8, attestationErrorTypes.size)
    }

    // ==========================================================================
    // VerifierAttestation Model Integration Tests
    // ==========================================================================

    @Test
    fun verifierAttestationJwtConstantsHasCorrectValues() {
        assertEquals("verifier-attestation+jwt", VerifierAttestationJwtConstants.TYP_HEADER)
        assertEquals(4, VerifierAttestationJwtConstants.REQUIRED_CLAIMS.size)
        assertTrue(VerifierAttestationJwtConstants.REQUIRED_CLAIMS.contains("iss"))
        assertTrue(VerifierAttestationJwtConstants.REQUIRED_CLAIMS.contains("sub"))
        assertTrue(VerifierAttestationJwtConstants.REQUIRED_CLAIMS.contains("exp"))
        assertTrue(VerifierAttestationJwtConstants.REQUIRED_CLAIMS.contains("cnf"))
    }

    @Test
    fun validateClientIdArgsSupportsVerifierAttestationFields() {
        val parsed =
            ParsedClientId(
                clientIdScheme = ClientIdScheme.VERIFIER_ATTESTATION,
                clientId = "verifier.example",
                clientIdWithScheme = "verifier_attestation:verifier.example",
            )

        val args =
            ValidateClientIdArgs(
                parsedClientId = parsed,
                jarUsed = true,
                jarAttestationJwt = "header.payload.signature",
                trustedAttestationIssuers = listOf("https://trusted-issuer.example.com"),
            )

        assertEquals(ClientIdScheme.VERIFIER_ATTESTATION, args.parsedClientId.clientIdScheme)
        assertTrue(args.jarUsed)
        assertEquals("header.payload.signature", args.jarAttestationJwt)
        assertEquals(1, args.trustedAttestationIssuers?.size)
    }
}

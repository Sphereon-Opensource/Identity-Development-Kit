/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the credential_identifier validation logic from HandleCredentialRequestCommandImpl
 * (OID4VCI 1.1 Section 9.3.1.2).
 *
 * The validation rule: when the request contains a credential_identifier, it MUST be
 * present in the token's authorization_details credential_identifiers list.
 * If the token has no identifiers (null or empty), the check is bypassed.
 * If the request uses credential_configuration_id instead, the check is bypassed.
 *
 * This test extracts the validation logic into a standalone helper to test in isolation.
 */
class CredentialIdentifierValidationTest {
    /**
     * Mirrors the credential_identifier validation logic from HandleCredentialRequestCommandImpl:
     *
     * Returns null if the identifier is valid (or check is not applicable),
     * otherwise returns an error message.
     */
    private fun validateCredentialIdentifier(
        requestedIdentifier: String?,
        tokenIdentifiers: List<String>?,
    ): String? {
        if (requestedIdentifier != null) {
            if (!tokenIdentifiers.isNullOrEmpty() && requestedIdentifier !in tokenIdentifiers) {
                return "Requested credential_identifier '$requestedIdentifier' is not in token authorization_details"
            }
        }
        return null
    }

    // ========================================================================
    // Request with credentialIdentifier matching token context passes
    // ========================================================================

    @Test
    fun identifierMatchingTokenContextPasses() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = "UniversityDegree-id-1",
                tokenIdentifiers = listOf("UniversityDegree-id-1", "UniversityDegree-id-2"),
            )

        assertNull(result, "Matching identifier should pass validation")
    }

    // ========================================================================
    // Request with credentialIdentifier NOT in token context returns error
    // ========================================================================

    @Test
    fun identifierNotInTokenContextReturnsError() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = "UnknownCredential-id",
                tokenIdentifiers = listOf("UniversityDegree-id-1", "UniversityDegree-id-2"),
            )

        assertTrue(result != null, "Non-matching identifier should fail validation")
        assertTrue(result.contains("UnknownCredential-id"), "Error should mention the requested identifier")
    }

    // ========================================================================
    // Request with credentialIdentifier when token has empty list passes (no restriction)
    // ========================================================================

    @Test
    fun identifierWithEmptyTokenIdentifiersListPasses() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = "AnyCredential-id",
                tokenIdentifiers = emptyList(),
            )

        assertNull(result, "Empty token identifiers list should bypass validation (no restriction)")
    }

    // ========================================================================
    // Request with credentialIdentifier when token has null identifiers passes
    // ========================================================================

    @Test
    fun identifierWithNullTokenIdentifiersPasses() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = "AnyCredential-id",
                tokenIdentifiers = null,
            )

        assertNull(result, "Null token identifiers should bypass validation (no restriction)")
    }

    // ========================================================================
    // Request with credentialConfigurationId (no identifier) bypasses check
    // ========================================================================

    @Test
    fun noIdentifierBypassesCheck() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = null,
                tokenIdentifiers = listOf("UniversityDegree-id-1"),
            )

        assertNull(result, "When no identifier is requested, validation should be bypassed")
    }

    @Test
    fun noIdentifierWithNullTokenContextBypasses() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = null,
                tokenIdentifiers = null,
            )

        assertNull(result, "Both null should bypass validation")
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    fun identifierMatchesSingleElementList() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = "only-id",
                tokenIdentifiers = listOf("only-id"),
            )

        assertNull(result, "Identifier matching the single element in token context should pass")
    }

    @Test
    fun identifierCaseSensitiveCheck() {
        val result =
            validateCredentialIdentifier(
                requestedIdentifier = "UniversityDegree-ID-1",
                tokenIdentifiers = listOf("UniversityDegree-id-1"),
            )

        assertTrue(result != null, "Identifier check should be case-sensitive per spec")
    }
}

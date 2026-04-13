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

package com.sphereon.openid.oid4vp.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for client ID validation models and parsing.
 */
class ValidateClientIdTest {

    // ==========================================================================
    // Client ID Scheme Parsing Tests
    // ==========================================================================

    @Test
    fun parsePreRegisteredClientIdWithoutPrefix() {
        val scheme = ClientIdScheme.fromClientId("my-client-123")
        assertEquals(ClientIdScheme.PRE_REGISTERED, scheme)
    }

    @Test
    fun parseRedirectUriScheme() {
        val scheme = ClientIdScheme.fromClientId("redirect_uri:https://client.example.org/cb")
        assertEquals(ClientIdScheme.REDIRECT_URI, scheme)
    }

    @Test
    fun parseX509SanDnsScheme() {
        val scheme = ClientIdScheme.fromClientId("x509_san_dns:verifier.example.com")
        assertEquals(ClientIdScheme.X509_SAN_DNS, scheme)
    }

    @Test
    fun parseX509SanUriScheme() {
        val scheme = ClientIdScheme.fromClientId("x509_san_uri:https://verifier.example.com")
        assertEquals(ClientIdScheme.X509_SAN_URI, scheme)
    }

    @Test
    fun parseX509HashScheme() {
        val scheme = ClientIdScheme.fromClientId("x509_hash:Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk")
        assertEquals(ClientIdScheme.X509_HASH, scheme)
    }

    @Test
    fun parseVerifierAttestationScheme() {
        val scheme = ClientIdScheme.fromClientId("verifier_attestation:verifier.example")
        assertEquals(ClientIdScheme.VERIFIER_ATTESTATION, scheme)
    }

    @Test
    fun parseOpenidFederationScheme() {
        val scheme = ClientIdScheme.fromClientId("openid_federation:https://federation-verifier.example.com")
        assertEquals(ClientIdScheme.OPENID_FEDERATION, scheme)
    }

    @Test
    fun parseDecentralizedIdentifierScheme() {
        val scheme = ClientIdScheme.fromClientId("decentralized_identifier:did:example:123")
        assertEquals(ClientIdScheme.DECENTRALIZED_IDENTIFIER, scheme)
    }

    @Test
    fun parseOriginScheme() {
        val scheme = ClientIdScheme.fromClientId("origin:https://browser.example.com")
        assertEquals(ClientIdScheme.ORIGIN, scheme)
    }

    @Test
    fun unknownPrefixFallsBackToPreRegistered() {
        val scheme = ClientIdScheme.fromClientId("unknown_scheme:some-value")
        assertEquals(ClientIdScheme.PRE_REGISTERED, scheme)
    }

    // ==========================================================================
    // Client ID Extraction Tests
    // ==========================================================================

    @Test
    fun extractClientIdWithoutSchemePrefix() {
        val clientId = ClientIdScheme.extractClientIdWithoutScheme("my-client-123")
        assertEquals("my-client-123", clientId)
    }

    @Test
    fun extractClientIdWithRedirectUriScheme() {
        val clientId = ClientIdScheme.extractClientIdWithoutScheme("redirect_uri:https://client.example.org/cb")
        assertEquals("https://client.example.org/cb", clientId)
    }

    @Test
    fun extractClientIdWithX509SanDnsScheme() {
        val clientId = ClientIdScheme.extractClientIdWithoutScheme("x509_san_dns:verifier.example.com")
        assertEquals("verifier.example.com", clientId)
    }

    @Test
    fun extractDidFromDecentralizedIdentifierScheme() {
        // Note: DIDs contain colons, so only first colon is the prefix separator
        val clientId = ClientIdScheme.extractClientIdWithoutScheme("decentralized_identifier:did:example:123")
        assertEquals("did:example:123", clientId)
    }

    // ==========================================================================
    // ParsedClientId Tests
    // ==========================================================================

    @Test
    fun parsedClientIdConversionToIdentifierContext() {
        val parsed = ParsedClientId(
            clientIdScheme = ClientIdScheme.X509_SAN_DNS,
            clientId = "verifier.example.com",
            clientIdWithScheme = "x509_san_dns:verifier.example.com"
        )
        
        val context = parsed.toIdentifierContext(
            issuer = "https://issuer.example.com",
            metadata = mapOf("key" to "value")
        )
        
        assertEquals("verifier.example.com", context.clientId)
        assertEquals("x509_san_dns", context.clientIdScheme)
        assertEquals("https://issuer.example.com", context.issuer)
        assertEquals("value", context.metadata["key"])
    }

    // ==========================================================================
    // ValidateClientIdArgs Tests
    // ==========================================================================

    @Test
    fun validateClientIdArgsConstruction() {
        val parsed = ParsedClientId(
            clientIdScheme = ClientIdScheme.REDIRECT_URI,
            clientId = "https://client.example.org/cb",
            clientIdWithScheme = "redirect_uri:https://client.example.org/cb"
        )
        
        val args = ValidateClientIdArgs(
            parsedClientId = parsed,
            jarUsed = false,
            redirectUri = "https://client.example.org/cb",
            responseUri = null
        )
        
        assertEquals(ClientIdScheme.REDIRECT_URI, args.parsedClientId.clientIdScheme)
        assertFalse(args.jarUsed)
        assertEquals("https://client.example.org/cb", args.redirectUri)
    }

    // ==========================================================================
    // ValidateClientIdResult Tests
    // ==========================================================================

    @Test
    fun validateClientIdResultWithValidResult() {
        val result = ValidateClientIdResult(
            valid = true,
            scheme = ClientIdScheme.PRE_REGISTERED,
            clientId = "my-client-123"
        )
        
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
        assertEquals(ClientIdScheme.PRE_REGISTERED, result.scheme)
    }

    @Test
    fun validateClientIdResultWithValidationErrors() {
        val result = ValidateClientIdResult(
            valid = false,
            scheme = ClientIdScheme.REDIRECT_URI,
            clientId = "https://wrong.example.org",
            errors = listOf(
                ClientIdValidationError(
                    type = ClientIdValidationErrorType.REDIRECT_URI_MISMATCH,
                    message = "client_id does not match redirect_uri",
                    details = "client_id='https://wrong.example.org', redirect_uri='https://correct.example.org'"
                )
            )
        )
        
        assertFalse(result.valid)
        assertEquals(1, result.errors.size)
        assertEquals(ClientIdValidationErrorType.REDIRECT_URI_MISMATCH, result.errors[0].type)
    }

    // ==========================================================================
    // ClientIdValidationError Tests
    // ==========================================================================

    @Test
    fun clientIdValidationErrorTypes() {
        // Test all error types are accessible
        val errorTypes = listOf(
            ClientIdValidationErrorType.JAR_NOT_ALLOWED,
            ClientIdValidationErrorType.JAR_REQUIRED,
            ClientIdValidationErrorType.CERTIFICATE_MISSING,
            ClientIdValidationErrorType.CERTIFICATE_VALIDATION_FAILED,
            ClientIdValidationErrorType.SAN_DNS_MISMATCH,
            ClientIdValidationErrorType.SAN_URI_MISMATCH,
            ClientIdValidationErrorType.CERTIFICATE_HASH_MISMATCH,
            ClientIdValidationErrorType.REDIRECT_URI_MISMATCH,
            ClientIdValidationErrorType.SCHEME_NOT_SUPPORTED,
            ClientIdValidationErrorType.VALIDATION_ERROR
        )
        
        assertEquals(10, errorTypes.size)
    }

    @Test
    fun clientIdValidationErrorConstructionWithDetails() {
        val error = ClientIdValidationError(
            type = ClientIdValidationErrorType.SAN_DNS_MISMATCH,
            message = "SAN DNS name does not match client_id",
            details = "client_id='wrong.example.com', SAN DNS names=[verifier.example.com, www.verifier.example.com]"
        )
        
        assertEquals(ClientIdValidationErrorType.SAN_DNS_MISMATCH, error.type)
        assertTrue(error.message.contains("SAN DNS"))
        assertTrue(error.details?.contains("wrong.example.com") == true)
    }
}

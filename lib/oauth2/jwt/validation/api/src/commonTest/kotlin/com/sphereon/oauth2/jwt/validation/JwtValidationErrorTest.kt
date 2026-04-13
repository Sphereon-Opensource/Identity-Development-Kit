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

package com.sphereon.oauth2.jwt.validation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtValidationErrorTest {
    @Test
    fun testMissingToken() {
        val error = JwtValidationError.missingToken()

        assertEquals(JwtValidationErrorType.MISSING_TOKEN, error.type)
        assertTrue(error.message.contains("required"))
        assertNull(error.issuer)
    }

    @Test
    fun testInvalidFormat() {
        val error = JwtValidationError.invalidFormat("Token does not have 3 parts")

        assertEquals(JwtValidationErrorType.INVALID_TOKEN_FORMAT, error.type)
        assertTrue(error.message.contains("3 parts"))
    }

    @Test
    fun testSignatureInvalid() {
        val error = JwtValidationError.signatureInvalid("https://auth.example.com")

        assertEquals(JwtValidationErrorType.SIGNATURE_INVALID, error.type)
        assertEquals("https://auth.example.com", error.issuer)
        assertTrue(error.message.contains("signature"))
    }

    @Test
    fun testExpired() {
        val error = JwtValidationError.expired(1735689600L)

        assertEquals(JwtValidationErrorType.TOKEN_EXPIRED, error.type)
        assertTrue(error.message.contains("1735689600"))
    }

    @Test
    fun testNotYetValid() {
        val error = JwtValidationError.notYetValid(1735689600L)

        assertEquals(JwtValidationErrorType.TOKEN_NOT_YET_VALID, error.type)
        assertTrue(error.message.contains("1735689600"))
    }

    @Test
    fun testUntrustedIssuer() {
        val error =
            JwtValidationError.untrustedIssuer(
                issuer = "https://evil.com",
                trustedIssuers = listOf("https://auth.example.com"),
            )

        assertEquals(JwtValidationErrorType.UNTRUSTED_ISSUER, error.type)
        assertEquals("https://evil.com", error.issuer)
        assertTrue(error.message.contains("evil.com"))
    }

    @Test
    fun testInvalidAudience() {
        val error =
            JwtValidationError.invalidAudience(
                tokenAudience = listOf("other-api"),
                expectedAudience = "my-api",
            )

        assertEquals(JwtValidationErrorType.INVALID_AUDIENCE, error.type)
        assertEquals("my-api", error.audience)
    }

    @Test
    fun testMissingClaim() {
        val error = JwtValidationError.missingClaim("sub")

        assertEquals(JwtValidationErrorType.MISSING_REQUIRED_CLAIM, error.type)
        assertEquals("sub", error.claim)
        assertTrue(error.message.contains("sub"))
    }

    @Test
    fun testJwksUnavailable() {
        val error =
            JwtValidationError.jwksUnavailable(
                issuer = "https://auth.example.com",
                cause = "Connection refused",
            )

        assertEquals(JwtValidationErrorType.JWKS_UNAVAILABLE, error.type)
        assertEquals("https://auth.example.com", error.issuer)
        assertEquals("Connection refused", error.cause)
    }

    @Test
    fun testKeyNotFound() {
        val error =
            JwtValidationError.keyNotFound(
                kid = "key-123",
                issuer = "https://auth.example.com",
            )

        assertEquals(JwtValidationErrorType.KEY_NOT_FOUND, error.type)
        assertTrue(error.message.contains("key-123"))
        assertEquals("https://auth.example.com", error.issuer)
    }

    @Test
    fun testAlgorithmNotAllowed() {
        val error =
            JwtValidationError.algorithmNotAllowed(
                algorithm = "none",
                allowedAlgorithms = listOf("RS256", "ES256"),
            )

        assertEquals(JwtValidationErrorType.ALGORITHM_NOT_ALLOWED, error.type)
        assertTrue(error.message.contains("none"))
    }

    @Test
    fun testIdpConfigurationError() {
        val error = JwtValidationError.idpConfigurationError("Missing issuer URL")

        assertEquals(JwtValidationErrorType.IDP_CONFIGURATION_ERROR, error.type)
        assertEquals("Missing issuer URL", error.message)
    }

    @Test
    fun testDiscoveryFailed() {
        val error =
            JwtValidationError.discoveryFailed(
                issuer = "https://auth.example.com",
                cause = "404 Not Found",
            )

        assertEquals(JwtValidationErrorType.DISCOVERY_FAILED, error.type)
        assertEquals("https://auth.example.com", error.issuer)
        assertEquals("404 Not Found", error.cause)
    }

    @Test
    fun testValidationError() {
        val error =
            JwtValidationError.validationError(
                message = "Unexpected error during validation",
                cause = "NullPointerException",
            )

        assertEquals(JwtValidationErrorType.VALIDATION_ERROR, error.type)
        assertEquals("NullPointerException", error.cause)
    }

    @Test
    fun testToIdkError() {
        val error = JwtValidationError.expired(1735689600L)
        val idkError = error.toIdkError()

        assertEquals("JWT_TOKEN_EXPIRED", idkError.code)
        assertNotNull(idkError.message)
        assertTrue(idkError.message.defaultMessage.contains("1735689600"))
    }

    @Test
    fun testSerialization() {
        val error =
            JwtValidationError.untrustedIssuer(
                issuer = "https://evil.com",
                trustedIssuers = listOf("https://auth.example.com"),
            )

        val json = Json.encodeToString(error)
        val deserialized = Json.decodeFromString<JwtValidationError>(json)

        assertEquals(error, deserialized)
    }

    @Test
    fun testAllErrorTypes() {
        val allTypes = JwtValidationErrorType.entries

        assertTrue(allTypes.contains(JwtValidationErrorType.MISSING_TOKEN))
        assertTrue(allTypes.contains(JwtValidationErrorType.INVALID_TOKEN_FORMAT))
        assertTrue(allTypes.contains(JwtValidationErrorType.SIGNATURE_INVALID))
        assertTrue(allTypes.contains(JwtValidationErrorType.TOKEN_EXPIRED))
        assertTrue(allTypes.contains(JwtValidationErrorType.TOKEN_NOT_YET_VALID))
        assertTrue(allTypes.contains(JwtValidationErrorType.UNTRUSTED_ISSUER))
        assertTrue(allTypes.contains(JwtValidationErrorType.INVALID_AUDIENCE))
        assertTrue(allTypes.contains(JwtValidationErrorType.MISSING_REQUIRED_CLAIM))
        assertTrue(allTypes.contains(JwtValidationErrorType.JWKS_UNAVAILABLE))
        assertTrue(allTypes.contains(JwtValidationErrorType.KEY_NOT_FOUND))
        assertTrue(allTypes.contains(JwtValidationErrorType.ALGORITHM_NOT_ALLOWED))
        assertTrue(allTypes.contains(JwtValidationErrorType.IDP_CONFIGURATION_ERROR))
        assertTrue(allTypes.contains(JwtValidationErrorType.DISCOVERY_FAILED))
        assertTrue(allTypes.contains(JwtValidationErrorType.VALIDATION_ERROR))
    }
}

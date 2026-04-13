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

package com.sphereon.oauth2.common

import com.sphereon.oauth2.common.model.IdTokenPayload
import com.sphereon.oauth2.common.validation.isAuthenticationFresh
import com.sphereon.oauth2.common.validation.isIdTokenExpired
import com.sphereon.oauth2.common.validation.isIdTokenIssuedAtValid
import com.sphereon.oauth2.common.validation.validateAudience
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for ID Token validation logic
 *
 * These tests verify the validation rules without requiring full JWT infrastructure.
 * Full ID Token validation with JWT signature verification is tested in integration tests.
 */
class IdTokenValidationTest {
    @Test
    fun `test ID token not expired when exp is in future`() {
        val now = Clock.System.now().epochSeconds
        val exp = now + 3600 // Expires in 1 hour

        val isExpired = isIdTokenExpired(exp, clockSkewSeconds = 60)
        assertFalse(isExpired, "Token should not be expired when exp is in future")
    }

    @Test
    fun `test ID token expired when exp is in past`() {
        val now = Clock.System.now().epochSeconds
        val exp = now - 3600 // Expired 1 hour ago

        val isExpired = isIdTokenExpired(exp, clockSkewSeconds = 60)
        assertTrue(isExpired, "Token should be expired when exp is in past")
    }

    @Test
    fun `test ID token not expired within clock skew`() {
        val now = Clock.System.now().epochSeconds
        val exp = now - 30 // Expired 30 seconds ago, but within 60 second clock skew

        val isExpired = isIdTokenExpired(exp, clockSkewSeconds = 60)
        assertFalse(isExpired, "Token should not be expired within clock skew tolerance")
    }

    @Test
    fun `test issued-at time valid when in past`() {
        val now = Clock.System.now().epochSeconds
        val iat = now - 60 // Issued 1 minute ago

        val isValid = isIdTokenIssuedAtValid(iat, clockSkewSeconds = 60)
        assertTrue(isValid, "iat should be valid when in past")
    }

    @Test
    fun `test issued-at time invalid when in future`() {
        val now = Clock.System.now().epochSeconds
        val iat = now + 120 // Issued 2 minutes in future (beyond clock skew)

        val isValid = isIdTokenIssuedAtValid(iat, clockSkewSeconds = 60)
        assertFalse(isValid, "iat should be invalid when too far in future")
    }

    @Test
    fun `test authentication is fresh when within max age`() {
        val now = Clock.System.now().epochSeconds
        val authTime = now - 60 // Authenticated 1 minute ago
        val maxAge = 300L // Max age 5 minutes

        val isFresh = isAuthenticationFresh(authTime, maxAge, clockSkewSeconds = 60)
        assertTrue(isFresh, "Authentication should be fresh within max age")
    }

    @Test
    fun `test authentication is stale when beyond max age`() {
        val now = Clock.System.now().epochSeconds
        val authTime = now - 600 // Authenticated 10 minutes ago
        val maxAge = 300L // Max age 5 minutes

        val isFresh = isAuthenticationFresh(authTime, maxAge, clockSkewSeconds = 60)
        assertFalse(isFresh, "Authentication should be stale beyond max age")
    }

    @Test
    fun `test audience validation with single string audience`() {
        val aud = listOf("client-123")
        val expectedAudience = "client-123"

        val isValid = validateAudience(aud, expectedAudience)
        assertTrue(isValid, "Should validate single string audience")
    }

    @Test
    fun `test audience validation fails with wrong single audience`() {
        val aud = listOf("client-456")
        val expectedAudience = "client-123"

        val isValid = validateAudience(aud, expectedAudience)
        assertFalse(isValid, "Should fail validation with wrong audience")
    }

    @Test
    fun `test audience validation with array containing expected audience`() {
        val aud = listOf("client-123", "client-456")
        val expectedAudience = "client-123"

        val isValid = validateAudience(aud, expectedAudience)
        assertTrue(isValid, "Should validate array audience containing expected value")
    }

    @Test
    fun `test audience validation fails with array not containing expected audience`() {
        val aud = listOf("client-456", "client-789")
        val expectedAudience = "client-123"

        val isValid = validateAudience(aud, expectedAudience)
        assertFalse(isValid, "Should fail validation when array doesn't contain expected audience")
    }

    @Test
    fun `test ID token payload structure validation`() {
        val now = Clock.System.now().epochSeconds
        val payload =
            IdTokenPayload(
                iss = "https://issuer.example.com",
                sub = "user-12345",
                aud = listOf("client-123"),
                exp = now + 3600,
                iat = now,
            )

        // Basic validation - check required fields are present
        assertTrue(payload.iss.isNotEmpty(), "Issuer should not be empty")
        assertTrue(payload.sub.isNotEmpty(), "Subject should not be empty")
        assertTrue(payload.exp > now, "Expiration should be in future")
        assertTrue(payload.iat <= now + 60, "Issued-at should not be too far in future")
    }

    @Test
    fun `test ID token payload with optional claims`() {
        val now = Clock.System.now().epochSeconds
        val payload =
            IdTokenPayload(
                iss = "https://issuer.example.com",
                sub = "user-12345",
                aud = listOf("client-123"),
                exp = now + 3600,
                iat = now,
                authTime = now - 60,
                nonce = "random-nonce-123",
                acr = "urn:mace:incommon:iap:silver",
                amr = listOf("pwd", "mfa"),
                azp = "client-123",
                atHash = "base64url-encoded-hash",
                cHash = "base64url-encoded-hash",
            )

        // Verify optional claims are preserved
        assertTrue(payload.authTime != null, "auth_time should be present")
        assertTrue(payload.nonce == "random-nonce-123", "nonce should match")
        assertTrue(payload.acr == "urn:mace:incommon:iap:silver", "acr should match")
        assertTrue(payload.amr?.contains("mfa") == true, "amr should contain mfa")
    }

    @Test
    fun `test audience serialization with single value`() {
        val now = Clock.System.now().epochSeconds
        val payload =
            IdTokenPayload(
                iss = "https://issuer.example.com",
                sub = "user-12345",
                aud = listOf("client-123"),
                exp = now + 3600,
                iat = now,
            )

        val json = Json.encodeToString(IdTokenPayload.serializer(), payload)
        val decoded = Json.decodeFromString(IdTokenPayload.serializer(), json)

        // Verify single audience is serialized as string in JSON
        assertTrue(json.contains("\"aud\":\"client-123\""), "Single audience should serialize as string")
        assertEquals(listOf("client-123"), decoded.aud, "Should deserialize back to single-element list")
    }

    @Test
    fun `test audience serialization with multiple values`() {
        val now = Clock.System.now().epochSeconds
        val payload =
            IdTokenPayload(
                iss = "https://issuer.example.com",
                sub = "user-12345",
                aud = listOf("client-123", "client-456"),
                exp = now + 3600,
                iat = now,
            )

        val json = Json.encodeToString(IdTokenPayload.serializer(), payload)
        val decoded = Json.decodeFromString(IdTokenPayload.serializer(), json)

        // Verify multiple audiences are serialized as array in JSON
        assertTrue(json.contains("\"aud\":["), "Multiple audiences should serialize as array")
        assertEquals(listOf("client-123", "client-456"), decoded.aud, "Should deserialize back to list")
    }

    @Test
    fun `test audience deserialization from string`() {
        val now = Clock.System.now().epochSeconds
        val json =
            """
            {
                "iss": "https://issuer.example.com",
                "sub": "user-12345",
                "aud": "client-123",
                "exp": ${now + 3600},
                "iat": $now
            }
            """.trimIndent()

        val decoded = Json.decodeFromString(IdTokenPayload.serializer(), json)

        assertEquals(listOf("client-123"), decoded.aud, "String audience should deserialize to single-element list")
    }

    @Test
    fun `test audience deserialization from array`() {
        val now = Clock.System.now().epochSeconds
        val json =
            """
            {
                "iss": "https://issuer.example.com",
                "sub": "user-12345",
                "aud": ["client-123", "client-456"],
                "exp": ${now + 3600},
                "iat": $now
            }
            """.trimIndent()

        val decoded = Json.decodeFromString(IdTokenPayload.serializer(), json)

        assertEquals(listOf("client-123", "client-456"), decoded.aud, "Array audience should deserialize to list")
    }

    @Test
    fun `test UserInfo claims are serialized at top level`() {
        val now = Clock.System.now().epochSeconds
        val payload =
            IdTokenPayload(
                iss = "https://issuer.example.com",
                sub = "user-12345",
                aud = listOf("client-123"),
                exp = now + 3600,
                iat = now,
                name = "John Doe",
                email = "john@example.com",
                emailVerified = true,
                givenName = "John",
                familyName = "Doe",
            )

        val json = Json.encodeToString(IdTokenPayload.serializer(), payload)

        // Verify UserInfo claims are at top level, not nested
        assertTrue(json.contains("\"name\":\"John Doe\""), "name should be top-level")
        assertTrue(json.contains("\"email\":\"john@example.com\""), "email should be top-level")
        assertTrue(json.contains("\"email_verified\":true"), "email_verified should be top-level")
        assertTrue(json.contains("\"given_name\":\"John\""), "given_name should be top-level")
        assertTrue(json.contains("\"family_name\":\"Doe\""), "family_name should be top-level")
        assertFalse(json.contains("_additional"), "Should not have _additional property")
    }

    @Test
    fun `test custom claims are deserialized and serialized at top level`() {
        val now = Clock.System.now().epochSeconds
        val json =
            """
            {
                "iss": "https://issuer.example.com",
                "sub": "user-12345",
                "aud": "client-123",
                "exp": ${now + 3600},
                "iat": $now,
                "custom_claim_1": "custom_value",
                "custom_claim_2": 42,
                "custom_claim_3": true
            }
            """.trimIndent()

        val decoded = Json.decodeFromString(IdTokenPayload.serializer(), json)

        // Verify custom claims are captured
        assertEquals(3, decoded.additionalClaims.size, "Should have 3 custom claims")
        assertTrue(decoded.additionalClaims.containsKey("custom_claim_1"), "Should have custom_claim_1")
        assertTrue(decoded.additionalClaims.containsKey("custom_claim_2"), "Should have custom_claim_2")
        assertTrue(decoded.additionalClaims.containsKey("custom_claim_3"), "Should have custom_claim_3")

        // Re-serialize and verify custom claims are at top level
        val reEncoded = Json.encodeToString(IdTokenPayload.serializer(), decoded)
        assertTrue(reEncoded.contains("\"custom_claim_1\":\"custom_value\""), "custom_claim_1 should be at top level")
        assertTrue(reEncoded.contains("\"custom_claim_2\":42"), "custom_claim_2 should be at top level")
        assertTrue(reEncoded.contains("\"custom_claim_3\":true"), "custom_claim_3 should be at top level")
    }

    @Test
    fun `test custom claims do not overwrite standard claims`() {
        val now = Clock.System.now().epochSeconds

        // Create payload with both standard and custom claims
        val payload =
            IdTokenPayload(
                iss = "https://issuer.example.com",
                sub = "user-12345",
                aud = listOf("client-123"),
                exp = now + 3600,
                iat = now,
                name = "John Doe",
                additionalClaims =
                    mapOf(
                        "org_id" to kotlinx.serialization.json.JsonPrimitive("org-456"),
                        "roles" to
                            kotlinx.serialization.json.JsonArray(
                                listOf(
                                    kotlinx.serialization.json.JsonPrimitive("admin"),
                                    kotlinx.serialization.json.JsonPrimitive("user"),
                                ),
                            ),
                    ),
            )

        val json = Json.encodeToString(IdTokenPayload.serializer(), payload)

        // Verify both standard and custom claims are present
        assertTrue(json.contains("\"name\":\"John Doe\""), "Standard claim should be present")
        assertTrue(json.contains("\"org_id\":\"org-456\""), "Custom claim should be present")
        assertTrue(json.contains("\"roles\":["), "Custom array claim should be present")

        // Decode and verify
        val decoded = Json.decodeFromString(IdTokenPayload.serializer(), json)
        assertEquals("John Doe", decoded.name, "Standard claim should be preserved")
        assertEquals(2, decoded.additionalClaims.size, "Should have 2 custom claims")
    }
}

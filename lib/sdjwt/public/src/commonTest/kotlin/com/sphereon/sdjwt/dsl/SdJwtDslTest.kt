/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.sdjwt.dsl

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdJwtDslTest {

    @Test
    fun buildSimpleSdJwtPayload() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            sub("user-123")
            claimSd("email", "user@example.com")
        }

        // Verify claims
        assertEquals("https://issuer.example.com", payload.claims["iss"]?.jsonPrimitive?.content)
        assertEquals("user-123", payload.claims["sub"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", payload.claims["email"]?.jsonPrimitive?.content)

        // Verify SD metadata
        assertEquals(setOf("email"), payload.sdClaims)
        assertNull(payload.minimumDigests)
    }

    @Test
    fun buildPayloadWithStandardJwtClaims() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            sub("user-123")
            aud("https://verifier.example.com")
            exp(1735689600L)
            nbf(1735686000L)
            iat(1735686000L)
            jti("jwt-id-12345")
        }

        assertEquals("https://issuer.example.com", payload.claims["iss"]?.jsonPrimitive?.content)
        assertEquals("user-123", payload.claims["sub"]?.jsonPrimitive?.content)
        assertEquals("https://verifier.example.com", payload.claims["aud"]?.jsonPrimitive?.content)
        assertEquals(1735689600L, payload.claims["exp"]?.jsonPrimitive?.content?.toLong())
        assertEquals(1735686000L, payload.claims["nbf"]?.jsonPrimitive?.content?.toLong())
        assertEquals(1735686000L, payload.claims["iat"]?.jsonPrimitive?.content?.toLong())
        assertEquals("jwt-id-12345", payload.claims["jti"]?.jsonPrimitive?.content)

        // None of the standard claims should be SD
        assertTrue(payload.sdClaims.isEmpty())
    }

    @Test
    fun buildPayloadWithSdStandardClaims() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            subSd("user-123")
            iatSd(1735686000L)
            jtiSd("jwt-id-12345")
        }

        // Claims should be present
        assertEquals("user-123", payload.claims["sub"]?.jsonPrimitive?.content)
        assertEquals(1735686000L, payload.claims["iat"]?.jsonPrimitive?.content?.toLong())
        assertEquals("jwt-id-12345", payload.claims["jti"]?.jsonPrimitive?.content)

        // These claims should be marked as SD
        assertEquals(setOf("sub", "iat", "jti"), payload.sdClaims)
    }

    @Test
    fun buildPayloadWithCustomClaimTypes() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            claim("name", "John Doe")
            claim("age", 30)
            claim("verified", true)
            claimSd("ssn", "123-45-6789")
            claimSd("score", 95)
            claimSd("active", false)
        }

        // Verify non-SD claims
        assertEquals("John Doe", payload.claims["name"]?.jsonPrimitive?.content)
        assertEquals(30, payload.claims["age"]?.jsonPrimitive?.int)
        assertEquals(true, payload.claims["verified"]?.jsonPrimitive?.boolean)

        // Verify SD claims
        assertEquals("123-45-6789", payload.claims["ssn"]?.jsonPrimitive?.content)
        assertEquals(95, payload.claims["score"]?.jsonPrimitive?.int)
        assertEquals(false, payload.claims["active"]?.jsonPrimitive?.boolean)

        // Verify SD metadata
        assertEquals(setOf("ssn", "score", "active"), payload.sdClaims)
    }

    @Test
    fun buildPayloadWithMinimumDigests() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            claimSd("email", "user@example.com")
            minimumDigests(5)
        }

        assertEquals(5, payload.minimumDigests)
    }

    @Test
    fun minimumDigestsMustBePositive() {
        assertFailsWith<IllegalArgumentException> {
            sdJwtPayload {
                iss("https://issuer.example.com")
                minimumDigests(0)
            }
        }
    }

    @Test
    fun buildPayloadWithNestedSdObject() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            objSd("address") {
                claim("street", "123 Main St")
                claim("city", "Springfield")
                claimSd("zip", "12345")
            }
        }

        // Verify nested object exists
        val address = payload.claims["address"]?.jsonObject
        assertNotNull(address)
        assertEquals("123 Main St", address["street"]?.jsonPrimitive?.content)
        assertEquals("Springfield", address["city"]?.jsonPrimitive?.content)
        assertEquals("12345", address["zip"]?.jsonPrimitive?.content)

        // Verify SD metadata - both the object and nested field should be tracked
        assertTrue("address" in payload.sdClaims)
        assertTrue("address.zip" in payload.sdClaims)
    }

    @Test
    fun checkIfClaimIsSelectivelyDisclosable() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            claim("public_field", "value")
            claimSd("private_field", "secret")
        }

        assertTrue(payload.isClaimSelectivelyDisclosable("private_field"))
        assertFalse(payload.isClaimSelectivelyDisclosable("public_field"))
        assertFalse(payload.isClaimSelectivelyDisclosable("iss"))
        assertFalse(payload.isClaimSelectivelyDisclosable("nonexistent"))
    }

    @Test
    fun getPlainClaims() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            sub("user-123")
            claimSd("email", "user@example.com")
            claimSd("phone", "+1234567890")
        }

        // Plain claims should include iss and sub, but not the SD claims
        val plainClaims = payload.plainClaims
        assertTrue("iss" in plainClaims)
        assertTrue("sub" in plainClaims)
        assertFalse("email" in plainClaims)
        assertFalse("phone" in plainClaims)
    }

    @Test
    fun buildPayloadWithJsonElementClaim() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            claim("metadata", JsonPrimitive("custom-value"))
            claimSd("sensitive_data", JsonPrimitive(12345))
        }

        assertEquals("custom-value", payload.claims["metadata"]?.jsonPrimitive?.content)
        assertEquals(12345, payload.claims["sensitive_data"]?.jsonPrimitive?.int)
        assertEquals(setOf("sensitive_data"), payload.sdClaims)
    }

    @Test
    fun buildPayloadWithMultipleAudiences() {
        val payload = sdJwtPayload {
            iss("https://issuer.example.com")
            aud("aud1", "aud2", "aud3")
        }

        // Multiple audiences should be an array
        assertNotNull(payload.claims["aud"])
    }

    @Test
    fun buildClaimsOnlyWithoutSdMetadata() {
        val builder = SdJwtPayloadBuilder()
        builder.iss("https://issuer.example.com")
        builder.claimSd("email", "user@example.com")

        val claimsOnly = builder.buildClaims()

        // Should contain the claims
        assertEquals("https://issuer.example.com", claimsOnly["iss"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", claimsOnly["email"]?.jsonPrimitive?.content)

        // But this is just the raw JsonObject, no SD metadata tracking
    }
}

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
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatedAccessTokenTest {
    private fun createTestToken(
        scopes: Set<String> = setOf("read", "write"),
        claims: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ): ValidatedAccessToken =
        ValidatedAccessToken(
            subject = "user-123",
            issuer = "https://auth.example.com",
            audiences = listOf("my-api"),
            expiresAt = 1735689600L,
            issuedAt = 1735686000L,
            notBefore = null,
            scopes = scopes,
            clientId = "client-123",
            jwtId = "jwt-id-456",
            rawToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
            claims = claims,
            idpId = "keycloak-prod",
        )

    @Test
    fun testHasScope() {
        val token = createTestToken(scopes = setOf("read", "write", "admin"))

        assertTrue(token.hasScope("read"))
        assertTrue(token.hasScope("write"))
        assertTrue(token.hasScope("admin"))
        assertFalse(token.hasScope("delete"))
        assertFalse(token.hasScope("superadmin"))
    }

    @Test
    fun testHasAllScopes() {
        val token = createTestToken(scopes = setOf("read", "write", "admin"))

        assertTrue(token.hasAllScopes(setOf("read")))
        assertTrue(token.hasAllScopes(setOf("read", "write")))
        assertTrue(token.hasAllScopes(setOf("read", "write", "admin")))
        assertFalse(token.hasAllScopes(setOf("read", "delete")))
        assertFalse(token.hasAllScopes(setOf("superadmin")))
    }

    @Test
    fun testHasAnyScope() {
        val token = createTestToken(scopes = setOf("read", "write"))

        assertTrue(token.hasAnyScope(setOf("read")))
        assertTrue(token.hasAnyScope(setOf("read", "delete")))
        assertTrue(token.hasAnyScope(setOf("admin", "write")))
        assertFalse(token.hasAnyScope(setOf("delete", "admin")))
        assertFalse(token.hasAnyScope(emptySet()))
    }

    @Test
    fun testEmptyScopes() {
        val token = createTestToken(scopes = emptySet())

        assertFalse(token.hasScope("read"))
        assertFalse(token.hasAllScopes(setOf("read")))
        assertTrue(token.hasAllScopes(emptySet()))
        assertFalse(token.hasAnyScope(setOf("read")))
    }

    @Test
    fun testTokenProperties() {
        val token = createTestToken()

        assertEquals("user-123", token.subject)
        assertEquals("https://auth.example.com", token.issuer)
        assertEquals(listOf("my-api"), token.audiences)
        assertEquals(1735689600L, token.expiresAt)
        assertEquals(1735686000L, token.issuedAt)
        assertEquals("client-123", token.clientId)
        assertEquals("jwt-id-456", token.jwtId)
        assertEquals("keycloak-prod", token.idpId)
    }

    @Test
    fun testSerialization() {
        val claims =
            mapOf(
                "custom_claim" to JsonPrimitive("custom_value"),
                "number_claim" to JsonPrimitive(42),
            )
        val token = createTestToken(claims = claims)

        val json = Json.encodeToString(token)
        val deserialized = Json.decodeFromString<ValidatedAccessToken>(json)

        assertEquals(token, deserialized)
        assertEquals("custom_value", (deserialized.claims["custom_claim"] as JsonPrimitive).content)
    }

    @Test
    fun testMultipleAudiences() {
        val token =
            ValidatedAccessToken(
                subject = "user-123",
                issuer = "https://auth.example.com",
                audiences = listOf("api-1", "api-2", "api-3"),
                expiresAt = 1735689600L,
                issuedAt = 1735686000L,
                notBefore = null,
                scopes = emptySet(),
                clientId = null,
                jwtId = null,
                rawToken = "token",
                claims = emptyMap(),
                idpId = "test",
            )

        assertEquals(3, token.audiences.size)
        assertTrue(token.audiences.contains("api-1"))
        assertTrue(token.audiences.contains("api-2"))
        assertTrue(token.audiences.contains("api-3"))
    }
}

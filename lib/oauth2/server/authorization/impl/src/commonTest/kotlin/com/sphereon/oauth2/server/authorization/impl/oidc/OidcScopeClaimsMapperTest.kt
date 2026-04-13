/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.oidc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for OidcScopeClaimsMapper — verifies OpenID Connect Core §5.4
 * scope-to-claims mapping.
 */
class OidcScopeClaimsMapperTest {
    private val mapper: OidcScopeClaimsMapper = OidcScopeClaimsMapperImpl()

    @Test
    fun openidScopeReturnsSub() {
        val claims = mapper.claimsForScopes(setOf("openid"))
        assertEquals(setOf("sub"), claims)
    }

    @Test
    fun profileScopeReturnsProfileClaims() {
        val claims = mapper.claimsForScopes(setOf("profile"))
        assertTrue("name" in claims)
        assertTrue("family_name" in claims)
        assertTrue("given_name" in claims)
        assertTrue("middle_name" in claims)
        assertTrue("nickname" in claims)
        assertTrue("preferred_username" in claims)
        assertTrue("profile" in claims)
        assertTrue("picture" in claims)
        assertTrue("website" in claims)
        assertTrue("gender" in claims)
        assertTrue("birthdate" in claims)
        assertTrue("zoneinfo" in claims)
        assertTrue("locale" in claims)
        assertTrue("updated_at" in claims)
    }

    @Test
    fun emailScopeReturnsEmailClaims() {
        val claims = mapper.claimsForScopes(setOf("email"))
        assertEquals(setOf("email", "email_verified"), claims)
    }

    @Test
    fun phoneScopeReturnsPhoneClaims() {
        val claims = mapper.claimsForScopes(setOf("phone"))
        assertEquals(setOf("phone_number", "phone_number_verified"), claims)
    }

    @Test
    fun addressScopeReturnsAddressClaim() {
        val claims = mapper.claimsForScopes(setOf("address"))
        assertEquals(setOf("address"), claims)
    }

    @Test
    fun combinedScopesMerge() {
        val claims = mapper.claimsForScopes(setOf("openid", "email", "phone"))
        assertTrue("sub" in claims)
        assertTrue("email" in claims)
        assertTrue("email_verified" in claims)
        assertTrue("phone_number" in claims)
        assertTrue("phone_number_verified" in claims)
        assertFalse("name" in claims, "profile claims should not be present without profile scope")
    }

    @Test
    fun unknownScopesReturnEmpty() {
        val claims = mapper.claimsForScopes(setOf("custom_scope", "another"))
        assertTrue(claims.isEmpty())
    }

    @Test
    fun emptyScopesReturnEmpty() {
        val claims = mapper.claimsForScopes(emptySet())
        assertTrue(claims.isEmpty())
    }

    @Test
    fun filterClaimsKeepsOnlyAllowedClaims() {
        val allClaims: Map<String, Any> =
            mapOf(
                "sub" to "user123",
                "name" to "John Doe",
                "email" to "john@example.com",
                "email_verified" to "true",
                "phone_number" to "+1234567890",
                "custom" to "value",
            )

        val filtered = mapper.filterClaims(allClaims, setOf("openid", "email"))
        assertEquals(3, filtered.size)
        assertEquals("user123", filtered["sub"])
        assertEquals("john@example.com", filtered["email"])
        assertEquals("true", filtered["email_verified"])
        assertFalse("name" in filtered, "name not in openid+email scopes")
        assertFalse("phone_number" in filtered)
        assertFalse("custom" in filtered)
    }

    @Test
    fun filterClaimsWithAllScopes() {
        val allClaims: Map<String, Any> =
            mapOf(
                "sub" to "user123",
                "name" to "John Doe",
                "email" to "john@example.com",
                "phone_number" to "+1234567890",
            )

        val filtered = mapper.filterClaims(allClaims, setOf("openid", "profile", "email", "phone"))
        assertEquals(4, filtered.size)
    }
}

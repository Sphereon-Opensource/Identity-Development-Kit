/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.http

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSameOriginCallbackTest {
    private val base = "https://as.example.org"

    @Test
    fun acceptsSameOriginCallback() {
        assertTrue(isSameOriginCallback("https://as.example.org/authorize/callback?session_id=s", base))
    }

    @Test
    fun acceptsSameOriginWithIssuerPath() {
        assertTrue(
            isSameOriginCallback("https://as.example.org/auth/authorize/callback?x=1", "https://as.example.org/auth"),
        )
    }

    @Test
    fun rejectsDifferentHost() {
        assertFalse(isSameOriginCallback("https://evil.example/authorize/callback?session_id=s", base))
    }

    @Test
    fun rejectsHostSuffixAttack() {
        assertFalse(isSameOriginCallback("https://as.example.org.evil.com/authorize/callback", base))
    }

    @Test
    fun rejectsUserinfoAtAttack() {
        assertFalse(isSameOriginCallback("https://as.example.org@evil.com/authorize/callback", base))
    }

    @Test
    fun rejectsDifferentScheme() {
        assertFalse(isSameOriginCallback("http://as.example.org/authorize/callback", base))
    }

    @Test
    fun rejectsQueryParamPrefixSpoof() {
        assertFalse(isSameOriginCallback("https://evil.example/?x=https://as.example.org/authorize/callback", base))
    }

    @Test
    fun acceptsMixedCaseSchemeAndHost() {
        // Scheme + host are case-insensitive; a same-origin callback differing only in case is honored.
        assertTrue(isSameOriginCallback("HTTPS://AS.EXAMPLE.ORG/authorize/callback?session_id=s", base))
    }

    @Test
    fun rejectsSchemelessUrl() {
        // Protocol-relative URLs must not be treated as same-origin.
        assertFalse(isSameOriginCallback("//as.example.org/authorize/callback", base))
    }

    @Test
    fun rejectsSameOriginWithoutCallbackPath() {
        assertFalse(isSameOriginCallback("https://as.example.org/somewhere-else", base))
    }
}

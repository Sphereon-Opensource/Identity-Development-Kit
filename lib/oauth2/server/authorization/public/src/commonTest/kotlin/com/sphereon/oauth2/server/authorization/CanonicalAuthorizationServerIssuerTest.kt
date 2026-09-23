/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.oauth2.server.authorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalAuthorizationServerIssuerTest {
    @Test
    fun canonicalizesSchemeHostDefaultPortAndSingleTrailingSlash() {
        assertEquals(
            "https://xn--bcher-kva.example/auth",
            CanonicalAuthorizationServerIssuer.canonicalize("HTTPS://BÜCHER.Example:443/auth/"),
        )
        CanonicalAuthorizationServerIssuer.validate("https://example.test/auth")
    }

    @Test
    fun preservesNonDefaultPortAndPath() {
        assertEquals(
            "https://as.example:8443/tenant/as",
            CanonicalAuthorizationServerIssuer.parse("https://AS.Example:8443/tenant/as/").value,
        )
    }

    @Test
    fun canonicalizesBracketedIpv6Loopback() {
        assertEquals(
            "https://[::1]/auth",
            CanonicalAuthorizationServerIssuer.parse("HTTPS://[::1]/auth/").value,
        )
    }

    @Test
    fun rejectsAmbiguousIssuerForms() {
        listOf(
            "https://user@example.test",
            "https://example.test?x=1",
            "https://example.test#fragment",
            "https://example.test/%2e%2e/as",
            "https://example.test/a//b",
            "https://example.test/a/../b",
            "https://example.test:0",
            "https://example.test:65536",
        ).forEach { value -> assertFailsWith<IllegalArgumentException> { CanonicalAuthorizationServerIssuer.parse(value) } }
    }

    @Test
    fun httpIsOnlyAllowedForExplicitHostedLoopbackDevelopment() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalAuthorizationServerIssuer.parse("http://localhost:8080", external = true)
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalAuthorizationServerIssuer.parse("http://localhost:8080")
        }
        assertEquals(
            "http://localhost:8080",
            CanonicalAuthorizationServerIssuer.parse("http://localhost:8080/", allowHostedLocalDevelopmentHttp = true).value,
        )
        assertFailsWith<IllegalArgumentException> {
            CanonicalAuthorizationServerIssuer.parse("http://example.test", allowHostedLocalDevelopmentHttp = true)
        }
    }

    @Test
    fun validateRejectsNonCanonicalSpellings() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalAuthorizationServerIssuer.validate("HTTPS://example.test:443/")
        }
    }
}

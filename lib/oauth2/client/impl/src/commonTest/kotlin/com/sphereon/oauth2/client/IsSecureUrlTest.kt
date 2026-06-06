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

package com.sphereon.oauth2.client

import com.sphereon.oauth2.client.util.isSecureUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that isSecureUrl accepts HTTPS everywhere, HTTP on loopback hosts (RFC 6761),
 * and rejects HTTP on non-loopback hosts.
 */
class IsSecureUrlTest {
    // --- HTTPS: always allowed ---

    @Test
    fun httpsExternalHostIsAllowed() {
        assertTrue(isSecureUrl("https://example.com/token"))
    }

    @Test
    fun httpsWithPortIsAllowed() {
        assertTrue(isSecureUrl("https://as.example.com:8443/token"))
    }

    // --- plain localhost: always allowed ---

    @Test
    fun httpLocalhostWithoutPortIsAllowed() {
        assertTrue(isSecureUrl("http://localhost"))
    }

    @Test
    fun httpLocalhostWithPortIsAllowed() {
        assertTrue(isSecureUrl("http://localhost:8080/token"))
    }

    @Test
    fun httpLocalhostWithPathIsAllowed() {
        assertTrue(isSecureUrl("http://localhost/oauth/token"))
    }

    // --- 127.0.0.1 and ::1: always allowed ---

    @Test
    fun http127IsAllowed() {
        assertTrue(isSecureUrl("http://127.0.0.1:9000/token"))
    }

    @Test
    fun httpIpv6LoopbackIsAllowed() {
        assertTrue(isSecureUrl("http://[::1]:8080/token"))
    }

    // --- *.localhost subdomains (RFC 6761): must now be allowed ---

    @Test
    fun httpSubdomainLocalhostWithPortIsAllowed() {
        assertTrue(isSecureUrl("http://acme.localhost:8088/realms/master"))
    }

    @Test
    fun httpSubdomainLocalhostWithoutPortIsAllowed() {
        assertTrue(isSecureUrl("http://acme.localhost/token"))
    }

    @Test
    fun httpDeeplyNestedSubdomainLocalhostIsAllowed() {
        assertTrue(isSecureUrl("http://a.b.localhost:1234/.well-known/oauth-authorization-server"))
    }

    @Test
    fun httpSubdomainLocalhostWithQueryIsAllowed() {
        assertTrue(isSecureUrl("http://keycloak.localhost:8080/token?foo=bar"))
    }

    // --- rejections: HTTP on non-loopback hosts must stay rejected ---

    @Test
    fun httpExternalHostIsRejected() {
        assertFalse(isSecureUrl("http://evil.example.com/token"))
    }

    @Test
    fun httpExternalHostIsRejectedNoPath() {
        assertFalse(isSecureUrl("http://example.com"))
    }

    @Test
    fun httpHostThatEndsWithLocalhostButIsNotSubdomainIsRejected() {
        // "notlocalhost" is NOT a *.localhost subdomain and NOT localhost itself
        assertFalse(isSecureUrl("http://notlocalhost/token"))
    }

    @Test
    fun httpLocalhostLookAlikeInDomainIsRejected() {
        // The host "localhostnotreally.com" must not slip through
        assertFalse(isSecureUrl("http://localhostnotreally.com/token"))
    }
}

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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.http.GenericHttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates challenge handling wired into `/token`, `/introspect`, and `/revoke`. Token endpoint
 * non-HTTP token endpoint client-auth failures remain 400; a Basic-auth 401 carries a challenge.
 * Introspection and revocation challenge every 401, including requests with no credentials.
 * Existing scheme-specific challenges win.
 */
class TokenEndpointAuthFailureTest {
    @Test
    fun tokenEndpoint_basicAuthFails_401WithWwwAuthenticate() {
        val headers = mapOf("Authorization" to "Basic Y2xpZW50MTpzZWNyZXQ=")
        assertTrue(isBasicAuthorizationHeaderInternal(headers))

        val failure = GenericHttpResponse(statusCode = 401, headers = emptyMap(), body = "")
        val withHeader = failure.withWwwAuthenticateIfBasicInternal(basicWasAttempted = true)
        assertEquals("Basic realm=\"oauth2\"", withHeader.headers["WWW-Authenticate"])
    }

    @Test
    fun tokenEndpoint_nonHttpClientAuthFailureRemains400WithoutChallenge() {
        val headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        assertFalse(isBasicAuthorizationHeaderInternal(headers))

        val failure = GenericHttpResponse(statusCode = 400, headers = emptyMap(), body = "")
        val withoutHeader = failure.withWwwAuthenticateIfBasicInternal(basicWasAttempted = false)
        assertEquals(400, withoutHeader.statusCode)
        assertNull(withoutHeader.headers["WWW-Authenticate"])
    }

    @Test
    fun isBasicAuthorizationHeader_caseInsensitive() {
        assertTrue(isBasicAuthorizationHeaderInternal(mapOf("authorization" to "basic Zm9v")))
        assertTrue(isBasicAuthorizationHeaderInternal(mapOf("Authorization" to "BASIC Zm9v")))
    }

    @Test
    fun isBasicAuthorizationHeader_rejectsBearer() {
        assertFalse(isBasicAuthorizationHeaderInternal(mapOf("Authorization" to "Bearer abc.def")))
    }

    @Test
    fun withWwwAuthenticateIfBasic_noopOnNon401() {
        val ok = GenericHttpResponse(statusCode = 200, headers = mapOf("X" to "Y"), body = "{}")
        val result = ok.withWwwAuthenticateIfBasicInternal(basicWasAttempted = true)
        assertNull(result.headers["WWW-Authenticate"], "200 responses must not carry WWW-Authenticate")
    }

    @Test
    fun unauthenticatedProtectedEndpoint401_addsBasicChallengeAndPreservesExistingChallenge() {
        val unauthenticated = GenericHttpResponse(statusCode = 401, headers = emptyMap(), body = "{}")
        assertEquals(
            "Basic realm=\"oauth2\"",
            unauthenticated.withWwwAuthenticateIfMissingInternal().headers["WWW-Authenticate"],
        )

        val dpop =
            GenericHttpResponse(
                statusCode = 401,
                headers = mapOf("WWW-Authenticate" to "DPoP error=\"invalid_token\""),
                body = "{}",
            )
        assertEquals(
            "DPoP error=\"invalid_token\"",
            dpop.withWwwAuthenticateIfMissingInternal().headers["WWW-Authenticate"],
        )
    }
}

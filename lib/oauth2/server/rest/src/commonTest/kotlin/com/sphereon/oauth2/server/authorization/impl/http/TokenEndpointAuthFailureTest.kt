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
 * WP2 Task 2.6 — validates the RFC 6749 §5.2 behavior wired into `/token`, `/introspect`,
 * `/revoke`: 401 responses gain `WWW-Authenticate: Basic realm="oauth2"` iff the caller attempted
 * Basic auth. The helpers under test are invoked verbatim from the adapter's error paths
 * (see `TokenHttpEndpointCommandImpl` and peers).
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
    fun tokenEndpoint_postAuthFails_401NoWwwAuthenticate() {
        val headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        assertFalse(isBasicAuthorizationHeaderInternal(headers))

        val failure = GenericHttpResponse(statusCode = 401, headers = emptyMap(), body = "")
        val withoutHeader = failure.withWwwAuthenticateIfBasicInternal(basicWasAttempted = false)
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
}

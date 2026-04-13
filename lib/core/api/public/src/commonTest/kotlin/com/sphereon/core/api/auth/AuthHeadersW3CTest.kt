/*
 * (c) 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.api.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract Tests: AuthHeaders W3C migration.
 *
 * Validates:
 * - TRACEPARENT and TRACESTATE constants exist
 * - X_TRACE_ID and X_SPAN_ID are removed
 * - AuthContext.toHeaders() emits traceparent
 * - AuthContext.fromHeaders() parses traceparent
 */
class AuthHeadersConstantsTest {
    @Test
    fun traceparentConstantExists() {
        // Given the AuthHeaders object
        // Then TRACEPARENT constant exists with correct value
        assertEquals("traceparent", AuthHeaders.TRACEPARENT)
    }

    @Test
    fun tracestateConstantExists() {
        // Given the AuthHeaders object
        // Then TRACESTATE constant exists with correct value
        assertEquals("tracestate", AuthHeaders.TRACESTATE)
    }

    @Test
    fun authorizationConstantUnchanged() {
        // Given the AuthHeaders object
        // Then AUTHORIZATION is unchanged
        assertEquals("Authorization", AuthHeaders.AUTHORIZATION)
    }

    @Test
    fun apiKeyConstantUnchanged() {
        // Given the AuthHeaders object
        // Then X_API_KEY is unchanged
        assertEquals("X-API-Key", AuthHeaders.X_API_KEY)
    }

    @Test
    fun tenantIdConstantUnchanged() {
        // Given the AuthHeaders object
        // Then X_TENANT_ID is unchanged
        assertEquals("X-Tenant-Id", AuthHeaders.X_TENANT_ID)
    }

    @Test
    fun requestIdConstantUnchanged() {
        // Given the AuthHeaders object
        // Then X_REQUEST_ID is unchanged
        assertEquals("X-Request-Id", AuthHeaders.X_REQUEST_ID)
    }

    @Test
    fun correlationIdConstantUnchanged() {
        // Given the AuthHeaders object
        // Then X_CORRELATION_ID is unchanged
        assertEquals("X-Correlation-Id", AuthHeaders.X_CORRELATION_ID)
    }
}

/**
 * Contract Tests: AuthContext with W3C traceparent.
 */
class AuthContextTraceparentTest {
    @Test
    fun authContextHasTraceparentField() {
        // Given an AuthContext with a traceparent value
        val ctx =
            AuthContext(
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
            )

        // Then the traceparent is accessible
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01", ctx.traceparent)
    }

    @Test
    fun authContextTraceparentDefaultsToNull() {
        // Given an AuthContext without traceparent
        val ctx = AuthContext()

        // Then traceparent is null
        assertNull(ctx.traceparent)
    }

    @Test
    fun authContextHasTracestateField() {
        // Given an AuthContext with a tracestate value
        val ctx =
            AuthContext(
                tracestate = "congo=t61rcWkgMzE",
            )

        // Then the tracestate is accessible
        assertEquals("congo=t61rcWkgMzE", ctx.tracestate)
    }

    @Test
    fun authContextTracestateDefaultsToNull() {
        // Given an AuthContext without tracestate
        val ctx = AuthContext()

        // Then tracestate is null
        assertNull(ctx.tracestate)
    }
}

class AuthContextToHeadersTest {
    @Test
    fun toHeadersEmitsTraceparent() {
        // Given an AuthContext with traceparent
        val ctx =
            AuthContext(
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
            )

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then the traceparent header is present
        assertEquals(
            "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
            headers["traceparent"],
        )
    }

    @Test
    fun toHeadersEmitsTracestate() {
        // Given an AuthContext with tracestate
        val ctx =
            AuthContext(
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
                tracestate = "congo=t61rcWkgMzE",
            )

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then the tracestate header is present
        assertEquals("congo=t61rcWkgMzE", headers["tracestate"])
    }

    @Test
    fun toHeadersOmitsTraceparentWhenNull() {
        // Given an AuthContext without traceparent
        val ctx = AuthContext(token = "my-token")

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then no traceparent header exists
        assertFalse(headers.containsKey("traceparent"))
    }

    @Test
    fun toHeadersOmitsTracestateWhenNull() {
        // Given an AuthContext without tracestate
        val ctx = AuthContext(token = "my-token")

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then no tracestate header exists
        assertFalse(headers.containsKey("tracestate"))
    }

    @Test
    fun toHeadersDoesNotEmitLegacyTraceHeaders() {
        // Given an AuthContext with traceparent (W3C)
        val ctx =
            AuthContext(
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
            )

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then legacy trace headers are NOT present
        assertFalse(headers.containsKey("X-Trace-Id"), "Legacy X-Trace-Id should not be emitted")
        assertFalse(headers.containsKey("X-Span-Id"), "Legacy X-Span-Id should not be emitted")
    }

    @Test
    fun toHeadersStillEmitsAuthorizationBearer() {
        // Given an AuthContext with a token
        val ctx = AuthContext(token = "my-jwt-token")

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then Authorization header is still emitted
        assertEquals("Bearer my-jwt-token", headers["Authorization"])
    }

    @Test
    fun toHeadersStillEmitsTenantId() {
        // Given an AuthContext with a tenant ID
        val ctx = AuthContext(tenantId = "tenant-123")

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then X-Tenant-Id is still emitted
        assertEquals("tenant-123", headers["X-Tenant-Id"])
    }

    @Test
    fun toHeadersStillEmitsCorrelationId() {
        // Given an AuthContext with a correlation ID
        val ctx = AuthContext(correlationId = "corr-abc")

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then X-Correlation-Id is still emitted
        assertEquals("corr-abc", headers["X-Correlation-Id"])
    }

    @Test
    fun toHeadersStillEmitsRequestId() {
        // Given an AuthContext with a request ID
        val ctx = AuthContext(requestId = "req-123")

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then X-Request-Id is still emitted
        assertEquals("req-123", headers["X-Request-Id"])
    }
}

class AuthContextFromHeadersTest {
    @Test
    fun fromHeadersParsesTraceparent() {
        // Given headers with a traceparent
        val headers =
            mapOf(
                "traceparent" to "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then traceparent is extracted
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01", ctx.traceparent)
    }

    @Test
    fun fromHeadersParsesTracestate() {
        // Given headers with tracestate
        val headers =
            mapOf(
                "traceparent" to "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
                "tracestate" to "congo=t61rcWkgMzE",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then tracestate is extracted
        assertEquals("congo=t61rcWkgMzE", ctx.tracestate)
    }

    @Test
    fun fromHeadersTraceparentIsNullWhenMissing() {
        // Given headers without traceparent
        val headers =
            mapOf(
                "Authorization" to "Bearer token123",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then traceparent is null
        assertNull(ctx.traceparent)
    }

    @Test
    fun fromHeadersStillParsesAuthorization() {
        // Given headers with Authorization
        val headers =
            mapOf(
                "Authorization" to "Bearer my-token",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then token is extracted
        assertEquals("my-token", ctx.token)
    }

    @Test
    fun fromHeadersStillParsesTenantId() {
        // Given headers with X-Tenant-Id
        val headers =
            mapOf(
                "X-Tenant-Id" to "tenant-123",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then tenantId is extracted
        assertEquals("tenant-123", ctx.tenantId)
    }

    @Test
    fun fromHeadersStillParsesCorrelationId() {
        // Given headers with X-Correlation-Id
        val headers =
            mapOf(
                "X-Correlation-Id" to "corr-abc",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then correlationId is extracted
        assertEquals("corr-abc", ctx.correlationId)
    }

    @Test
    fun fromHeadersRoundTripWithTraceparent() {
        // Given an AuthContext with traceparent
        val original =
            AuthContext(
                token = "jwt-token",
                tenantId = "tenant-1",
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
                tracestate = "congo=t61rcWkgMzE",
                correlationId = "corr-1",
            )

        // When round-tripping through headers
        val headers = original.toHeaders()
        val restored = AuthContext.fromHeaders(headers)

        // Then all fields survive the round-trip
        assertEquals(original.token, restored.token)
        assertEquals(original.tenantId, restored.tenantId)
        assertEquals(original.traceparent, restored.traceparent)
        assertEquals(original.tracestate, restored.tracestate)
        assertEquals(original.correlationId, restored.correlationId)
    }
}

/**
 * Security-focused tests: Legacy header removal and malformed traceparent handling.
 * Per security review items #10 and related.
 */
class AuthContextSecurityTest {
    @Test
    fun fromHeadersIgnoresLegacyXTraceIdHeader() {
        // Given headers with only the legacy X-Trace-Id (no traceparent)
        val headers =
            mapOf(
                "X-Trace-Id" to "some-legacy-trace-id",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then traceparent is null (legacy header is ignored)
        assertNull(ctx.traceparent)
    }

    @Test
    fun fromHeadersIgnoresLegacyXSpanIdHeader() {
        // Given headers with only the legacy X-Span-Id (no traceparent)
        val headers =
            mapOf(
                "X-Span-Id" to "some-legacy-span-id",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then tracestate is null (legacy header ignored, no traceparent/tracestate set)
        assertNull(ctx.traceparent)
    }

    @Test
    fun toHeadersNeverEmitsLegacyTraceIdEvenWithCorrelation() {
        // Given an AuthContext with all tracing fields populated
        val ctx =
            AuthContext(
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
                tracestate = "congo=t61rcWkgMzE",
                correlationId = "corr-1",
                requestId = "req-1",
            )

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then no legacy trace headers appear anywhere
        val headerKeys = headers.keys.map { it.lowercase() }
        assertFalse("x-trace-id" in headerKeys, "Legacy X-Trace-Id must not be emitted")
        assertFalse("x-span-id" in headerKeys, "Legacy X-Span-Id must not be emitted")
    }

    @Test
    fun fromHeadersMalformedTraceparentIsStoredAsIs() {
        // Given headers with a malformed traceparent value
        // (AuthContext stores the raw string; validation is done by TraceContext.fromW3CTraceParent)
        val headers =
            mapOf(
                "traceparent" to "not-a-valid-traceparent",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then the raw value is stored (caller uses TraceContext.fromW3CTraceParent to validate)
        assertEquals("not-a-valid-traceparent", ctx.traceparent)
    }

    @Test
    fun fromHeadersDoesNotLeakTokenIntoTraceparent() {
        // Given headers with Authorization but no traceparent
        val headers =
            mapOf(
                "Authorization" to "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.sig",
            )

        // When parsing
        val ctx = AuthContext.fromHeaders(headers)

        // Then token is in the token field, not in traceparent
        assertNotNull(ctx.token)
        assertNull(ctx.traceparent)
    }

    @Test
    fun toHeadersDoesNotLeakTokenIntoTraceparent() {
        // Given an AuthContext with token but no traceparent
        val ctx =
            AuthContext(
                token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.sig",
            )

        // When converting to headers
        val headers = ctx.toHeaders()

        // Then token is only in Authorization header, not in traceparent
        assertFalse(headers.containsKey("traceparent"))
        assertTrue(headers.containsKey("Authorization"))
    }
}

class AuthContextExistingBehaviorTest {
    @Test
    fun anonymousContextHasNoAuth() {
        // Given the anonymous context
        val ctx = AuthContext.ANONYMOUS

        // Then it has no authentication
        assertFalse(ctx.isAuthenticated)
        assertNull(ctx.token)
        assertNull(ctx.apiKey)
    }

    @Test
    fun isAuthenticatedWithToken() {
        // Given an AuthContext with a token
        val ctx = AuthContext(token = "bearer-token")

        // Then it is authenticated
        assertTrue(ctx.isAuthenticated)
    }

    @Test
    fun isAuthenticatedWithApiKey() {
        // Given an AuthContext with an API key
        val ctx = AuthContext(apiKey = "my-api-key")

        // Then it is authenticated
        assertTrue(ctx.isAuthenticated)
    }

    @Test
    fun isServiceCallWithServiceId() {
        // Given an AuthContext with a service ID
        val ctx = AuthContext(serviceId = "svc-123")

        // Then it is a service call
        assertTrue(ctx.isServiceCall)
    }

    @Test
    fun effectivePrincipalPriority() {
        // Given an AuthContext with principalId, userId, and serviceId
        val ctx =
            AuthContext(
                principalId = "principal-1",
                userId = "user-1",
                serviceId = "svc-1",
            )

        // Then effectivePrincipal returns principalId first
        assertEquals("principal-1", ctx.effectivePrincipal)
    }

    @Test
    fun effectivePrincipalFallsBackToUserId() {
        // Given an AuthContext with only userId
        val ctx = AuthContext(userId = "user-1")

        // Then effectivePrincipal returns userId
        assertEquals("user-1", ctx.effectivePrincipal)
    }

    @Test
    fun withScopeAddsScope() {
        // Given an AuthContext
        val ctx = AuthContext()

        // When adding a scope
        val updated = ctx.withScope("read")

        // Then the scope is added
        assertTrue("read" in updated.scopes)
    }

    @Test
    fun withPolicyContextAddsEntry() {
        // Given an AuthContext
        val ctx = AuthContext()

        // When adding policy context
        val updated = ctx.withPolicyContext("key1", "value1")

        // Then the entry is added
        assertEquals("value1", updated.policyContext["key1"])
    }
}

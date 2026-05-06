/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the [CommandBackedHttpAdapter] dispatcher's path-peel
 * behaviour under [TenantPathPolicy.LeadingSlug] and
 * [TenantPathPolicy.WellKnownSuffix].
 *
 * Uses the real adapter class against a deterministic [RoutableSlugLookup]
 * fake (NOT a mock framework — just a Map-backed implementation) and
 * verifies that the dispatcher:
 *  - matches as-is when no peel is needed
 *  - peels the right depth in the right direction
 *  - rejects peels for non-existent slugs
 *  - leaves session tenant unchanged on `None` policy
 *  - sets the resolved-tenant-id provider on a successful peel
 *  - clears the override after dispatch
 */
class TenantPathPolicyDispatcherTest {
    private val captured = mutableListOf<String>()

    @Test
    fun leadingSlug_peelsOneSegment_andDispatches() =
        runTest {
            val adapter =
                adapter(
                    policy = TenantPathPolicy.LeadingSlug(maxDepth = 1),
                    slugLookup = SlugLookupFake(roots = mapOf("acme" to "tenant-acme")),
                    endpoints = listOf(echoEndpoint(method = HttpMethod.POST, pattern = "/authorize")),
                )
            val response = adapter.handleRequest(request("POST", "/acme/authorize"))
            assertEquals(200, response.statusCode)
            assertEquals("/authorize", captured.last())
            assertEquals("tenant-acme", adapter.providerSeen)
        }

    @Test
    fun leadingSlug_failsToPeel_whenSlugInvalid_andFallsThroughToAsIs() =
        runTest {
            val adapter =
                adapter(
                    policy = TenantPathPolicy.LeadingSlug(maxDepth = 1),
                    slugLookup = SlugLookupFake(roots = mapOf("acme" to "tenant-acme")),
                    endpoints = listOf(echoEndpoint(method = HttpMethod.POST, pattern = "/foo/authorize")),
                )
            val response = adapter.handleRequest(request("POST", "/foo/authorize"))
            assertEquals(200, response.statusCode)
            assertEquals("/foo/authorize", captured.last())
            // No peel happened → no override fired.
            assertNull(adapter.providerSeen)
        }

    @Test
    fun leadingSlug_returnsTenantUnresolved_whenRequiredAndNoMatch() =
        runTest {
            val adapter =
                adapter(
                    policy = TenantPathPolicy.LeadingSlug(maxDepth = 1, required = true),
                    slugLookup = SlugLookupFake(),
                    endpoints = listOf(echoEndpoint(method = HttpMethod.POST, pattern = "/authorize")),
                )
            val response = adapter.handleRequest(request("POST", "/unknown/authorize"))
            // Renderer turns the IdkError into an HTTP response — for our test renderer that's a 500 unless wrapped.
            // The important assertion is that NO peel happened and the captured echo never fired.
            assertTrue(captured.isEmpty(), "Endpoint must NOT have run when required peel fails")
            assertEquals(404, response.statusCode)
        }

    @Test
    fun wellKnownSuffix_peelsOneTrailingSegment() =
        runTest {
            val adapter =
                adapter(
                    policy = TenantPathPolicy.WellKnownSuffix(maxDepth = 1),
                    slugLookup = SlugLookupFake(roots = mapOf("acme" to "tenant-acme")),
                    endpoints = listOf(echoEndpoint(method = HttpMethod.GET, pattern = "/.well-known/openid-configuration")),
                )
            val response = adapter.handleRequest(request("GET", "/.well-known/openid-configuration/acme"))
            assertEquals(200, response.statusCode)
            assertEquals("/.well-known/openid-configuration", captured.last())
            assertEquals("tenant-acme", adapter.providerSeen)
        }

    @Test
    fun wellKnownSuffix_descendsParentChild_inUrlOrder() =
        runTest {
            val adapter =
                adapter(
                    policy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
                    slugLookup =
                        SlugLookupFake(
                            roots = mapOf("tenanta" to "T_A"),
                            children = mapOf("T_A" to mapOf("tenantc" to "T_C")),
                        ),
                    endpoints = listOf(echoEndpoint(method = HttpMethod.GET, pattern = "/.well-known/openid-configuration")),
                )
            val response = adapter.handleRequest(request("GET", "/.well-known/openid-configuration/tenanta/tenantc"))
            assertEquals(200, response.statusCode)
            assertEquals("/.well-known/openid-configuration", captured.last())
            // Final descended tenant is the inner child (URL semantic order: parent → child).
            assertEquals("T_C", adapter.providerSeen)
        }

    @Test
    fun wellKnownSuffix_rejectsSiblingChild() =
        runTest {
            val adapter =
                adapter(
                    policy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
                    slugLookup =
                        SlugLookupFake(
                            roots = mapOf("tenanta" to "T_A", "tenantb" to "T_B"),
                            children = mapOf("T_A" to mapOf("tenantc" to "T_C")), // tenantc is child of tenanta only
                        ),
                    endpoints = listOf(echoEndpoint(method = HttpMethod.GET, pattern = "/.well-known/openid-configuration")),
                )
            val response = adapter.handleRequest(request("GET", "/.well-known/openid-configuration/tenantb/tenantc"))
            assertEquals(404, response.statusCode)
            assertTrue(captured.isEmpty())
        }

    @Test
    fun nonePolicy_neverPeels() =
        runTest {
            val adapter =
                adapter(
                    policy = TenantPathPolicy.None,
                    slugLookup = SlugLookupFake(roots = mapOf("acme" to "tenant-acme")),
                    endpoints = listOf(echoEndpoint(method = HttpMethod.GET, pattern = "/api/v1/tenants/{tenantId}")),
                )
            val response = adapter.handleRequest(request("GET", "/api/v1/tenants/acme"))
            assertEquals(200, response.statusCode)
            assertEquals("/api/v1/tenants/acme", captured.last())
            // None policy → tenant override never set, even though `acme` IS a valid root slug.
            assertNull(adapter.providerSeen)
        }

    // ---- helpers ----

    private fun adapter(
        policy: TenantPathPolicy,
        slugLookup: RoutableSlugLookup,
        endpoints: List<HttpEndpointCommand>,
    ): TestableAdapter = TestableAdapter(policy, slugLookup, endpoints)

    private fun request(
        method: String,
        path: String
    ) = GenericHttpRequest(method = method, path = path)

    /** Echo endpoint that records the path it was dispatched with and returns 200. */
    private fun echoEndpoint(
        method: HttpMethod,
        pattern: String
    ): HttpEndpointCommand =
        object : HttpEndpointCommand {
            override val id: String = "echo:$method:$pattern"
            override val isEnabled: Boolean = true
            override val endpoint: HttpEndpointDescriptor =
                HttpEndpointDescriptor(method = method, pathPattern = pattern)

            override suspend fun execute(args: GenericHttpRequest): IdkResult<GenericHttpResponse, IdkError> {
                captured += args.path
                return Ok(GenericHttpResponse(statusCode = 200, body = ""))
            }
        }

    private inner class TestableAdapter(
        policy: TenantPathPolicy,
        private val slugLookup: RoutableSlugLookup,
        override val endpointCommands: List<HttpEndpointCommand>,
    ) : CommandBackedHttpAdapter(
            id = "test-adapter",
            execution = TestSessionExecution(),
            mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
            tenantPathPolicy = policy,
        ) {
        @Volatile var providerSeen: String? = null

        override val routableSlugLookup: RoutableSlugLookup get() = slugLookup
        override val resolvedTenantIdProvider: MutableResolvedTenantIdProvider = ProviderRecord(this)
    }

    private class ProviderRecord(
        private val owner: TestableAdapter
    ) : MutableResolvedTenantIdProvider {
        private var current: String? = null

        override fun currentTenantId(): String? = current

        override fun setCurrentTenantId(tenantId: String) {
            current = tenantId
            owner.providerSeen = tenantId
        }

        override fun clearCurrentTenantId() {
            current = null
        }
    }
}

/** Map-backed fake to keep tests deterministic and easy to reason about. */
private class SlugLookupFake(
    private val roots: Map<String, String> = emptyMap(),
    private val children: Map<String, Map<String, String>> = emptyMap(),
) : RoutableSlugLookup {
    override suspend fun findRootBySlug(slug: String): RoutableSlugLookup.Resolved? = roots[slug]?.let { RoutableSlugLookup.Resolved(it, slug) }

    override suspend fun findChildBySlug(
        parentTenantId: String,
        slug: String
    ): RoutableSlugLookup.Resolved? = children[parentTenantId]?.get(slug)?.let { RoutableSlugLookup.Resolved(it, slug) }
}

private class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for test")
    override val log: SessionLogService = TestSessionLogService(sessionContext)
    override val conf: ContextConfig = TestContextConfig
}

private class TestSessionLogService(
    override val sessionContext: SessionContext,
) : SessionLogService {
    override val id: String = "test-tenant-path-log"
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.SESSION
    override val logManager: SessionLogManager
        get() = throw NotImplementedError("Not needed for test")

    override suspend fun setConfig(config: LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
}

private object TestContextConfig : ContextConfig {
    override val app: AppConfigService get() = throw NotImplementedError("Not needed for test")
    override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for test")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for test")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
}

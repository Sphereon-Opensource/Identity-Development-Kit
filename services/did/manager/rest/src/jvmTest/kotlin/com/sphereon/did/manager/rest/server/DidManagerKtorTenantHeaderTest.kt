/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.context.TenantInput
import com.sphereon.di.session.SessionInstance
import com.sphereon.did.manager.rest.server.adapter.DidManagerHttpAdapter
import com.sphereon.did.manager.rest.server.ktor.configureDidManager
import com.sphereon.did.manager.rest.server.ktor.createDidManagerAppGraph
import com.sphereon.ktor.server.inject.resolver.TenantResolver
import com.sphereon.ktor.server.inject.sessionInstance
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * VDX-infra-ubb: Ktor-level integration test for X-Tenant-ID / X-User-ID forwarding through
 * `KotlinInjectPlugin` into the DID manager endpoint commands. The adapter contract on
 * [DidManagerHttpAdapter] documents that header extraction is the host's responsibility — this
 * test boots the bundled Ktor host (`configureDidManager`) and verifies that requests carrying
 * the standard headers reach a handler that exposes `sessionInstance.graph.didManagerHttpAdapter`
 * with the tenant + principal already resolved on `execution.tenantId` / `execution.principalId`.
 *
 * The test deliberately uses an in-process probe route rather than the universal adapter
 * catch-all because [DidManagerHttpAdapter] is `SessionScope`-bound; the probe forwards the
 * `GenericHttpRequest` through `adapter.handleRequest` *under* the plugin-resolved session, so
 * `execution.tenantId` reflects the inbound `X-Tenant-ID` exactly as it would in production.
 */
class DidManagerKtorTenantHeaderTest {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * In-test resolver that reads `X-Tenant-ID` per call, falling back to "anonymous" when the
     * header is absent. Header-based tenant resolution was deliberately removed from the
     * production plugin (client headers cannot be trusted), but this test exists specifically
     * to verify the wire-level forwarding contract through KotlinInjectPlugin — so we install
     * a trusted-header resolver locally to exercise the path.
     */
    private class HeaderTenantResolver : TenantResolver {
        override fun resolve(call: ApplicationCall): TenantInput = DefaultTenantInputString(call.request.headers["X-Tenant-ID"] ?: "anonymous")
    }

    @Test
    fun listDids_underTenantHeaders_routesUnderResolvedTenant() =
        testApplication {
            DefaultPrincipalMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.softwaretest.type" to "software",
                    "kms.providers.softwaretest.id" to "softwaretest",
                    "kms.providers.softwaretest.keystore.type" to "memory",
                    "kms.providers.softwaretest.keystore.id" to "ktor-tenant-keystore",
                    "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                ),
            )
            val appGraph = createDidManagerAppGraph(application = this, appId = "did-manager-ktor-tenant")
            try {
                application {
                    configureDidManager(appGraph, tenantResolver = HeaderTenantResolver())
                    routing {
                        // Probe route forwards the inbound Ktor call to the session-bound DID
                        // manager adapter; this is the same dispatch path the
                        // `installUniversalHttpAdapters` catch-all would take, but pinned to
                        // a known path so the test is independent of that wiring detail.
                        get("/probe/list-dids") {
                            val session: SessionInstance = call.sessionInstance
                            val adapter = (session.graph as DidManagerHttpAdapter.Graph).didManagerHttpAdapter
                            val response =
                                adapter.handleRequest(
                                    GenericHttpRequest(method = "GET", path = "/api/did/v1/identifiers"),
                                )
                            call.respondText(
                                text =
                                    "tenant=${session.sessionExecution.tenantId};" +
                                        "principal=${session.sessionExecution.principalId};" +
                                        "status=${response.statusCode};body=${response.body}",
                            )
                        }
                    }
                }

                val responseAlpha =
                    client.get("/probe/list-dids") {
                        headers.append("X-Tenant-ID", "tenant-alpha")
                        headers.append("X-User-ID", "user-1")
                    }
                assertEquals(HttpStatusCode.OK, responseAlpha.status, "probe must reach the handler under tenant-alpha")
                val bodyA = responseAlpha.bodyAsText()
                assertTrue(
                    "tenant=tenant-alpha" in bodyA,
                    "X-Tenant-ID must reach the session context as 'tenant-alpha' (got: $bodyA)",
                )
                assertTrue(
                    "principal=user-1" in bodyA,
                    "X-User-ID must reach the session context as 'user-1' (got: $bodyA)",
                )
                assertTrue(
                    "status=200" in bodyA,
                    "DidManagerHttpAdapter list endpoint must return 200 under resolved session (got: $bodyA)",
                )
                val statusBodyA = bodyA.substringAfter("body=")
                val parsedA = json.parseToJsonElement(statusBodyA)
                assertTrue(
                    parsedA is JsonObject && parsedA["items"] != null,
                    "list response under tenant-alpha must be a DidListResponse envelope (body=$statusBodyA)",
                )

                val responseBeta =
                    client.get("/probe/list-dids") {
                        headers.append("X-Tenant-ID", "tenant-beta")
                        headers.append("X-User-ID", "user-2")
                    }
                assertEquals(HttpStatusCode.OK, responseBeta.status)
                val bodyB = responseBeta.bodyAsText()
                assertTrue("tenant=tenant-beta" in bodyB, "tenant-beta header must reach context (got: $bodyB)")
                assertTrue("principal=user-2" in bodyB, "user-2 header must reach context (got: $bodyB)")
            } finally {
                appGraph.userContextManager.destroyAll()
            }
        }

    @Test
    fun listDids_withoutTenantHeader_routesUnderAnonymous() =
        testApplication {
            DefaultPrincipalMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.softwaretest.type" to "software",
                    "kms.providers.softwaretest.id" to "softwaretest",
                    "kms.providers.softwaretest.keystore.type" to "memory",
                    "kms.providers.softwaretest.keystore.id" to "ktor-tenant-keystore-anon",
                    "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                ),
            )
            val appGraph = createDidManagerAppGraph(application = this, appId = "did-manager-ktor-anon")
            try {
                application {
                    configureDidManager(appGraph, tenantResolver = HeaderTenantResolver())
                    routing {
                        get("/probe/list-dids") {
                            val session: SessionInstance = call.sessionInstance
                            val adapter = (session.graph as DidManagerHttpAdapter.Graph).didManagerHttpAdapter
                            val response =
                                adapter.handleRequest(
                                    GenericHttpRequest(method = "GET", path = "/api/did/v1/identifiers"),
                                )
                            call.respondText(
                                "tenant=${session.sessionExecution.tenantId};status=${response.statusCode}",
                            )
                        }
                    }
                }

                val response = client.get("/probe/list-dids")
                assertEquals(
                    HttpStatusCode.OK,
                    response.status,
                    "anonymous request must still reach the adapter without headers",
                )
                val body = response.bodyAsText()
                assertNotNull(body)
                assertTrue("status=200" in body, "anonymous list endpoint must return 200 (got: $body)")
            } finally {
                appGraph.userContextManager.destroyAll()
            }
        }

    /**
     * Hits the real URL `/api/did/v1/identifiers` through the universal dispatcher
     * (`installUniversalHttpAdapters` inside `configureDidManager`), not a hand-rolled probe
     * route. This catches the class of bug where `*HttpAdapterDescriptorProvider` ships
     * relative endpoint paths instead of full host-facing paths: the catalog then fails to
     * match and the request lands on a 404.
     *
     * Functionally equivalent to the probe-route test above, but pinned to the production
     * dispatch path so a descriptor-provider regression fails this test even when the
     * runtime adapter is wired correctly.
     */
    @Test
    fun listDids_throughUniversalDispatcher_returnsOk() =
        testApplication {
            DefaultPrincipalMapPropertySource.addProperties(
                mapOf(
                    "kms.providers.softwaretest.type" to "software",
                    "kms.providers.softwaretest.id" to "softwaretest",
                    "kms.providers.softwaretest.keystore.type" to "memory",
                    "kms.providers.softwaretest.keystore.id" to "ktor-universal-keystore",
                    "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                ),
            )
            val appGraph = createDidManagerAppGraph(application = this, appId = "did-manager-ktor-universal")
            try {
                application {
                    configureDidManager(appGraph, tenantResolver = HeaderTenantResolver())
                }

                // Send the full OpenAPI listDids query payload, including the typed-enum
                // params (`sort` / `sortDirection`) in their documented camelCase wire form.
                // A no-param GET only exercises the all-defaults decode path and would miss
                // wire-vs-enum mismatches; downstream dispatchers (e.g. VDX-infra's
                // BinaryCommandAdapter) deserialize the query JSON directly into DidFilter
                // and reject any enum value that isn't present as either the Kotlin enum name
                // OR an @SerialName on the case. Pin every documented param so a future
                // OpenAPI-vs-enum drift fails this test rather than only failing in production.
                val response =
                    client.get("/api/did/v1/identifiers?includeDeactivated=false&includeDeleted=false&page=0&size=20&sort=createdAt&sortDirection=DESC") {
                        headers.append("X-Tenant-ID", "tenant-universal")
                        headers.append("X-User-ID", "user-universal")
                    }
                assertEquals(
                    HttpStatusCode.OK,
                    response.status,
                    "Catalog must route GET /api/did/v1/identifiers to DidLifecycleHttpAdapter via the universal dispatcher. " +
                        "A 404 here typically means a *HttpAdapterDescriptorProvider returned relative endpoint paths " +
                        "instead of paths prefixed with adapterBasePath. A 400 typically means a typed-enum wire " +
                        "value (e.g. sort=createdAt) doesn't match a DidSortField @SerialName, so the request " +
                        "fails to deserialize into DidFilter in any caller that bypasses parseSortField.",
                )
                val body = response.bodyAsText()
                val parsed = json.parseToJsonElement(body)
                assertTrue(
                    parsed is JsonObject && parsed["items"] != null,
                    "list response must be a DidListResponse envelope (body=$body)",
                )
            } finally {
                appGraph.userContextManager.destroyAll()
            }
        }
}

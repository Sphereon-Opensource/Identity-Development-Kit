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

import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.AlsoKnownAsHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.CapabilityHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.ControllerHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.DidLifecycleHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.DidServiceHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.DocumentCacheHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.EquivalentIdHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.KeyMappingHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.VerificationMethodHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.adapter.describe.VerificationRelationshipHttpAdapterDescriptorProvider
import com.sphereon.did.manager.rest.server.ktor.createDidManagerAppGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity test catching drift between the per-resource `*HttpAdapter` wired endpoint
 * sets and the matching `*HttpAdapterDescriptorProvider` declared catalogs.
 *
 * The composite `DidManagerHttpAdapter` aggregates descriptors from every sub-adapter;
 * the union of every per-resource descriptor provider's catalog must match it exactly.
 * A new endpoint that lands in only one of the two places ships either as an
 * unreachable route or as a mis-described catalog entry — both bad. This test asserts
 * set equality on (method, pathPattern, operationId) over the aggregated view.
 */
class DidManagerAdapterDescriptorParityTest {
    @Test
    fun adapterAndDescriptorProviderAgree() {
        val appGraph = createDidManagerAppGraph(application = this, appId = "did-manager-rest-parity")
        try {
            val userContext = appGraph.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("rest-parity", principalType = com.sphereon.di.context.PrincipalType.USER)
            val composite = TestSessionGraph.fromSession(session).adapter

            val config = DidManagerRestConfig()
            val providers: List<HttpAdapterDescriptorProvider> =
                listOf(
                    DidLifecycleHttpAdapterDescriptorProvider(config),
                    VerificationMethodHttpAdapterDescriptorProvider(config),
                    VerificationRelationshipHttpAdapterDescriptorProvider(config),
                    DidServiceHttpAdapterDescriptorProvider(config),
                    KeyMappingHttpAdapterDescriptorProvider(config),
                    ControllerHttpAdapterDescriptorProvider(config),
                    AlsoKnownAsHttpAdapterDescriptorProvider(config),
                    EquivalentIdHttpAdapterDescriptorProvider(config),
                    DocumentCacheHttpAdapterDescriptorProvider(config),
                    CapabilityHttpAdapterDescriptorProvider(config),
                )

            val adapterKeys =
                composite.endpointDescriptors
                    .map { Triple(it.method, it.pathPattern, it.operationId) }
                    .toSet()
            val providerEndpoints = providers.flatMap { it.describe().endpoints }
            val providerKeys = providerEndpoints.map { Triple(it.method, it.pathPattern, it.operationId) }.toSet()

            assertEquals(
                providerKeys,
                adapterKeys,
                "Aggregated adapter endpoint set diverged from aggregated descriptor provider catalog. " +
                    "Add the new endpoint to BOTH the matching *HttpAdapter (ctor + endpointCommands) " +
                    "AND its *HttpAdapterDescriptorProvider, or remove it from both.",
            )
            assertEquals(adapterKeys.size, composite.endpointDescriptors.size, "Adapter endpoint set has duplicates.")
            assertEquals(providerKeys.size, providerEndpoints.size, "Descriptor provider list has duplicates.")

            // Catalog routing depends on descriptor-provider endpoints carrying the FULL
            // host-facing path (adapterBasePath + relative pattern). A descriptor provider
            // that forgets to apply adapterBasePath ships an unreachable route — the same
            // class of bug as embedding the prefix in the raw HttpEndpointCommand.ENDPOINT
            // pathPattern, just one layer later. Pin both expectations explicitly. The
            // expected prefix is sourced from DidManagerRestPaths so a future rebrand of
            // the default base path doesn't leave this assertion silently wrong.
            val expectedPrefix = "${DidManagerRestPaths.BASE_PATH}/"
            providerEndpoints.forEach { ep ->
                assertTrue(
                    ep.pathPattern.startsWith(expectedPrefix),
                    "Descriptor provider endpoint must include the adapter basePath " +
                        "'${DidManagerRestPaths.BASE_PATH}'; got '${ep.pathPattern}'. Extend " +
                        "StaticPublicApiDescriptor (or otherwise prepend adapterBasePath in " +
                        "describe()) so the catalog can route this endpoint.",
                )
            }
            composite.endpointDescriptors.forEach { ep ->
                assertTrue(
                    ep.pathPattern.startsWith(expectedPrefix),
                    "Adapter endpoint descriptor must include the adapter basePath " +
                        "'${DidManagerRestPaths.BASE_PATH}'; got '${ep.pathPattern}'.",
                )
            }
        } finally {
            appGraph.userContextManager.destroyAll()
        }
    }
}

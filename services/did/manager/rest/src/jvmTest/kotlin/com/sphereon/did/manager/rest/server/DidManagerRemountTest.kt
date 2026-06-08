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

import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.did.manager.rest.server.adapter.DidManagerHttpAdapter
import com.sphereon.did.manager.rest.server.ktor.createDidManagerAppGraph
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the DID Manager REST surface is *actually* remountable.
 *
 * This is the test the Codex follow-up flagged as missing: even though every adapter and
 * descriptor provider was updated to consume [DidManagerRestConfig], nothing in the
 * existing suite drives the dispatcher with a non-default `adapterBasePath`. Without this
 * test, a future change that hardcodes `/api/did/v1` somewhere in the routing chain would
 * pass CI while silently making the surface un-remountable.
 *
 * The test overrides [DidManagerRestPaths.BASE_PATH_CONFIG_KEY] at the AppScope property
 * source *before* the app graph is built, so the [DidManagerRestConfigModule] @Provides
 * picks up the override during DI graph construction. It then asserts:
 *
 *  1. Every endpoint descriptor advertises a path under the custom prefix — proves all
 *     descriptor providers consume the config and no hardcoded literal remains in them.
 *  2. The runtime adapter's full endpoint set (method × path × operationId) matches an
 *     explicit descriptor-provider catalog built with the same config — proves the
 *     adapter side honors the override identically to the descriptor side, so the
 *     catalog and the routing chain can't silently diverge under a remount.
 *
 * Together these guard every place the literal *could* leak back in.
 */
class DidManagerRemountTest {
    private lateinit var app: com.sphereon.di.app.AppGraph
    private lateinit var adapter: DidManagerHttpAdapter

    @BeforeTest
    fun setUp() {
        // Override the base path BEFORE constructing the graph — the @Provides runs at
        // injection time and reads the property exactly once into DidManagerRestConfig.
        DefaultAppMapPropertySource.addProperty(
            DidManagerRestPaths.BASE_PATH_CONFIG_KEY,
            CUSTOM_BASE_PATH,
        )

        app = createDidManagerAppGraph(application = this, appId = "did-manager-rest-remount")
        app.userContextManager.destroyAll()
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("rest-remount")
        adapter = TestSessionGraph.fromSession(session).adapter
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) app.userContextManager.destroyAll()
        // Clean up the override so it doesn't leak into other test classes that share
        // the same JVM (the property source is a global singleton).
        DefaultAppMapPropertySource.deleteProperty(DidManagerRestPaths.BASE_PATH_CONFIG_KEY)
    }

    @Test
    fun descriptors_advertisePathsUnderCustomPrefix() {
        val descriptors = adapter.endpointDescriptors
        assertTrue(descriptors.isNotEmpty(), "expected at least one endpoint descriptor")
        descriptors.forEach { descriptor ->
            descriptor.pathPatterns.forEach { pattern ->
                assertTrue(
                    pattern.startsWith("$CUSTOM_BASE_PATH/") || pattern == CUSTOM_BASE_PATH,
                    "Endpoint ${descriptor.method} ${descriptor.operationId} advertised " +
                        "pattern '$pattern' that does not start with custom base path " +
                        "'$CUSTOM_BASE_PATH'. A hardcoded literal is still present somewhere " +
                        "in the routing chain.",
                )
                assertTrue(
                    !pattern.startsWith(DidManagerRestPaths.BASE_PATH),
                    "Endpoint ${descriptor.method} ${descriptor.operationId} still advertises " +
                        "the default base path '${DidManagerRestPaths.BASE_PATH}' in pattern " +
                        "'$pattern' despite the config override.",
                )
            }
        }
    }

    @Test
    fun adapterAndDescriptorProviderAgreeOnOverride() {
        // The DI-resolved runtime adapter's endpoint paths must match what an explicit
        // descriptor provider would produce *with the same config*. This rules out the
        // failure mode where descriptors get remounted but adapters don't (or vice versa)
        // because one side reads from config and the other still hardcodes the literal.
        val explicitConfig = DidManagerRestConfig(adapterBasePath = CUSTOM_BASE_PATH)
        val explicitDescriptorProviders =
            listOf(
                com.sphereon.did.manager.rest.server.adapter.describe
                    .DidLifecycleHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .VerificationMethodHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .VerificationRelationshipHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .DidServiceHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .KeyMappingHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .ControllerHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .AlsoKnownAsHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .EquivalentIdHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .DocumentCacheHttpAdapterDescriptorProvider(explicitConfig),
                com.sphereon.did.manager.rest.server.adapter.describe
                    .CapabilityHttpAdapterDescriptorProvider(explicitConfig),
            )

        val adapterKeys =
            adapter.endpointDescriptors
                .map { Triple(it.method, it.pathPattern, it.operationId) }
                .toSet()
        val expectedKeys =
            explicitDescriptorProviders
                .flatMap { it.describe().endpoints }
                .map { Triple(it.method, it.pathPattern, it.operationId) }
                .toSet()

        assertEquals(
            expectedKeys,
            adapterKeys,
            "Runtime adapter endpoint set under the overridden base path '$CUSTOM_BASE_PATH' " +
                "diverged from the explicit descriptor-provider catalog built with the same " +
                "config. One side is still hardcoding '${DidManagerRestPaths.BASE_PATH}'.",
        )
    }

    private companion object {
        const val CUSTOM_BASE_PATH = "/custom/dids"
    }
}

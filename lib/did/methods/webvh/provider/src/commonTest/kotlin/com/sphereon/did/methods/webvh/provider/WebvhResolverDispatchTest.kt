/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.provider

import com.sphereon.di.app.AppGraph
import com.sphereon.did.methods.webvh.WebvhDidCapabilities
import com.sphereon.did.methods.webvh.provider.testutil.createWebvhProviderTestAppGraph
import com.sphereon.did.methods.webvh.resolver.WebvhDidResolverImpl
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.impl.DidResolverRegistryImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Sanity test for the multibinding wiring: when `:lib-did-methods-webvh-resolver`
 * is on the classpath, [DidResolverRegistry] MUST return a `WebvhDidResolverImpl`
 * for the `webvh` method via Metro multibinding contribution
 * (`@ContributesIntoSet(SessionScope::class, binding = binding<DidResolver>())`).
 *
 * Without this, `ResolveDidCommandImpl.resolve("did:webvh:...")` would return
 * `null` from `getResolver("webvh")` and silently fail to dispatch — making
 * webvh unreachable through the Universal Resolver REST endpoint at
 * `/1.0/identifiers/did:webvh:...`.
 */
class WebvhResolverDispatchTest {
    private lateinit var app: AppGraph
    private lateinit var registry: DidResolverRegistry

    @BeforeTest
    fun setUp() {
        app = createWebvhProviderTestAppGraph(testInstance = this)
        app.userContextManager.destroyAll()
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("webvh-resolver-dispatch-test")
        registry = (session.graph as DidResolverRegistryImpl.Graph).didResolverRegistry
    }

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @Test
    fun registryReturnsWebvhResolverForWebvhMethod() {
        val resolver = registry.getResolver(WebvhDidCapabilities.METHOD)
        assertNotNull(resolver, "DidResolverRegistry must have a resolver for the 'webvh' method")
        assertTrue(
            resolver is WebvhDidResolverImpl,
            "Resolver for 'webvh' should be WebvhDidResolverImpl, was ${resolver::class.simpleName}",
        )
    }

    @Test
    fun registryAdvertisesWebvhCapabilities() {
        val capabilities = registry.getCapabilities(WebvhDidCapabilities.METHOD)
        assertNotNull(capabilities, "DidResolverRegistry must advertise capabilities for 'webvh'")
        assertTrue(capabilities.lifecycle.create, "webvh must advertise create capability")
        assertTrue(capabilities.lifecycle.update, "webvh must advertise update capability")
        assertTrue(capabilities.lifecycle.deactivate, "webvh must advertise deactivate capability")
    }
}

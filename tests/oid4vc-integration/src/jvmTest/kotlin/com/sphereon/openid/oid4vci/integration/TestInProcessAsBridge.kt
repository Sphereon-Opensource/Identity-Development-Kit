/*
 * Copyright 2026 Sphereon International B.V.
 * Test-only public accessor for the production in-process authorization-server bridge.
 */
package com.sphereon.openid.oid4vci.integration

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.impl.bridge.SphereonAsBridge
import dev.zacsweers.metro.ContributesTo
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

@ContributesTo(SessionScope::class)
interface InProcessAsBridgeTestGraph {
    val oid4vciAuthorizationServerBridge: Oid4vciAuthorizationServerBridge
}

class InProcessAsBridgeCompositionTest {
    @Test
    fun publicBridgeSelectsTheSameProductionInProcessAuthority() {
        val ctx = Oid4vciTestContext(this)
        val graph = ctx.session.graph as InProcessAsBridgeTestGraph
        val bridge = graph.oid4vciAuthorizationServerBridge
        assertIs<SphereonAsBridge>(bridge)
        assertSame(bridge, graph.oid4vciAuthorizationServerBridge)
    }
}

/*
 * (c) 2026 Sphereon International B.V.
 * Test-only binding that forces SphereonAsBridge (in-process) over HttpAsBridge
 * when both are on the classpath.
 */
package com.sphereon.openid.oid4vci.integration

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.impl.bridge.SphereonAsBridge
import com.sphereon.openid.oid4vci.issuer.impl.http.HttpAsBridge
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * In tests, we want the in-process AS bridge (SphereonAsBridge) rather than HttpAsBridge
 * which tries to connect to localhost:8080. This binding replaces HttpAsBridge.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciAuthorizationServerBridge>(), replaces = [HttpAsBridge::class])
class TestInProcessAsBridge(
    private val delegate: SphereonAsBridge,
) : Oid4vciAuthorizationServerBridge by delegate

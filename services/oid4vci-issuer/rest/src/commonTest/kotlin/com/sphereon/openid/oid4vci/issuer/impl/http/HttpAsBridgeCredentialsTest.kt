/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import kotlin.test.Test
import kotlin.test.assertEquals

class HttpAsBridgeCredentialsTest {
    @Test
    fun activeTenantAuthorizationServerIssuerClientTakesPrecedence() {
        val properties = mapOf(
            "oauth2.servers.default-server" to "acme",
            "oauth2.servers.acme.internal-clients.issuer.client-id" to "issuer-service:tenant-123",
            "oauth2.servers.acme.internal-clients.issuer.client-secret-id" to "secret_tenant_issuer_01",
            "oid4vci.issuer.as-bridge.client-id" to "bootstrap-issuer",
            "oid4vci.issuer.as-bridge.client-secret-id" to "secret_bootstrap_issuer_01",
        )

        assertEquals(
            AsBridgeClientCredentialReference("issuer-service:tenant-123", "secret_tenant_issuer_01"),
            resolveAsBridgeClientCredentialReference(properties::get),
        )
    }

    @Test
    fun explicitBridgeCredentialRemainsStandaloneFallback() {
        val properties = mapOf(
            "oid4vci.issuer.as-bridge.client-id" to "standalone-issuer",
            "oid4vci.issuer.as-bridge.client-secret-id" to "secret_standalone_issuer_01",
        )

        assertEquals(
            AsBridgeClientCredentialReference("standalone-issuer", "secret_standalone_issuer_01"),
            resolveAsBridgeClientCredentialReference(properties::get),
        )
    }

    @Test
    fun scopedClientIdIsFormEncodedBeforeBasicAuthJoining() {
        val credentials = AsBridgeClientCredentials("issuer-service:tenant-123", "tenant-secret")

        assertEquals(
            "Basic aXNzdWVyLXNlcnZpY2UlM0F0ZW5hbnQtMTIzOnRlbmFudC1zZWNyZXQ=",
            credentials.toBasicAuthHeader(),
        )
    }
}

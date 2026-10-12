/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.openid.oid4vp.universal.impl.describe

import com.sphereon.core.api.http.config.UniversalHttpConfig
import com.sphereon.core.api.http.dispatch.DefaultHttpAdapterCatalog
import com.sphereon.core.api.http.dispatch.DefaultHttpAdapterRouteSelector
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteSelection
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.describe.Oid4vpVerifierDescriptorProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UniversalOid4vpDescriptorProviderTest {
    private val selector =
        DefaultHttpAdapterRouteSelector(
            DefaultHttpAdapterCatalog(
                setOf(UniversalOid4vpDescriptorProvider(), Oid4vpVerifierDescriptorProvider()),
                UniversalHttpConfig.DEFAULT,
            ),
        )

    @Test
    fun statusRouteIsReachableOnHostAndTenantScopedPublicPrefixes() {
        for (path in listOf(
            "/oid4vp/backend/auth/requests/correlation-1",
            "/oid4vp/tenant-a/oid4vp/backend/auth/requests/correlation-1",
        )) {
            val selection = assertIs<HttpAdapterRouteSelection.Selected>(selector.select("GET", path), path)
            assertEquals(GetAuthRequestStatusEndpointCommand.COMMAND_ID, selection.match.commandId, path)
        }
    }

    @Test
    fun tenantScopedPrefixWithoutTheOid4vpMountSegmentDoesNotRoute() {
        assertIs<HttpAdapterRouteSelection.NotFound>(
            selector.select("GET", "/oid4vp/tenant-a/backend/auth/requests/correlation-1"),
        )
    }

    @Test
    fun universalAndVerifierProtocolAdaptersDeclareTheSameTenantPathPolicy() {
        assertEquals(
            Oid4vpVerifierDescriptorProvider().describe().mount.tenantPathPolicy,
            UniversalOid4vpDescriptorProvider().describe().mount.tenantPathPolicy,
        )
    }
}

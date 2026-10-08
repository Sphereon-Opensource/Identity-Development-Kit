/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.oauth2.server.authorization.command.discovery

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DiscoveryHttpCommandsTest {
    @Test
    fun discoveryAdapterIdentityIsPublicAndStable() {
        assertEquals("oauth2.as.discovery", OAuth2DiscoveryHttpContract.ADAPTER_ID)
    }

    @Test
    fun discoveryEndpointDescriptorsDeclareEveryCataloguedPath() {
        assertContains(
            OAuth2ServerMetadataHttpEndpointCommand.ENDPOINT.pathPatterns,
            OAuth2ServerMetadataHttpEndpointCommand.TENANT_PATH_PATTERN,
        )
        assertContains(
            OpenidDiscoveryHttpEndpointCommand.ENDPOINT.pathPatterns,
            OpenidDiscoveryHttpEndpointCommand.TENANT_PATH_PATTERN,
        )
    }
}

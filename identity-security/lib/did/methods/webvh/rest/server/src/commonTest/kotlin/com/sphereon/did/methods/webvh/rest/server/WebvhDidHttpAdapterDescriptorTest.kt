/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.rest.server

import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.FetchWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.rest.server.describe.WebvhDidHttpAdapterDescriptorProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests the app-scoped descriptor that exposes the `did:webvh` REST adapter
 * to `HttpAdapterCatalog` at startup. Verifies all 6 endpoints are
 * advertised at the spec'd paths under `/api/v1/did/webvh` with the right
 * HTTP methods + commandIds, so route collision detection can run before
 * any session is created.
 *
 * Pure data-shape test — no DI, no HTTP server, runs on every KMP target.
 */
class WebvhDidHttpAdapterDescriptorTest {
    private val descriptor: WebvhDidHttpAdapterDescriptorProvider = WebvhDidHttpAdapterDescriptorProvider()

    @Test
    fun adapterIsMountedAtVersionedBasePath() {
        val description = descriptor.describe()
        assertEquals("", description.mount.serverPrefix)
        assertEquals(WebvhDidHttpAdapter.BASE_PATH, description.mount.adapterBasePath)
        assertEquals("/api/v1/did/webvh", description.mount.adapterBasePath, "Per IDK convention all new endpoints must be /api/v1/...")
    }

    @Test
    fun adapterAdvertisesAllSevenWebvhEndpoints() {
        val description = descriptor.describe()
        val commandIds = description.endpoints.mapNotNull { it.commandId }.toSet()
        val expected =
            setOf(
                CreateWebvhDidServiceCommand.COMMAND_ID,
                UpdateWebvhDidServiceCommand.COMMAND_ID,
                DeactivateWebvhDidServiceCommand.COMMAND_ID,
                CreateWitnessProofServiceCommand.COMMAND_ID,
                UpdateWitnessFileServiceCommand.COMMAND_ID,
                FetchWebvhLogServiceCommand.COMMAND_ID,
                com.sphereon.did.methods.webvh.command
                    .ReplayWebvhLogServiceCommand.COMMAND_ID,
            )
        assertEquals(expected, commandIds, "Adapter must expose all seven webvh commands (six lifecycle + replay-log)")
    }

    @Test
    fun createUsesPostAtBasePath() {
        val endpoint = descriptor.describe().endpoints.firstOrNull { it.commandId == CreateWebvhDidServiceCommand.COMMAND_ID }
        assertNotNull(endpoint)
        assertEquals(HttpMethod.POST, endpoint.method)
        // The descriptor absolutizes endpoint paths against the adapter base.
        assertTrue(
            endpoint.pathPattern.startsWith(WebvhDidHttpAdapter.BASE_PATH),
            "create endpoint should be mounted under ${WebvhDidHttpAdapter.BASE_PATH}; was ${endpoint.pathPattern}",
        )
    }

    @Test
    fun updateUsesPutWithDidPath() {
        val endpoint = descriptor.describe().endpoints.firstOrNull { it.commandId == UpdateWebvhDidServiceCommand.COMMAND_ID }
        assertNotNull(endpoint)
        assertEquals(HttpMethod.PUT, endpoint.method)
        assertTrue(endpoint.pathPattern.contains("{did}"), "update endpoint pathPattern must include {did}; was ${endpoint.pathPattern}")
    }

    @Test
    fun deactivateUsesDeleteWithDidPath() {
        val endpoint = descriptor.describe().endpoints.firstOrNull { it.commandId == DeactivateWebvhDidServiceCommand.COMMAND_ID }
        assertNotNull(endpoint)
        assertEquals(HttpMethod.DELETE, endpoint.method)
        assertTrue(endpoint.pathPattern.contains("{did}"))
    }

    @Test
    fun fetchLogUsesGetWithDidPathAndLogSuffix() {
        val endpoint = descriptor.describe().endpoints.firstOrNull { it.commandId == FetchWebvhLogServiceCommand.COMMAND_ID }
        assertNotNull(endpoint)
        assertEquals(HttpMethod.GET, endpoint.method)
        assertTrue(endpoint.pathPattern.contains("{did}"))
        assertTrue(endpoint.pathPattern.endsWith("/log"), "fetch-log endpoint pathPattern must end with /log; was ${endpoint.pathPattern}")
    }

    @Test
    fun witnessProofEndpointsUseDistinctSubpaths() {
        val description = descriptor.describe()
        val createWitness = description.endpoints.firstOrNull { it.commandId == CreateWitnessProofServiceCommand.COMMAND_ID }
        val updateWitnessFile = description.endpoints.firstOrNull { it.commandId == UpdateWitnessFileServiceCommand.COMMAND_ID }
        assertNotNull(createWitness)
        assertNotNull(updateWitnessFile)
        // Per the original plan the two distinct witness sub-resources are not the same URL.
        assertTrue(
            createWitness.pathPattern != updateWitnessFile.pathPattern ||
                createWitness.method != updateWitnessFile.method,
            "witness endpoints must be distinguishable by (method, path)",
        )
    }

    @Test
    fun adapterIdMatchesAdvertisedDescriptor() {
        assertEquals(WebvhDidHttpAdapter.ID, descriptor.describe().id)
    }
}

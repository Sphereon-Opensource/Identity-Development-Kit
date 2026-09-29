/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
import com.sphereon.crypto.kms.rest.server.service.KeyProviderPresentationSource
import com.sphereon.crypto.kms.rest.server.service.ProvidersRestService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

class ProvidersHttpAdapterDeleteKeyTest {
    @Test
    fun deleteThatRemovedNothingIsNotFound() =
        runTest {
            val response = ProvidersHttpAdapter(providersService(deleted = false), unusedPresentation()).handleResolvedRequest(request(), route())

            assertEquals(404, response.statusCode)
        }

    @Test
    fun deleteThatRemovedTheKeyIsNoContent() =
        runTest {
            val response = ProvidersHttpAdapter(providersService(deleted = true), unusedPresentation()).handleResolvedRequest(request(), route())

            assertEquals(204, response.statusCode)
        }

    private fun request() = GenericHttpRequest(method = "DELETE", path = "/providers/resource-1/keys/key-1")

    private fun route() =
        HttpAdapterRouteMatch(
            adapterId = ProvidersHttpAdapter.ID,
            method = "DELETE",
            originalPath = "/providers/resource-1/keys/key-1",
            normalizedPath = "/providers/resource-1/keys/key-1",
            matchedPathPattern = "/providers/{providerId}/keys/{aliasOrKid}",
            handlerCommandId = "kms.providers.delete-key",
            tenantIdFromPath = null,
        )

    /** Only providerDeleteKey is reachable from the delete route; every other member fails the test. */
    private fun providersService(deleted: Boolean): ProvidersRestService =
        Proxy.newProxyInstance(
            ProvidersRestService::class.java.classLoader,
            arrayOf(ProvidersRestService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "providerDeleteKey" -> {
                    assertEquals("resource-1", args[0])
                    assertEquals("key-1", args[1])
                    deleted
                }
                else -> error("ProvidersRestService.${method.name} must not be called")
            }
        } as ProvidersRestService

    private fun unusedPresentation(): KeyProviderPresentationSource =
        Proxy.newProxyInstance(
            KeyProviderPresentationSource::class.java.classLoader,
            arrayOf(KeyProviderPresentationSource::class.java),
        ) { _, method, _ -> error("KeyProviderPresentationSource.${method.name} must not be called") }
            as KeyProviderPresentationSource
}

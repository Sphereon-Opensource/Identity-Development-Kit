/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderQuery
import com.sphereon.crypto.kms.rest.server.service.CapabilitiesRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class CapabilitiesHttpAdapter(
    private val capabilitiesService: CapabilitiesRestService,
) : RoutedHttpAdapter() {
    companion object {
        const val ID = "KMS-CAPABILITIES"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "")

    override val routes =
        httpRoutes {
            get("/capabilities") {
                operationId("listCapabilities")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListCapabilities(req) }
            }
            get("/providers/{providerId}/capabilities") {
                operationId("getProviderCapabilities")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetProviderCapabilities(req) }
            }
            post("/providers/query") {
                operationId("queryProviders")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleQueryProviders(req) }
            }
            post("/providers/query/best") {
                operationId("queryBestProvider")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleQueryBestProvider(req) }
            }
        }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val capabilitiesHttpAdapter: CapabilitiesHttpAdapter
    }

    private val json: Json get() = JsonConfig.instance

    private suspend fun handleListCapabilities(request: GenericHttpRequest): GenericHttpResponse =
        try {
            val includeDisabled = request.queryParams["includeDisabled"]?.toBooleanStrictOrNull() ?: false
            jsonResponse(200, json.encodeToString(capabilitiesService.listCapabilities(includeDisabled)))
        } catch (expected: Exception) {
            errorResponse(expected)
        }

    private suspend fun handleGetProviderCapabilities(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/providers/{providerId}/capabilities")
        val providerId = req.pathParams["providerId"] ?: return errorResponse(400, "Missing path parameter: providerId")
        return try {
            jsonResponse(200, json.encodeToString(capabilitiesService.getProviderCapabilities(providerId)))
        } catch (expected: Exception) {
            errorResponse(expected)
        }
    }

    private suspend fun handleQueryProviders(request: GenericHttpRequest): GenericHttpResponse {
        val body = request.body ?: return errorResponse(400, "Missing request body")
        val query =
            try {
                json.decodeFromString<ProviderQuery>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }
        return try {
            jsonResponse(200, json.encodeToString(capabilitiesService.queryProviders(query)))
        } catch (expected: Exception) {
            errorResponse(expected)
        }
    }

    private suspend fun handleQueryBestProvider(request: GenericHttpRequest): GenericHttpResponse {
        val body = request.body ?: return errorResponse(400, "Missing request body")
        val query =
            try {
                json.decodeFromString<ProviderQuery>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }
        return try {
            jsonResponse(200, json.encodeToString(capabilitiesService.queryBestProvider(query)))
        } catch (expected: Exception) {
            errorResponse(expected)
        }
    }
}

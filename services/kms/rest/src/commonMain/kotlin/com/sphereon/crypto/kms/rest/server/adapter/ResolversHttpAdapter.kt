/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.kms.rest.api.generated.models.ListResolversResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ResolvePublicKey
import com.sphereon.crypto.kms.rest.api.generated.models.ResolvedKeyInfo
import com.sphereon.crypto.kms.rest.api.generated.models.Resolver
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toRestResponse
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.server.service.ResolversRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * HTTP adapter for KMS Resolvers API.
 *
 * Handles all resolver-related endpoints:
 * - GET /resolvers - List all resolvers
 * - GET /resolvers/{resolverId} - Get resolver details
 * - POST /resolvers/{resolverId}/resolve - Resolve a public key
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class ResolversHttpAdapter(
    private val resolversService: ResolversRestService,
) : RoutedHttpAdapter() {
    companion object {
        const val ID = "KMS-RESOLVERS"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/resolvers",
        )

    // Routes are relative to the mount (adapterBasePath = /resolvers)
    override val routes =
        httpRoutes {
            get("/") {
                operationId("listResolvers")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListResolvers(req) }
            }
            get("/{resolverId}") {
                operationId("getResolver")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetResolver(req) }
            }
            post("/{resolverId}/resolve") {
                operationId("resolvePublicKey")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleResolveKey(req) }
            }
        }

    /**
     * Contributes this adapter as a property to the SessionGraph.
     * Without this, @ContributesBinding only adds to a multibinding Set that isn't accessible.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val resolversHttpAdapter: ResolversHttpAdapter
    }

    private val json: Json get() = JsonConfig.instance

    private suspend fun handleListResolvers(request: GenericHttpRequest): GenericHttpResponse {
        val resolvers = resolversService.listResolvers().map { it.toRest() }.toTypedArray()
        return jsonResponse(200, json.encodeToString<ListResolversResponse>(resolvers.toRestResponse()))
    }

    private suspend fun handleGetResolver(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/resolvers/{resolverId}")
        val resolverId =
            req.pathParams["resolverId"]
                ?: return errorResponse(400, "Missing path parameter: resolverId")

        val resolver = resolversService.getResolver(resolverId)
        return jsonResponse(200, json.encodeToString<Resolver>(resolver.toRest()))
    }

    private suspend fun handleResolveKey(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/resolvers/{resolverId}/resolve")
        val resolverId =
            req.pathParams["resolverId"]
                ?: return errorResponse(400, "Missing path parameter: resolverId")
        val body =
            req.body
                ?: return errorResponse(400, "Missing request body")

        val resolveRequest =
            try {
                json.decodeFromString<ResolvePublicKey>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val resolvedKeyInfo =
            resolversService.resolveKey(
                resolverId = resolverId,
                keyInfo = resolveRequest.keyInfo.toSdk(),
                identifierMethod = resolveRequest.identifierMethod?.let { IdentifierMethod.valueOf(it.value) },
                trustedCerts = resolveRequest.trustedCerts,
                verifyX509CertificateChain = resolveRequest.verifyX509CertificateChain,
            )
        return jsonResponse(200, json.encodeToString<ResolvedKeyInfo>(resolvedKeyInfo.toRest()))
    }
}

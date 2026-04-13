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
import com.sphereon.core.api.http.createdResponse
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.core.api.http.errorResponse
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.core.api.http.noContentResponse
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKey
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.KeyProvider
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeyProvidersResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKey
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKeyResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toRestResponse
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.server.service.ProvidersRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import com.sphereon.crypto.kms.rest.api.generated.models.KeyOperations as KeyOperationsRest

/**
 * HTTP adapter for KMS Providers API.
 *
 * Handles all provider-related endpoints:
 * - GET /providers - List all providers
 * - GET /providers/{providerId} - Get provider details
 * - GET /providers/{providerId}/keys - List keys in provider
 * - GET /providers/{providerId}/keys/{aliasOrKid} - Get specific key
 * - POST /providers/{providerId}/keys - Store key in provider
 * - POST /providers/{providerId}/keys/generate - Generate key in provider
 * - DELETE /providers/{providerId}/keys/{aliasOrKid} - Delete key from provider
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class ProvidersHttpAdapter(
    private val providersService: ProvidersRestService,
) : RoutedHttpAdapter() {
    companion object {
        const val ID = "KMS-PROVIDERS"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/providers",
        )

    // Routes are relative to the mount (adapterBasePath = /providers)
    override val routes =
        httpRoutes {
            get("/") {
                operationId("listKeyProviders")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListProviders(req) }
            }
            get("/{providerId}") {
                operationId("getKeyProvider")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetProvider(req) }
            }
            get("/{providerId}/keys") {
                operationId("providerListKeys")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListKeys(req) }
            }
            get("/{providerId}/keys/{aliasOrKid}") {
                operationId("providerGetKey")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetKey(req) }
            }
            post("/{providerId}/keys") {
                operationId("providerStoreKey")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleStoreKey(req) }
            }
            post("/{providerId}/keys/generate") {
                operationId("providerGenerateKey")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleGenerateKey(req) }
            }
            delete("/{providerId}/keys/{aliasOrKid}") {
                operationId("providerDeleteKey")
                handle { req -> handleDeleteKey(req) }
            }
        }

    /**
     * Contributes this adapter as a property to the SessionGraph.
     * Without this, @ContributesBinding only adds to a multibinding Set that isn't accessible.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val providersHttpAdapter: ProvidersHttpAdapter
    }

    private val json: Json get() = JsonConfig.instance

    private suspend fun handleListProviders(request: GenericHttpRequest): GenericHttpResponse {
        val providers =
            try {
                providersService.listKeyProviders().map { it.toRest() }.toTypedArray()
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return jsonResponse(200, json.encodeToString<ListKeyProvidersResponse>(providers.toRestResponse()))
    }

    private suspend fun handleGetProvider(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/providers/{providerId}")
        val providerId =
            req.pathParams["providerId"]
                ?: return errorResponse(400, "Missing path parameter: providerId")

        val provider =
            try {
                providersService.getKeyProvider(providerId)
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return jsonResponse(200, json.encodeToString<KeyProvider>(provider.toRest()))
    }

    private suspend fun handleListKeys(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/providers/{providerId}/keys")
        val providerId =
            req.pathParams["providerId"]
                ?: return errorResponse(400, "Missing path parameter: providerId")

        val keyInfos =
            try {
                providersService.providerListKeys(providerId).map { it.toRest() }.toTypedArray()
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return jsonResponse(200, json.encodeToString<ListKeysResponse>(keyInfos.toRestResponse()))
    }

    private suspend fun handleGetKey(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/providers/{providerId}/keys/{aliasOrKid}")
        val providerId =
            req.pathParams["providerId"]
                ?: return errorResponse(400, "Missing path parameter: providerId")
        val aliasOrKid =
            req.pathParams["aliasOrKid"]
                ?: return errorResponse(400, "Missing path parameter: aliasOrKid")

        val keyInfo =
            try {
                providersService.providerGetKey(providerId, aliasOrKid)
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return jsonResponse(200, json.encodeToString<GetKeyResponse>(GetKeyResponse(keyInfo = keyInfo.toRest())))
    }

    private suspend fun handleStoreKey(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/providers/{providerId}/keys")
        val providerId =
            req.pathParams["providerId"]
                ?: return errorResponse(400, "Missing path parameter: providerId")
        val body =
            req.body
                ?: return errorResponse(400, "Missing request body")

        val storeKeyRequest =
            try {
                json.decodeFromString<StoreKey>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val key =
            try {
                providersService.providerStoreKey(
                    providerId = providerId,
                    keyInfo = storeKeyRequest.keyInfo.toSdk(),
                    certChain = storeKeyRequest.certChain,
                )
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return createdResponse(
            "/providers/$providerId/keys/${key.alias}",
            json.encodeToString<StoreKeyResponse>(StoreKeyResponse(keyInfo = key.toRest())),
        )
    }

    private suspend fun handleGenerateKey(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/providers/{providerId}/keys/generate")
        val providerId =
            req.pathParams["providerId"]
                ?: return errorResponse(400, "Missing path parameter: providerId")
        val body =
            req.body
                ?: return errorResponse(400, "Missing request body")

        val generateRequest =
            try {
                json.decodeFromString<GenerateKey>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val keyPair =
            try {
                providersService.providerGenerateKey(
                    alias = generateRequest.alias,
                    providerId = providerId,
                    use = generateRequest.use?.let { JwkUse.valueOf(it.value) },
                    keyOperations =
                        generateRequest.keyOperations
                            ?.map<KeyOperationsRest, KeyOperations> {
                                KeyOperations.fromJose(JoseKeyOperations.valueOf(it.value.uppercase()))
                            }?.toTypedArray(),
                    alg = generateRequest.alg?.let { SignatureAlgorithm.fromValue(it.value) },
                )
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return createdResponse(
            "/providers/$providerId/keys/${keyPair.alias}",
            json.encodeToString<GenerateKeyResponse>(GenerateKeyResponse(keyPair = keyPair.toRest())),
        )
    }

    private suspend fun handleDeleteKey(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/providers/{providerId}/keys/{aliasOrKid}")
        val providerId =
            req.pathParams["providerId"]
                ?: return errorResponse(400, "Missing path parameter: providerId")
        val aliasOrKid =
            req.pathParams["aliasOrKid"]
                ?: return errorResponse(400, "Missing path parameter: aliasOrKid")

        try {
            providersService.providerDeleteKey(providerId, aliasOrKid)
        } catch (expected: Exception) {
            return errorResponse(expected)
        }
        return noContentResponse()
    }
}

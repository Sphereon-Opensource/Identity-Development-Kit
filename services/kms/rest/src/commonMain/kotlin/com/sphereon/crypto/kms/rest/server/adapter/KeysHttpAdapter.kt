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
import com.sphereon.core.api.http.response.createdResponse
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.core.api.http.response.errorResponse
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.core.api.http.response.noContentResponse
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceInput
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceResponse
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKey
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKeyResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.server.service.KmsRestService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import com.sphereon.crypto.kms.rest.api.generated.models.KeyOperations as KeyOperationsRest

/**
 * HTTP adapter for KMS Keys API.
 *
 * Handles all key-related endpoints:
 * - GET /keys - List all keys
 * - GET /keys/{aliasOrKid} - Get specific key
 * - POST /keys - Store a key
 * - POST /keys/generate - Generate a new key
 * - POST /keys/register - Register an existing provider key for platform use
 * - DELETE /keys/{aliasOrKid} - Delete a key
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class KeysHttpAdapter(
    private val kmsService: KmsRestService,
    private val registerCommand: RegisterKeyReferenceServiceCommand,
) : RoutedHttpAdapter() {
    companion object {
        const val ID = "KMS-KEYS"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/keys",
        )

    // Routes are relative to the mount (adapterBasePath = /keys)
    // The mount prefix is automatically prepended when matching requests
    override val routes =
        httpRoutes {
            get("/{aliasOrKid}") {
                operationId("getKey")
                produces(MediaType.ApplicationJson)
                handle { req -> handleGetKey(req) }
            }
            get("/") {
                operationId("listKeys")
                produces(MediaType.ApplicationJson)
                handle { req -> handleListKeys(req) }
            }
            post("/") {
                operationId("storeKey")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleStoreKey(req) }
            }
            post("/generate") {
                operationId("generateKey")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleGenerateKey(req) }
            }
            post("/register") {
                operationId("registerKeyReference")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleRegisterKeyReference(req) }
            }
            delete("/{aliasOrKid}") {
                operationId("deleteKey")
                handle { req -> handleDeleteKey(req) }
            }
        }

    /**
     * This @ContributesTo interface is required to make the HttpAdapter accessible
     * as a property in the kotlin-inject SessionGraph.
     *
     * While @ContributesBinding generates provider methods, it doesn't create
     * a property getter for the implementation. The @ContributesTo interface
     * tells kotlin-inject to generate `val kmsHttpAdapter: KmsHttpAdapter` in
     * the graph, which allows the Spring scanner to find and resolve it.
     *
     * This is the standard kotlin-inject-anvil pattern for making @ContributesBinding
     * classes individually accessible (not just as part of a Set multibinding).
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val kmsKeysHttpAdapter: KeysHttpAdapter
    }

    private val json: Json get() = JsonConfig.instance

    private suspend fun handleGetKey(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/keys/{aliasOrKid}")
        val aliasOrKid =
            req.pathParams["aliasOrKid"]
                ?: return errorResponse(400, "Missing path parameter: aliasOrKid")
        val providerId = req.queryParams["providerId"]

        val keyInfo =
            try {
                kmsService.getKey(aliasOrKid, providerId)
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return jsonResponse(200, json.encodeToString(GetKeyResponse(keyInfo = keyInfo.toRest())))
    }

    private suspend fun handleListKeys(request: GenericHttpRequest): GenericHttpResponse {
        val providerId = request.queryParams["providerId"]
        val keyInfos =
            try {
                kmsService.listKeys(providerId).map { it.toRest() }.toTypedArray()
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return jsonResponse(200, json.encodeToString(ListKeysResponse(keyInfos = keyInfos)))
    }

    private suspend fun handleStoreKey(request: GenericHttpRequest): GenericHttpResponse {
        val body =
            request.body
                ?: return errorResponse(400, "Missing request body")

        val storeKeyRequest =
            try {
                json.decodeFromString<StoreKey>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val key =
            try {
                kmsService.storeKey(
                    keyInfo = storeKeyRequest.keyInfo.toSdk(),
                    certChain = storeKeyRequest.certChain,
                )
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return createdResponse(
            "/keys/${key.alias}",
            json.encodeToString(StoreKeyResponse(keyInfo = key.toRest())),
        )
    }

    private suspend fun handleGenerateKey(request: GenericHttpRequest): GenericHttpResponse {
        val body =
            request.body
                ?: return errorResponse(400, "Missing request body")

        val generateRequest =
            try {
                json.decodeFromString<GenerateKeyGlobal>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val keyPair =
            try {
                kmsService.generateKey(
                    alias = generateRequest.alias,
                    use = generateRequest.use?.let { JwkUse.valueOf(it.value) },
                    keyOperations =
                        generateRequest.keyOperations
                            ?.map<KeyOperationsRest, KeyOperations> {
                                KeyOperations.fromJose(JoseKeyOperations.valueOf(it.value.uppercase()))
                            }?.toTypedArray(),
                    alg = generateRequest.alg?.let { SignatureAlgorithm.fromValue(it.value) },
                    providerId = generateRequest.providerId,
                )
            } catch (expected: Exception) {
                return errorResponse(expected)
            }
        return createdResponse(
            "/keys/${keyPair.alias}",
            json.encodeToString(GenerateKeyResponse(keyPair = keyPair.toRest())),
        )
    }

    private suspend fun handleRegisterKeyReference(request: GenericHttpRequest): GenericHttpResponse {
        val body =
            request.body
                ?: return errorResponse(400, "Missing request body")

        val input =
            try {
                json.decodeFromString<RegisterKeyReferenceInput>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val result = registerCommand.execute(input)

        val response =
            result.getOrElse { error ->
                return errorResponse(500, "Failed to register key reference: ${error.message}")
            }

        return createdResponse(
            "/keys/${response.alias}",
            json.encodeToString(response),
        )
    }

    private suspend fun handleDeleteKey(request: GenericHttpRequest): GenericHttpResponse {
        val req = request.withExtractedParams("/keys/{aliasOrKid}")
        val aliasOrKid =
            req.pathParams["aliasOrKid"]
                ?: return errorResponse(400, "Missing path parameter: aliasOrKid")
        val providerId = req.queryParams["providerId"]

        try {
            kmsService.deleteKey(aliasOrKid, providerId)
        } catch (expected: Exception) {
            return errorResponse(expected)
        }
        return noContentResponse()
    }
}

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
import com.sphereon.crypto.kms.rest.api.generated.models.DecryptRequest
import com.sphereon.crypto.kms.rest.api.generated.models.EncryptRequest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementRequest
import com.sphereon.crypto.kms.rest.api.generated.models.UnwrapKeyRequest
import com.sphereon.crypto.kms.rest.api.generated.models.WrapKeyRequest
import com.sphereon.crypto.kms.rest.server.service.EncryptionRestService
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
class EncryptionHttpAdapter(
    private val encryptionService: EncryptionRestService,
) : RoutedHttpAdapter() {
    companion object {
        const val ID = "KMS-ENCRYPTION"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/encryption")

    override val routes =
        httpRoutes {
            post("/encrypt") {
                operationId("encrypt")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleEncrypt(req) }
            }
            post("/decrypt") {
                operationId("decrypt")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleDecrypt(req) }
            }
            post("/wrap") {
                operationId("wrapKey")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleWrapKey(req) }
            }
            post("/unwrap") {
                operationId("unwrapKey")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleUnwrapKey(req) }
            }
            post("/key-agreement") {
                operationId("performKeyAgreement")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleKeyAgreement(req) }
            }
        }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val encryptionHttpAdapter: EncryptionHttpAdapter
    }

    private val json: Json get() = JsonConfig.instance

    private suspend fun handleEncrypt(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<EncryptRequest, com.sphereon.crypto.kms.rest.api.generated.models.EncryptResponse>(request) { encryptionService.encrypt(it) }

    private suspend fun handleDecrypt(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<DecryptRequest, com.sphereon.crypto.kms.rest.api.generated.models.DecryptResponse>(request) { encryptionService.decrypt(it) }

    private suspend fun handleWrapKey(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<WrapKeyRequest, com.sphereon.crypto.kms.rest.api.generated.models.WrapKeyResponse>(request) { encryptionService.wrapKey(it) }

    private suspend fun handleUnwrapKey(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<UnwrapKeyRequest, com.sphereon.crypto.kms.rest.api.generated.models.UnwrapKeyResponse>(request) { encryptionService.unwrapKey(it) }

    private suspend fun handleKeyAgreement(request: GenericHttpRequest): GenericHttpResponse =
        handleBody<KeyAgreementRequest, com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementResponse>(request) { encryptionService.performKeyAgreement(it) }

    private suspend inline fun <reified T, reified O> handleBody(
        request: GenericHttpRequest,
        crossinline handler: suspend (T) -> O,
    ): GenericHttpResponse {
        val body = request.body ?: return errorResponse(400, "Missing request body")
        val input =
            try {
                json.decodeFromString<T>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }
        return try {
            jsonResponse(200, json.encodeToString(handler(input)))
        } catch (expected: Exception) {
            errorResponse(expected)
        }
    }
}

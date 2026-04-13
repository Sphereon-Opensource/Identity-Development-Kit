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
import com.sphereon.core.api.http.errorResponse
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.crypto.kms.rest.api.generated.infrastructure.Base64ByteArray
import com.sphereon.crypto.kms.rest.api.generated.models.CreateRawSignature
import com.sphereon.crypto.kms.rest.api.generated.models.CreateRawSignatureResponse
import com.sphereon.crypto.kms.rest.api.generated.models.VerifyRawSignature
import com.sphereon.crypto.kms.rest.api.generated.models.VerifyRawSignatureResponse
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.server.service.SignatureRestService
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
 * HTTP adapter for KMS Signatures API.
 *
 * Handles all signature-related endpoints:
 * - POST /signatures/raw/create - Create a raw signature
 * - POST /signatures/raw/verify - Verify a raw signature
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class SignaturesHttpAdapter(
    private val signatureService: SignatureRestService,
) : RoutedHttpAdapter() {
    companion object {
        const val ID = "KMS-SIGNATURES"
    }

    override val id: String = ID

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/signatures",
        )

    // Routes are relative to the mount (adapterBasePath = /signatures)
    override val routes =
        httpRoutes {
            post("/raw/create") {
                operationId("createRawSignature")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleCreateRawSignature(req) }
            }
            post("/raw/verify") {
                operationId("verifyRawSignature")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req -> handleVerifyRawSignature(req) }
            }
        }

    /**
     * Contributes this adapter as a property to the SessionGraph.
     * Without this, @ContributesBinding only adds to a multibinding Set that isn't accessible.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val signaturesHttpAdapter: SignaturesHttpAdapter
    }

    private val json: Json get() = JsonConfig.instance

    private suspend fun handleCreateRawSignature(request: GenericHttpRequest): GenericHttpResponse {
        val body =
            request.body
                ?: return errorResponse(400, "Missing request body")

        val createRequest =
            try {
                json.decodeFromString<CreateRawSignature>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val signature =
            signatureService.createRawSignature(
                keyInfo = createRequest.keyInfo.toSdk(),
                input = createRequest.input.value,
            )
        return jsonResponse(
            201,
            json.encodeToString<CreateRawSignatureResponse>(
                CreateRawSignatureResponse(Base64ByteArray(signature)),
            ),
        )
    }

    private suspend fun handleVerifyRawSignature(request: GenericHttpRequest): GenericHttpResponse {
        val body =
            request.body
                ?: return errorResponse(400, "Missing request body")

        val verifyRequest =
            try {
                json.decodeFromString<VerifyRawSignature>(body)
            } catch (expected: Exception) {
                return errorResponse(400, "Invalid request body: ${expected.message}")
            }

        val isValid =
            signatureService.isValidRawSignature(
                keyInfo = verifyRequest.keyInfo.toSdk(),
                input = verifyRequest.input.value,
                signature = verifyRequest.signature.value,
            )
        return jsonResponse(
            200,
            json.encodeToString<VerifyRawSignatureResponse>(
                VerifyRawSignatureResponse(isValid = isValid),
            ),
        )
    }
}

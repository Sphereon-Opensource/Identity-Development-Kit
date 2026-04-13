/*
 * Copyright 2025 Sphereon International B.V.
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
 */

package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.errorResponse
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.universal.CallbackConfig
import com.sphereon.openid.oid4vp.universal.CreateAuthRequestEndpointCommand
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestOutput
import com.sphereon.openid.oid4vp.universal.QrCodeOptions
import com.sphereon.openid.oid4vp.universal.QrCodeService
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpConfigProvider
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.Oid4vpUriScheme
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCallbackConfig
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCreateArgs
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.openid.oid4vp.verifier.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.verifier.store.ClientMetadataConfigurationStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.random.Random

/**
 * Implementation of [CreateAuthRequestEndpointCommand].
 *
 * POST /oid4vp/backend/auth/requests
 *
 * Creates a new OID4VP authorization session using the existing OID4VP verifier
 * service and returns the request URI for wallet initiation along with a QR code.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateAuthRequestEndpointCommand>())
class CreateAuthRequestEndpointCommandImpl(
    execution: SessionExecution,
    private val oid4vpVerifierService: Oid4vpVerifierService,
    private val dcqlConfigStore: DcqlQueryConfigurationStore,
    private val clientMetadataConfigStore: ClientMetadataConfigurationStore,
    private val sessionEventService: SessionEventService,
    private val qrCodeService: QrCodeService,
    private val configProvider: UniversalOid4vpConfigProvider
) : HttpEndpointCommandAdapter(
    id = CreateAuthRequestEndpointCommand.COMMAND_ID,
    execution = execution,
    endpoint = CreateAuthRequestEndpointCommand.ENDPOINT
), CreateAuthRequestEndpointCommand {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // 1. Parse request body
        val input = parseInput(request.body)
            ?: return Ok(errorResponse(400, "Invalid or missing request body"))

        // 2. Validate input - must have either queryId or dcqlQuery
        val inputQueryId = input.queryId
        val inputDcqlQuery = input.dcqlQuery
        val inputClientMetadataId = input.clientMetadataId

        if (inputQueryId == null && inputDcqlQuery == null) {
            return Ok(errorResponse(400, "Either query_id or dcql_query must be provided"))
        }

        // 3. Resolve DCQL query (from config store or inline)
        val dcqlQuery = if (inputQueryId != null) {
            val config = dcqlConfigStore.getByQueryId(inputQueryId).getOrNull()
                ?: return Ok(errorResponse(404, "Query configuration not found: $inputQueryId"))
            if (!config.enabled) {
                return Ok(errorResponse(400, "Query configuration is disabled: $inputQueryId"))
            }
            config.dcqlQuery
        } else {
            inputDcqlQuery!!
        }

        // 4. Resolve client metadata (from config store or use default)
        val clientMetadataConfig = if (inputClientMetadataId != null) {
            clientMetadataConfigStore.getByClientMetadataId(inputClientMetadataId).getOrNull()
                ?: return Ok(errorResponse(404, "Client metadata configuration not found: $inputClientMetadataId"))
        } else {
            // Use first available client metadata config as default
            val allConfigs = clientMetadataConfigStore.getAll().getOrNull()
            allConfigs?.values?.firstOrNull()
        }

        val clientId = input.clientId
            ?: clientMetadataConfig?.clientId
            ?: return Ok(errorResponse(400, "client_id must be provided or configured"))

        // 5. Generate nonce
        val nonce = generateNonce()

        // 6. Determine response URI: client metadata > config > fallback
        val responseUri = clientMetadataConfig?.clientMetadata?.baseMetadata?.redirectUris?.firstOrNull()
            ?: configProvider.getConfig().responseUri
            ?: "$clientId/response"

        // 7. Create authorization request via existing verifier service
        // Resolve client_id_scheme: explicit > detect from client_id prefix > default
        val resolvedScheme = input.clientIdScheme
            ?: ClientIdScheme.fromClientId(clientId)

        val createArgs = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = clientId,
            responseUri = responseUri,
            responseMode = ResponseMode.DIRECT_POST,
            nonce = nonce,
            state = input.state,
            clientMetadata = clientMetadataConfig?.clientMetadata,
            clientIdScheme = resolvedScheme
        )

        val created = oid4vpVerifierService.createAuthorizationRequest(createArgs).getOrElse { error ->
            return Ok(errorResponse(500, "Failed to create authorization request: ${error.message.defaultMessage}"))
        }

        val sessionId = created.sessionId
            ?: return Ok(errorResponse(500, "Session ID not generated"))

        // 8. Update session with callback config if provided
        val inputCallback = input.callback
        if (inputCallback != null) {
            val callbackConfig = AuthorizationSessionCallbackConfig(
                url = inputCallback.url,
                statuses = inputCallback.statuses
            )
            // Update the session with callback config
            oid4vpVerifierService.authorizationSessionStore.getByCorrelationId(sessionId).getOrNull()?.let { session ->
                oid4vpVerifierService.authorizationSessionStore.put(
                    sessionId,
                    session.copy(callback = callbackConfig),
                    input.ttlSeconds ?: AuthorizationSessionStore.DEFAULT_TTL_SECONDS
                )
            }
        }

        // 9. Build authorization request URI
        val requestUri = oid4vpVerifierService.buildAuthorizationRequestUri(
            BuildAuthorizationRequestUriArgs(
                request = created.request,
                scheme = Oid4vpUriScheme.OPENID4VP,
                useRequestUri = true,
                requestUri = buildRequestUri(sessionId, input.requestUriBase)
            )
        ).getOrElse { error ->
            return Ok(errorResponse(500, "Failed to build request URI: ${error.message.defaultMessage}"))
        }.value

        // 10. Generate QR code as data URI (only if qr_code options provided)
        val qrUri = input.qrCodeOptions?.let { qrOptions ->
            qrCodeService.generateDataUri(requestUri, qrOptions)
        }

        // 11. Emit SESSION_CREATED event
        emitSessionCreatedEvent(sessionId, input.queryId, requestUri)

        // 12. Build response per Universal OID4VP spec
        val output = CreateAuthorizationRequestOutput(
            correlationId = sessionId,
            queryId = input.queryId,
            requestUri = requestUri,
            statusUri = "/oid4vp/backend/auth/requests/$sessionId",
            qrUri = qrUri
        )

        return Ok(
            GenericHttpResponse(
                statusCode = 201,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Cache-Control" to "no-store"
                ),
                body = json.encodeToString(CreateAuthorizationRequestOutput.serializer(), output)
            )
        )
    }

    private fun parseInput(body: String?): CreateAuthorizationRequestInput? {
        if (body.isNullOrBlank()) return null
        return try {
            json.decodeFromString(CreateAuthorizationRequestInput.serializer(), body)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        Random.nextBytes(bytes)
        return bytes.joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }

    private fun buildRequestUri(correlationId: String, inputRequestUriBase: String? = null): String {
        val base = (inputRequestUriBase ?: configProvider.getConfig().externalBaseUrl)?.trimEnd('/')
        return if (base != null) {
            "$base/oid4vp/request-uri/$correlationId"
        } else {
            "/oid4vp/request-uri/$correlationId"
        }
    }

    private suspend fun emitSessionCreatedEvent(
        correlationId: String,
        queryId: String?,
        requestUri: String
    ) {
        try {
            sessionEventService.emit(
                sessionEventService.eventBuilder()
                    .type(UniversalOid4vpEventTypes.SESSION_CREATED)
                    .origin(CreateAuthRequestEndpointCommand.COMMAND_ID)
                    .payload(buildJsonObject {
                        put("correlationId", correlationId)
                        queryId?.let { put("queryId", it) }
                        put("requestUri", requestUri)
                    })
                    .build()
            )
        } catch (e: Exception) {
            // Best effort - don't fail the request if event emission fails
            log.warn("Failed to emit SESSION_CREATED event: ${e.message}")
        }
    }
}

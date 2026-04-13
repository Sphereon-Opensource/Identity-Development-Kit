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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.universal.CreateAuthRequestServiceCommand
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
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.openid.oid4vp.verifier.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.verifier.store.ClientMetadataConfigurationStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random

/**
 * Typed service command implementation for creating authorization requests.
 *
 * POST /oid4vp/backend/auth/requests
 *
 * This implementation uses the Binary API v5 pattern with:
 * - Typed input: [CreateAuthorizationRequestInput] (JSON body)
 * - Typed output: [CreateAuthorizationRequestOutput]
 * - HTTP binding via [PublicApiCommand] on [CreateAuthRequestServiceCommand]
 *
 * The input is automatically deserialized from the request body by the BinaryCommandAdapter.
 * The output is automatically serialized to JSON.
 */
@Inject
@SingleIn(SessionScope::class)
class CreateAuthRequestServiceCommandImpl(
    execution: SessionExecution,
    private val oid4vpVerifierService: Oid4vpVerifierService,
    private val dcqlConfigStore: DcqlQueryConfigurationStore,
    private val clientMetadataConfigStore: ClientMetadataConfigurationStore,
    private val sessionEventService: SessionEventService,
    private val qrCodeService: QrCodeService,
    private val configProvider: UniversalOid4vpConfigProvider
) : TypedServiceCommandAdapter<CreateAuthorizationRequestInput, CreateAuthorizationRequestOutput>(
    commandId = CreateAuthRequestServiceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateAuthorizationRequestInput>(),
    outputTypeToken = typeToken<CreateAuthorizationRequestOutput>()
), CreateAuthRequestServiceCommand {

    override val commandId: String get() = CreateAuthRequestServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateAuthorizationRequestInput,
        applyDuring: (CreateAuthorizationRequestInput) -> CreateAuthorizationRequestInput
    ): IdkResult<CreateAuthorizationRequestOutput, IdkError> {
        val input = applyDuring(args)

        // 1. Validate input - must have either queryId or dcqlQuery
        val inputQueryId = input.queryId
        val inputDcqlQuery = input.dcqlQuery
        val inputClientMetadataId = input.clientMetadataId

        if (inputQueryId == null && inputDcqlQuery == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Either query_id or dcql_query must be provided"
            ))
        }

        // 2. Resolve DCQL query (from config store or inline)
        val dcqlQuery = if (inputQueryId != null) {
            val config = dcqlConfigStore.getByQueryId(inputQueryId).getOrNull()
                ?: return Err(IdkError.NOT_FOUND_ERROR(
                    message = "Query configuration not found: $inputQueryId"
                ))
            if (!config.enabled) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Query configuration is disabled: $inputQueryId"
                ))
            }
            config.dcqlQuery
        } else {
            inputDcqlQuery!!
        }

        // 3. Resolve client metadata (from config store or use default)
        val clientMetadataConfig = if (inputClientMetadataId != null) {
            clientMetadataConfigStore.getByClientMetadataId(inputClientMetadataId).getOrNull()
                ?: return Err(IdkError.NOT_FOUND_ERROR(
                    message = "Client metadata configuration not found: $inputClientMetadataId"
                ))
        } else {
            // Use first available client metadata config as default
            val allConfigs = clientMetadataConfigStore.getAll().getOrNull()
            allConfigs?.values?.firstOrNull()
        }

        val clientId = input.clientId
            ?: clientMetadataConfig?.clientId
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "client_id must be provided or configured"
            ))

        // 4. Generate nonce
        val nonce = generateNonce()

        // 5. Determine response URI (must be configured)
        val responseUri = clientMetadataConfig?.clientMetadata?.baseMetadata?.redirectUris?.firstOrNull()
            ?: "$clientId/response"

        // 6. Create authorization request via existing verifier service
        val createArgs = CreateAuthorizationRequestArgs(
            dcqlQuery = dcqlQuery,
            clientId = clientId,
            responseUri = responseUri,
            responseMode = ResponseMode.DIRECT_POST,
            nonce = nonce,
            state = input.state,
            clientMetadata = clientMetadataConfig?.clientMetadata
        )

        val created = oid4vpVerifierService.createAuthorizationRequest(createArgs).getOrElse { error ->
            return Err(IdkError.UNKNOWN_ERROR(
                message = "Failed to create authorization request: ${error.message.defaultMessage}"
            ))
        }

        val sessionId = created.sessionId
            ?: return Err(IdkError.UNKNOWN_ERROR(message = "Session ID not generated"))

        // 7. Update session with callback config if provided
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

        // 8. Build authorization request URI
        val requestUri = oid4vpVerifierService.buildAuthorizationRequestUri(
            BuildAuthorizationRequestUriArgs(
                request = created.request,
                scheme = Oid4vpUriScheme.OPENID4VP,
                useRequestUri = true,
                requestUri = buildRequestUri(sessionId, input.requestUriBase)
            )
        ).getOrElse { error ->
            return Err(IdkError.UNKNOWN_ERROR(
                message = "Failed to build request URI: ${error.message.defaultMessage}"
            ))
        }.value

        // 9. Generate QR code as data URI (only if qr_code options provided)
        val qrUri = input.qrCodeOptions?.let { qrOptions ->
            qrCodeService.generateDataUri(requestUri, qrOptions)
        }

        // 10. Emit SESSION_CREATED event
        emitSessionCreatedEvent(sessionId, input.queryId, requestUri)

        // 11. Build response per Universal OID4VP spec
        val output = CreateAuthorizationRequestOutput(
            correlationId = sessionId,
            queryId = input.queryId,
            requestUri = requestUri,
            statusUri = "/oid4vp/backend/auth/requests/$sessionId",
            qrUri = qrUri
        )

        return Ok(output)
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        Random.nextBytes(bytes)
        return bytes.joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }

    /**
     * Build the request URI for an OID4VP authorization request.
     *
     * Resolution order for base URL:
     * 1. [inputRequestUriBase] from the API call (caller override)
     * 2. [UniversalOid4vpConfig.externalBaseUrl] from configuration
     * 3. Falls back to a relative path if neither is set
     */
    private fun buildRequestUri(correlationId: String, inputRequestUriBase: String?): String {
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
                    .origin(CreateAuthRequestServiceCommand.COMMAND_ID)
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

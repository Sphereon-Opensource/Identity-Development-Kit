/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.encodeUrlGraph
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.wallet.interaction.WalletClaimDescriptor
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletDisclosureSummary
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletNestedPresentationChallenge
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutionResult
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutor
import com.sphereon.wallet.interaction.WalletNestedPresentationRequest
import com.sphereon.wallet.interaction.WalletNestedPresentationResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class Oid4vpNestedPresentationExecutor(
    private val holder: Oid4vpHolderService,
    private val selectedCredentialResolver: Oid4vpSelectedCredentialResolver,
    private val walletConfigProvider: Oid4vpWalletConfigProvider = Oid4vpWalletConfigProvider.none,
    private val sdJwtHolderBindingProvider: Oid4vpSdJwtHolderBindingProvider = Oid4vpSdJwtHolderBindingProvider.passthrough,
) : WalletNestedPresentationExecutor {
    override suspend fun preparePresentation(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        request: WalletNestedPresentationRequest,
    ): WalletNestedPresentationExecutionResult<WalletNestedPresentationChallenge> {
        context.storeNestedRequest(request)
        return when (val resolved = resolveNestedRequest(context, state, request)) {
            is WalletNestedPresentationExecutionResult.Failed -> {
                resolved
            }

            is WalletNestedPresentationExecutionResult.Success -> {
                val verifier = resolved.value.toCounterparty()
                val selection = resolved.value.dcqlQuery?.toCredentialSelectionRequest(emptyMap())
                val requestedClaims =
                    selection
                        ?.requirements
                        .orEmpty()
                        .flatMap { requirement -> requirement.requiredClaimPaths.map { WalletClaimDescriptor(path = it) } }
                WalletNestedPresentationExecutionResult.Success(
                    WalletNestedPresentationChallenge(
                        credentialSelection = selection,
                        disclosure = WalletDisclosureSummary(verifier = verifier, requestedClaims = requestedClaims),
                    ),
                )
            }
        }
    }

    override suspend fun createPresentationResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletNestedPresentationExecutionResult<WalletNestedPresentationResponse> {
        val request =
            context.loadNestedRequest()
                ?: return failed("oid4vp.nested_request_missing", "wallet.interaction.error.oid4vp_nested_request_missing", retryable = true)
        val resolved =
            when (val resolved = resolveNestedRequest(context, state, request)) {
                is WalletNestedPresentationExecutionResult.Failed -> return resolved
                is WalletNestedPresentationExecutionResult.Success -> resolved.value
            }
        val selectedCredentials =
            try {
                selectedCredentialResolver.resolveSelectedCredentials(
                    context = context,
                    state = state,
                    resolvedRequest = resolved,
                    selectedCredentialIdsByRequirement = selectedIdsByRequirement(action, state),
                )
            } catch (_: Exception) {
                return failed(
                    code = "oid4vp.credential_resolution_failed",
                    messageKey = "wallet.interaction.error.oid4vp_credential_resolution_failed",
                    retryable = true,
                )
            }
        val boundCredentials =
            sdJwtHolderBindingProvider
                .applyHolderBinding(
                    Oid4vpSdJwtHolderBindingRequest(
                        walletUnitId = context.walletUnitId,
                        operationBinding =
                            state.adapterId?.let { namespace ->
                                context.privateSessionStore
                                    .get(context.sessionId, namespace)
                                    ?.values
                                    ?.get("security_operation_binding")
                            },
                        request = resolved,
                        selectedCredentials = selectedCredentials,
                    ),
                ).getOrElse {
                    return failed(
                        code = "oid4vp.response_creation_failed",
                        messageKey = "wallet.interaction.error.oid4vp_response_creation_failed",
                        arguments = mapOf("providerErrorCode" to it.code),
                    )
                }

        val response = holder.createAuthorizationResponse(resolved, boundCredentials)
        if (response.isErr) {
            return failed(
                code = "oid4vp.response_creation_failed",
                messageKey = "wallet.interaction.error.oid4vp_response_creation_failed",
                arguments = mapOf("providerErrorCode" to response.error.code),
            )
        }
        return WalletNestedPresentationExecutionResult.Success(
            WalletNestedPresentationResponse(response.value.toIaeResponseJson()),
        )
    }

    private suspend fun resolveNestedRequest(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        request: WalletNestedPresentationRequest,
    ): WalletNestedPresentationExecutionResult<ResolvedOid4vpRequest> {
        val requestObject = request.requestObject
        val parsed =
            if (requestObject != null) {
                try {
                    WalletNestedPresentationExecutionResult.Success(
                        Oid4vpWalletInteractionProtocolAdapter.json.decodeFromJsonElement(
                            AuthorizationRequest.serializer(),
                            requestObject,
                        ),
                    )
                } catch (_: Exception) {
                    failed("oid4vp.nested_request_parse_failed", "wallet.interaction.error.oid4vp_request_parse_failed")
                }
            } else {
                val requestUri =
                    request.requestUri
                        ?: return failed("oid4vp.nested_request_missing", "wallet.interaction.error.oid4vp_nested_request_missing", retryable = true)
                val result =
                    holder.parseAuthorizationRequest(
                        requestUri = "openid4vp://?request_uri=${requestUri.encodeUrlGraph()}",
                        walletConfig = walletConfigProvider.walletConfig(context, state),
                    )
                if (result.isErr) {
                    failed(
                        code = "oid4vp.request_parse_failed",
                        messageKey = "wallet.interaction.error.oid4vp_request_parse_failed",
                        arguments = mapOf("providerErrorCode" to result.error.code),
                    )
                } else {
                    WalletNestedPresentationExecutionResult.Success(result.value)
                }
            }

        val authorizationRequest =
            when (parsed) {
                is WalletNestedPresentationExecutionResult.Failed -> return parsed
                is WalletNestedPresentationExecutionResult.Success -> parsed.value
            }
        val resolved = holder.resolveAuthorizationRequest(authorizationRequest)
        if (resolved.isErr) {
            return failed(
                code = "oid4vp.request_resolve_failed",
                messageKey = "wallet.interaction.error.oid4vp_request_resolve_failed",
                arguments = mapOf("providerErrorCode" to resolved.error.code),
            )
        }
        return WalletNestedPresentationExecutionResult.Success(resolved.value)
    }

    private suspend fun WalletInteractionContext.storeNestedRequest(request: WalletNestedPresentationRequest) {
        val existing =
            privateSessionStore
                .get(sessionId, Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID)
                ?.values
                .orEmpty()
        val values =
            buildMap {
                request.requestObject?.let {
                    put("iae_request_object", Oid4vpWalletInteractionProtocolAdapter.json.encodeToString(JsonObject.serializer(), it))
                }
                request.requestUri?.let { put("iae_request_uri", it) }
            }
        privateSessionStore.put(
            sessionId,
            WalletInteractionPrivateSessionData(
                namespace = Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID,
                values = existing + values,
            ),
        )
    }

    private suspend fun WalletInteractionContext.loadNestedRequest(): WalletNestedPresentationRequest? {
        val values =
            privateSessionStore
                .get(sessionId, Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID)
                ?.values
                .orEmpty()
        val requestObject =
            values["iae_request_object"]?.let {
                Oid4vpWalletInteractionProtocolAdapter.json.decodeFromString(JsonObject.serializer(), it)
            }
        val requestUri = values["iae_request_uri"]
        return if (requestObject != null || !requestUri.isNullOrBlank()) {
            WalletNestedPresentationRequest(
                protocol = com.sphereon.wallet.interaction.WalletProtocol.OID4VP,
                requestObject = requestObject,
                requestUri = requestUri,
            )
        } else {
            null
        }
    }

    private fun selectedIdsByRequirement(
        action: WalletInteractionAction,
        state: WalletInteractionState,
    ): Map<String, List<String>> {
        action.selection
            ?.selectedCredentialIdsByRequirement
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val selectedIds = state.disclosure?.selectedCredentialIds.orEmpty()
        val requirements = state.credentialSelection?.requirements.orEmpty()
        return if (selectedIds.isNotEmpty() && requirements.size == 1) {
            mapOf(requirements.single().id to selectedIds)
        } else {
            emptyMap()
        }
    }

    private fun ResolvedOid4vpRequest.toCounterparty(): WalletCounterpartySummary =
        WalletCounterpartySummary(
            role = WalletCounterpartyRole.VERIFIER,
            identifier = verifierInfo.clientId,
            displayName = verifierInfo.displayName ?: verifierInfo.clientId,
            logoUri = verifierInfo.logoUri,
            metadata = mapOf("client_id_scheme" to verifierInfo.clientIdScheme.name),
        )

    private fun AuthorizationResponse.toIaeResponseJson(): JsonObject =
        buildJsonObject {
            state?.let { put("state", JsonPrimitive(it)) }
            additionalParameters.forEach { (key, value) -> put(key, value) }
        }

    private fun failed(
        code: String,
        messageKey: String,
        retryable: Boolean = false,
        arguments: Map<String, String> = emptyMap(),
    ): WalletNestedPresentationExecutionResult.Failed =
        WalletNestedPresentationExecutionResult.Failed(
            code = code,
            messageKey = messageKey,
            retryable = retryable,
            arguments = arguments,
        )
}

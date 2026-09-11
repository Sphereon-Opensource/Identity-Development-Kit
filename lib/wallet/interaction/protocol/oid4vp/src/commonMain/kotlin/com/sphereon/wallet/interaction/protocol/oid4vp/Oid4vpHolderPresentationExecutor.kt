/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.holder.JarmOptions
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.oid4vp.holder.WalletConfig
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFailureCodes
import com.sphereon.wallet.interaction.WalletInteractionState
import kotlinx.serialization.decodeFromString

interface Oid4vpSelectedCredentialResolver {
    suspend fun resolveSelectedCredentials(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedRequest: ResolvedOid4vpRequest,
        selectedCredentialIdsByRequirement: Map<String, List<String>>,
    ): List<SelectedCredential>

    suspend fun recordPresentationSubmitted(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedRequest: ResolvedOid4vpRequest,
        selectedCredentialIdsByRequirement: Map<String, List<String>>,
        selectedCredentials: List<SelectedCredential>,
    ) {
    }
}

interface Oid4vpWalletConfigProvider {
    suspend fun walletConfig(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): WalletConfig?

    companion object {
        val none: Oid4vpWalletConfigProvider =
            object : Oid4vpWalletConfigProvider {
                override suspend fun walletConfig(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                ): WalletConfig? = null
            }
    }
}

interface Oid4vpJarmOptionsProvider {
    suspend fun jarmOptions(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedRequest: ResolvedOid4vpRequest,
    ): JarmOptions?

    companion object {
        val none: Oid4vpJarmOptionsProvider =
            object : Oid4vpJarmOptionsProvider {
                override suspend fun jarmOptions(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                    resolvedRequest: ResolvedOid4vpRequest,
                ): JarmOptions? = null
            }

        /**
         * Default holder-side JARM options enabling encrypted `direct_post.jwt` responses (OpenID4VP
         * 1.0 section 8.3: unsigned encrypted JWTs). The JWE configuration is derived downstream from
         * the verifier's client_metadata; the wallet unit id becomes the JARM payload issuer. No
         * signing key is provided, so a verifier demanding signed JARM fails with the explicit
         * signing-key error instead of silently degrading.
         */
        val walletUnitIssuer: Oid4vpJarmOptionsProvider =
            object : Oid4vpJarmOptionsProvider {
                override suspend fun jarmOptions(
                    context: WalletInteractionContext,
                    state: WalletInteractionState,
                    resolvedRequest: ResolvedOid4vpRequest,
                ): JarmOptions = JarmOptions(issuer = context.walletUnitId)
            }
    }
}

class Oid4vpHolderPresentationExecutor(
    private val holder: Oid4vpHolderService,
    private val selectedCredentialResolver: Oid4vpSelectedCredentialResolver,
    private val sdJwtHolderBindingProvider: Oid4vpSdJwtHolderBindingProvider,
    private val dataIntegrityHolderBindingProvider: Oid4vpDataIntegrityHolderBindingProvider = Oid4vpDataIntegrityHolderBindingProvider.none,
    private val walletConfigProvider: Oid4vpWalletConfigProvider = Oid4vpWalletConfigProvider.none,
    private val jarmOptionsProvider: Oid4vpJarmOptionsProvider = Oid4vpJarmOptionsProvider.none,
    private val responseMode: ResponseMode? = null,
) : Oid4vpPresentationExecutor {
    override suspend fun submitPresentation(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vpPresentationExecutionResult {
        val privateValues =
            context.privateSessionStore
                .get(context.sessionId, Oid4vpWalletInteractionProtocolAdapter.ADAPTER_ID)
                ?.values
                .orEmpty()
        val rawRequest =
            privateValues["entry_point.raw"]
                ?: return failed(
                    code = WalletInteractionFailureCodes.OID4VP_AUTHORIZATION_REQUEST_MISSING,
                    messageKey = "wallet.interaction.error.oid4vp_authorization_request_missing",
                    retryable = true,
                )

        val request =
            cachedAuthorizationRequest(privateValues)
                ?: run {
                    val parsed = holder.parseAuthorizationRequest(rawRequest, walletConfigProvider.walletConfig(context, state))
                    if (parsed.isErr) {
                        return failed(WalletInteractionFailureCodes.OID4VP_REQUEST_PARSE_FAILED, "wallet.interaction.error.oid4vp_request_parse_failed", parsed.error)
                    }
                    parsed.value
                }

        val resolved = holder.resolveAuthorizationRequest(request)
        if (resolved.isErr) {
            return failed(WalletInteractionFailureCodes.OID4VP_REQUEST_RESOLVE_FAILED, "wallet.interaction.error.oid4vp_request_resolve_failed", resolved.error)
        }

        val selectedCredentials =
            try {
                selectedCredentialResolver.resolveSelectedCredentials(
                    context = context,
                    state = state,
                    resolvedRequest = resolved.value,
                    selectedCredentialIdsByRequirement = selectedIdsByRequirement(privateValues, state),
                )
            } catch (_: Exception) {
                return failed(
                    code = WalletInteractionFailureCodes.OID4VP_CREDENTIAL_RESOLUTION_FAILED,
                    messageKey = "wallet.interaction.error.oid4vp_credential_resolution_failed",
                    retryable = true,
                )
            }

        val operationBinding =
            state.adapterId?.let { namespace ->
                context.privateSessionStore
                    .get(context.sessionId, namespace)
                    ?.values
                    ?.get(Oid4vpWalletInteractionProtocolAdapter.SECURITY_OPERATION_BINDING_PRIVATE_KEY)
            }
        val selectedCredentialsWithBinding =
            selectedCredentials.map {
                it.copy(
                    holderJwtVpOperationBinding = operationBinding,
                    holderJwtVpWalletUnitId = context.walletUnitId,
                )
            }
        val dataIntegrityBinding =
            dataIntegrityHolderBindingProvider
                .applyHolderBinding(
                    Oid4vpDataIntegrityHolderBindingRequest(
                        walletUnitId = context.walletUnitId,
                        operationBinding = operationBinding,
                        request = resolved.value,
                        selectedCredentials = selectedCredentialsWithBinding,
                    ),
                ).getOrElse {
                    return failed(WalletInteractionFailureCodes.OID4VP_RESPONSE_CREATION_FAILED, "wallet.interaction.error.oid4vp_response_creation_failed", it)
                }
        val boundCredentials =
            sdJwtHolderBindingProvider
                .applyHolderBinding(
                    Oid4vpSdJwtHolderBindingRequest(
                        walletUnitId = context.walletUnitId,
                        operationBinding = operationBinding,
                        request = resolved.value,
                        selectedCredentials = dataIntegrityBinding.selectedCredentials,
                    ),
                ).getOrElse {
                    return failed(WalletInteractionFailureCodes.OID4VP_RESPONSE_CREATION_FAILED, "wallet.interaction.error.oid4vp_response_creation_failed", it)
                }

        val response = holder.createAuthorizationResponse(
            resolved.value,
            boundCredentials,
            dataIntegrityBinding.preparedPresentations,
        )
        if (response.isErr) {
            return failed(WalletInteractionFailureCodes.OID4VP_RESPONSE_CREATION_FAILED, "wallet.interaction.error.oid4vp_response_creation_failed", response.error)
        }

        val submission =
            holder.submitAuthorizationResponse(
                resolvedRequest = resolved.value,
                response = response.value,
                responseMode = responseMode,
                jarmOptions = jarmOptionsProvider.jarmOptions(context, state, resolved.value),
            )
        if (submission.isErr) {
            return failed(WalletInteractionFailureCodes.OID4VP_RESPONSE_SUBMISSION_FAILED, "wallet.interaction.error.oid4vp_response_submission_failed", submission.error)
        }

        suspend fun recordPresentationSubmitted(): Oid4vpPresentationExecutionResult.Failed? =
            try {
                selectedCredentialResolver.recordPresentationSubmitted(
                    context = context,
                    state = state,
                    resolvedRequest = resolved.value,
                    selectedCredentialIdsByRequirement = selectedIdsByRequirement(privateValues, state),
                    selectedCredentials = selectedCredentials,
                )
                null
            } catch (_: Exception) {
                failed(
                    code = WalletInteractionFailureCodes.OID4VP_PRESENTATION_HISTORY_UPDATE_FAILED,
                    messageKey = "wallet.interaction.error.oid4vp_presentation_history_update_failed",
                    retryable = false,
                )
            }

        return when (val result = submission.value) {
            is SubmissionResult.Success -> {
                recordPresentationSubmitted()
                    ?: (
                        result.redirectUri?.let { Oid4vpPresentationExecutionResult.RedirectRequired(it) }
                            ?: Oid4vpPresentationExecutionResult.Submitted()
                    )
            }

            is SubmissionResult.Redirect -> {
                recordPresentationSubmitted()
                    ?: Oid4vpPresentationExecutionResult.RedirectRequired(result.redirectUri)
            }

            is SubmissionResult.DigitalCredential -> {
                recordPresentationSubmitted()
                    ?: Oid4vpPresentationExecutionResult.DigitalCredentialResponse(result.data)
            }

            is SubmissionResult.Error -> {
                Oid4vpPresentationExecutionResult.Failed(
                    code = WalletInteractionFailureCodes.OID4VP_VERIFIER_ERROR,
                    messageKey = "wallet.interaction.error.oid4vp_verifier_error",
                    arguments = mapOf("protocolError" to result.error),
                )
            }
        }
    }

    private fun selectedIdsByRequirement(
        privateValues: Map<String, String>,
        state: WalletInteractionState,
    ): Map<String, List<String>> {
        privateValues["selected_credential_ids_by_requirement"]?.let { serialized ->
            return Oid4vpWalletInteractionProtocolAdapter.json.decodeFromString(serialized)
        }
        val selectedIds = state.disclosure?.selectedCredentialIds.orEmpty()
        val requirements = state.credentialSelection?.requirements.orEmpty()
        return if (selectedIds.isNotEmpty() && requirements.size == 1) {
            mapOf(requirements.single().id to selectedIds)
        } else {
            emptyMap()
        }
    }

    private fun cachedAuthorizationRequest(privateValues: Map<String, String>): AuthorizationRequest? =
        privateValues["authorization_request"]?.let { serialized ->
            runCatching {
                Oid4vpWalletInteractionProtocolAdapter.json.decodeFromString(AuthorizationRequest.serializer(), serialized)
            }.getOrNull()
        }

    private fun failed(
        code: String,
        messageKey: String,
        error: IdkError,
        retryable: Boolean = false,
    ): Oid4vpPresentationExecutionResult.Failed =
        failed(
            code = code,
            messageKey = messageKey,
            retryable = retryable,
            arguments =
                buildMap {
                    put("providerErrorCode", error.code)
                    error.message.defaultMessage
                        .takeIf { it.isNotBlank() }
                        ?.let { put("providerErrorMessage", it) }
                },
        )

    private fun failed(
        code: String,
        messageKey: String,
        retryable: Boolean = false,
        arguments: Map<String, String> = emptyMap(),
    ): Oid4vpPresentationExecutionResult.Failed =
        Oid4vpPresentationExecutionResult.Failed(
            code = code,
            messageKey = messageKey,
            retryable = retryable,
            arguments = arguments,
        )
}

/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutionResult
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutor
import com.sphereon.wallet.interaction.WalletNestedPresentationRequest
import com.sphereon.wallet.interaction.WalletProtocol
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class Oid4vciHolderIssuanceOptions(
    val signingKeyId: String,
    val signingAlgorithm: String = "ES256",
    val clientId: String? = null,
    val redirectUri: String? = null,
    val credentialConfigurationId: String? = null,
    val usePar: Boolean = false,
    val iaeCodeVerifier: String? = null,
    val iaeCodeChallenge: String? = null,
    val iaeCodeChallengeMethod: String? = null,
)

interface Oid4vciIssuanceOptionsProvider {
    suspend fun options(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
    ): Oid4vciHolderIssuanceOptions
}

interface Oid4vciCredentialResponseReceiver {
    suspend fun receiveCredentialResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        credentialResponse: CredentialResponse,
    ): List<WalletCredentialPreview>
}

class Oid4vciHolderIssuanceExecutor(
    private val holder: Oid4vciHolderService,
    private val optionsProvider: Oid4vciIssuanceOptionsProvider,
    private val credentialReceiver: Oid4vciCredentialResponseReceiver,
    private val nestedPresentationExecutor: WalletNestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
) : Oid4vciIssuanceExecutor {
    override suspend fun requestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        action: WalletInteractionAction?,
    ): Oid4vciIssuanceExecutionResult {
        val privateValues = context.privateValues()
        if (state.status == WalletInteractionStatus.DeferredRetrievalPending) {
            return requestDeferredCredential(context, state, privateValues)
        }
        if (privateValues["iae_pending"] == "true") {
            return continueInteractiveAuthorization(context, state, privateValues, action)
        }

        val rawOffer =
            privateValues["entry_point.raw"]
                ?: return failed(
                    code = "oid4vci.credential_offer_missing",
                    messageKey = "wallet.interaction.error.oid4vci_credential_offer_missing",
                    retryable = true,
                )

        val offer = holder.parseCredentialOffer(rawOffer)
        if (offer.isErr) {
            return failed("oid4vci.offer_parse_failed", "wallet.interaction.error.oid4vci_offer_parse_failed", offer.error)
        }
        val resolved = holder.resolveCredentialOffer(offer.value)
        if (resolved.isErr) {
            return failed("oid4vci.offer_resolve_failed", "wallet.interaction.error.oid4vci_offer_resolve_failed", resolved.error)
        }

        val options =
            try {
                optionsProvider.options(context, state, resolved.value)
            } catch (_: Exception) {
                return failed(
                    code = "oid4vci.options_unavailable",
                    messageKey = "wallet.interaction.error.oid4vci_options_unavailable",
                    retryable = true,
                )
            }

        val preAuthorizedGrant =
            resolved.value.offer.grants
                ?.preAuthorizedCode
        if (preAuthorizedGrant == null) {
            privateValues["authorization_callback"]?.takeIf { it.isNotBlank() }?.let { callback ->
                return exchangeAuthorizationCodeAndRequestCredential(context, state, resolved.value, options, callback)
            }
            return buildAuthorizationRequest(context, state, resolved.value, options)
        }

        val authorizationServer = holder.selectAuthorizationServer(resolved.value.issuerMetadata, preAuthorizedGrant.authorizationServer)
        if (authorizationServer.isErr) {
            return failed("oid4vci.authorization_server_resolve_failed", "wallet.interaction.error.oid4vci_authorization_server_resolve_failed", authorizationServer.error)
        }
        val tokenEndpoint =
            authorizationServer.value.tokenEndpoint
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing")

        val token =
            holder.exchangePreAuthorizedCode(
                tokenEndpoint = tokenEndpoint,
                preAuthorizedCode = preAuthorizedGrant.preAuthorizedCode,
                txCode = privateValues["tx_code"],
                clientId = options.clientId,
                redirectUri = options.redirectUri,
            )
        if (token.isErr) {
            return failed("oid4vci.token_exchange_failed", "wallet.interaction.error.oid4vci_token_exchange_failed", token.error)
        }
        context.storePrivate(
            mapOf(
                "access_token" to token.value.accessToken,
                "token_endpoint" to tokenEndpoint,
            ),
        )

        val cNonce = resolveProofNonce(resolved.value, token.value.cNonce)
        if (cNonce.isErr) {
            return failed("oid4vci.nonce_request_failed", "wallet.interaction.error.oid4vci_nonce_request_failed", cNonce.error)
        }
        val proof =
            holder.createCredentialRequestProof(
                issuerUrl = resolved.value.offer.credentialIssuer,
                cNonce = cNonce.value,
                signingKeyId = options.signingKeyId,
                signingAlgorithm = options.signingAlgorithm,
                clientId = options.clientId,
            )
        if (proof.isErr) {
            return failed("oid4vci.proof_creation_failed", "wallet.interaction.error.oid4vci_proof_creation_failed", proof.error)
        }

        val credentialConfigurationId =
            options.credentialConfigurationId
                ?: state.credentialOffer?.credentialConfigurationIds?.firstOrNull()
                ?: resolved.value.offer.credentialConfigurationIds
                    .firstOrNull()
        context.storePrivate(
            mapOf(
                "holder_key_alias" to options.signingKeyId,
                "credential_configuration_id" to credentialConfigurationId.orEmpty(),
            ),
        )
        val credential =
            holder.requestCredential(
                credentialEndpoint = resolved.value.issuerMetadata.credentialEndpoint,
                accessToken = token.value.accessToken,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proof.value.proofs,
            )
        if (credential.isErr) {
            return failed("oid4vci.credential_request_failed", "wallet.interaction.error.oid4vci_credential_request_failed", credential.error)
        }

        return handleCredentialResponse(context, state, resolved.value, credential.value)
    }

    private suspend fun requestDeferredCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        privateValues: Map<String, String>,
    ): Oid4vciIssuanceExecutionResult {
        val deferredEndpoint =
            privateValues["deferred_credential_endpoint"]
                ?: return failed("oid4vci.deferred_endpoint_missing", "wallet.interaction.error.oid4vci_deferred_endpoint_missing", retryable = true)
        val accessToken =
            privateValues["access_token"]
                ?: return failed("oid4vci.access_token_missing", "wallet.interaction.error.oid4vci_access_token_missing", retryable = true)
        val transactionId =
            privateValues["deferred_transaction_id"]
                ?: return failed("oid4vci.deferred_transaction_missing", "wallet.interaction.error.oid4vci_deferred_transaction_missing", retryable = true)
        val credential =
            holder.requestDeferredCredential(
                deferredCredentialEndpoint = deferredEndpoint,
                accessToken = accessToken,
                transactionId = transactionId,
            )
        if (credential.isErr) {
            return failed("oid4vci.deferred_request_failed", "wallet.interaction.error.oid4vci_deferred_request_failed", credential.error, retryable = true)
        }

        val rawOffer =
            privateValues["entry_point.raw"]
                ?: return failed("oid4vci.credential_offer_missing", "wallet.interaction.error.oid4vci_credential_offer_missing", retryable = true)
        val offer = holder.parseCredentialOffer(rawOffer)
        if (offer.isErr) {
            return failed("oid4vci.offer_parse_failed", "wallet.interaction.error.oid4vci_offer_parse_failed", offer.error)
        }
        val resolved = holder.resolveCredentialOffer(offer.value)
        if (resolved.isErr) {
            return failed("oid4vci.offer_resolve_failed", "wallet.interaction.error.oid4vci_offer_resolve_failed", resolved.error)
        }
        return handleCredentialResponse(context, state, resolved.value, credential.value)
    }

    override suspend fun notifyCredentialAccepted(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vciIssuerNotificationResult = notifyIssuer(context, CredentialNotificationEvent.CREDENTIAL_ACCEPTED)

    override suspend fun notifyCredentialDeclined(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vciIssuerNotificationResult = notifyIssuer(context, CredentialNotificationEvent.CREDENTIAL_FAILURE, eventDescription = "credential_declined")

    private suspend fun buildAuthorizationRequest(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
    ): Oid4vciIssuanceExecutionResult {
        val authorizationGrant =
            resolvedOffer.offer.grants?.authorizationCode
                ?: return failed("oid4vci.unsupported_grant", "wallet.interaction.error.oid4vci_unsupported_grant")
        val clientId = options.clientId ?: return failed("oid4vci.client_id_missing", "wallet.interaction.error.oid4vci_client_id_missing")
        val redirectUri = options.redirectUri ?: return failed("oid4vci.redirect_uri_missing", "wallet.interaction.error.oid4vci_redirect_uri_missing")
        val authorizationServer = holder.selectAuthorizationServer(resolvedOffer.issuerMetadata, authorizationGrant.authorizationServer)
        if (authorizationServer.isErr) {
            return failed("oid4vci.authorization_server_resolve_failed", "wallet.interaction.error.oid4vci_authorization_server_resolve_failed", authorizationServer.error)
        }
        authorizationServer.value.interactiveAuthorizationEndpoint?.takeIf { it.isNotBlank() }?.let { iaeEndpoint ->
            return initiateInteractiveAuthorization(context, state, resolvedOffer, options, authorizationServer.value, iaeEndpoint)
        }
        val authorizationEndpoint =
            authorizationServer.value.authorizationEndpoint
                ?: return failed("oid4vci.authorization_endpoint_missing", "wallet.interaction.error.oid4vci_authorization_endpoint_missing")
        val authorizationRequest =
            holder.buildAuthorizationRequest(
                authorizationEndpoint = authorizationEndpoint,
                clientId = clientId,
                redirectUri = redirectUri,
                credentialConfigurationIds = state.credentialOffer?.credentialConfigurationIds ?: resolvedOffer.offer.credentialConfigurationIds,
                issuerState = authorizationGrant.issuerState,
                usePar = options.usePar,
                parEndpoint = authorizationServer.value.pushedAuthorizationRequestEndpoint,
                locations = listOf(resolvedOffer.offer.credentialIssuer),
            )
        if (authorizationRequest.isErr) {
            return failed("oid4vci.authorization_request_failed", "wallet.interaction.error.oid4vci_authorization_request_failed", authorizationRequest.error)
        }
        context.storePrivate(
            mapOf(
                "authorization_code_verifier" to authorizationRequest.value.codeVerifier,
                "authorization_state" to authorizationRequest.value.state,
                "authorization_token_endpoint" to authorizationServer.value.tokenEndpoint.orEmpty(),
                "authorization_redirect_uri" to redirectUri,
                "authorization_client_id" to clientId,
            ),
        )
        return Oid4vciIssuanceExecutionResult.AuthorizationRequired(authorizationRequest.value.authorizationUrl)
    }

    private suspend fun initiateInteractiveAuthorization(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        authorizationServer: ResolvedAuthorizationServer,
        iaeEndpoint: String,
    ): Oid4vciIssuanceExecutionResult {
        val clientId = options.clientId ?: return failed("oid4vci.client_id_missing", "wallet.interaction.error.oid4vci_client_id_missing")
        val redirectUri = options.redirectUri ?: return failed("oid4vci.redirect_uri_missing", "wallet.interaction.error.oid4vci_redirect_uri_missing")
        val tokenEndpoint =
            authorizationServer.tokenEndpoint
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing")
        val result =
            holder.initiateIae(
                InitiateIaeArgs(
                    iaeEndpoint = iaeEndpoint,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    interactionTypesSupported = listOf(IAE_OPENID4VP_PRESENTATION),
                    authorizationDetails = resolvedOffer.authorizationDetails(),
                    codeChallenge = options.iaeCodeChallenge,
                    codeChallengeMethod = options.iaeCodeChallengeMethod,
                ),
            )
        if (result.isErr) {
            return failed("oid4vci.iae_initiate_failed", "wallet.interaction.error.oid4vci_iae_initiate_failed", result.error)
        }
        context.storePrivate(
            mapOf(
                "iae_endpoint" to iaeEndpoint,
                "iae_token_endpoint" to tokenEndpoint,
                "iae_redirect_uri" to redirectUri,
                "iae_client_id" to clientId,
                "iae_code_verifier" to options.iaeCodeVerifier.orEmpty(),
            ),
        )
        return handleIaeResult(context, state, resolvedOffer, options, result.value)
    }

    private suspend fun continueInteractiveAuthorization(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        privateValues: Map<String, String>,
        action: WalletInteractionAction?,
    ): Oid4vciIssuanceExecutionResult {
        val iaeEndpoint =
            privateValues["iae_endpoint"]
                ?: return failed("oid4vci.iae_endpoint_missing", "wallet.interaction.error.oid4vci_iae_endpoint_missing", retryable = true)
        val authSession =
            privateValues["iae_auth_session"]
                ?: return failed("oid4vci.iae_auth_session_missing", "wallet.interaction.error.oid4vci_iae_auth_session_missing", retryable = true)
        val nestedResponse =
            when (
                val response =
                    nestedPresentationExecutor.createPresentationResponse(
                        context = context,
                        state = state,
                        action = action ?: WalletInteractionAction.continueFlow(),
                    )
            ) {
                is WalletNestedPresentationExecutionResult.Success -> {
                    response.value.response
                }

                is WalletNestedPresentationExecutionResult.Failed -> {
                    return failed(
                        code = response.code,
                        messageKey = response.messageKey,
                        retryable = response.retryable,
                        arguments = response.arguments,
                    )
                }
            }
        val result =
            holder.followUpIae(
                FollowUpIaeArgs(
                    iaeEndpoint = iaeEndpoint,
                    authSession = authSession,
                    openid4vpResponse = nestedResponse,
                    codeVerifier = privateValues["iae_code_verifier"]?.takeIf { it.isNotBlank() },
                ),
            )
        if (result.isErr) {
            return failed("oid4vci.iae_follow_up_failed", "wallet.interaction.error.oid4vci_iae_follow_up_failed", result.error, retryable = true)
        }

        val rawOffer =
            privateValues["entry_point.raw"]
                ?: return failed("oid4vci.credential_offer_missing", "wallet.interaction.error.oid4vci_credential_offer_missing", retryable = true)
        val offer = holder.parseCredentialOffer(rawOffer)
        if (offer.isErr) {
            return failed("oid4vci.offer_parse_failed", "wallet.interaction.error.oid4vci_offer_parse_failed", offer.error)
        }
        val resolved = holder.resolveCredentialOffer(offer.value)
        if (resolved.isErr) {
            return failed("oid4vci.offer_resolve_failed", "wallet.interaction.error.oid4vci_offer_resolve_failed", resolved.error)
        }
        val options =
            try {
                optionsProvider.options(context, state, resolved.value)
            } catch (_: Exception) {
                return failed(
                    code = "oid4vci.options_unavailable",
                    messageKey = "wallet.interaction.error.oid4vci_options_unavailable",
                    retryable = true,
                )
            }
        return handleIaeResult(context, state, resolved.value, options, result.value)
    }

    private suspend fun handleIaeResult(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        result: IaeHolderResult,
    ): Oid4vciIssuanceExecutionResult =
        when (result) {
            is IaeHolderResult.AuthorizationCode -> {
                exchangeIaeAuthorizationCodeAndRequestCredential(context, state, resolvedOffer, options, result.code)
            }

            is IaeHolderResult.Error -> {
                failed(
                    code = "oid4vci.iae_error",
                    messageKey = "wallet.interaction.error.oid4vci_iae_error",
                    arguments =
                        buildMap {
                            put("protocolError", result.error)
                            result.errorDescription?.let { put("protocolErrorDescription", it) }
                        },
                )
            }

            is IaeHolderResult.InteractionRequired -> {
                prepareNestedPresentation(context, state, result)
            }
        }

    private suspend fun prepareNestedPresentation(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        result: IaeHolderResult.InteractionRequired,
    ): Oid4vciIssuanceExecutionResult {
        if (result.type != IAE_OPENID4VP_PRESENTATION) {
            return failed(
                code = "oid4vci.iae_interaction_unsupported",
                messageKey = "wallet.interaction.error.oid4vci_iae_interaction_unsupported",
                retryable = true,
                arguments = mapOf("interactionType" to result.type),
            )
        }
        context.storePrivate(
            mapOf(
                "iae_pending" to "true",
                "iae_auth_session" to result.authSession,
                "iae_request_uri" to result.requestUri.orEmpty(),
            ),
        )
        if (result.openid4vpRequest == null && result.requestUri.isNullOrBlank()) {
            return failed(
                code = "oid4vci.iae_openid4vp_request_missing",
                messageKey = "wallet.interaction.error.oid4vci_iae_openid4vp_request_missing",
                retryable = true,
            )
        }
        val request =
            WalletNestedPresentationRequest(
                protocol = WalletProtocol.OID4VP,
                interactionType = result.type,
                requestObject = result.openid4vpRequest,
                requestUri = result.requestUri,
            )
        return when (val prepared = nestedPresentationExecutor.preparePresentation(context, state, request)) {
            is WalletNestedPresentationExecutionResult.Success -> {
                Oid4vciIssuanceExecutionResult.NestedPresentationRequired(
                    credentialSelection = prepared.value.credentialSelection,
                    disclosure = prepared.value.disclosure,
                    expiresInSeconds = result.expiresIn ?: prepared.value.expiresInSeconds,
                )
            }

            is WalletNestedPresentationExecutionResult.Failed -> {
                failed(
                    code = prepared.code,
                    messageKey = prepared.messageKey,
                    retryable = prepared.retryable,
                    arguments = prepared.arguments,
                )
            }
        }
    }

    private suspend fun exchangeIaeAuthorizationCodeAndRequestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        code: String,
    ): Oid4vciIssuanceExecutionResult {
        val privateValues = context.privateValues()
        val tokenEndpoint =
            privateValues["iae_token_endpoint"]
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing", retryable = true)
        val codeVerifier =
            privateValues["iae_code_verifier"]?.takeIf { it.isNotBlank() }
                ?: return failed("oid4vci.iae_code_verifier_missing", "wallet.interaction.error.oid4vci_iae_code_verifier_missing", retryable = true)
        val redirectUri =
            privateValues["iae_redirect_uri"]
                ?: return failed("oid4vci.redirect_uri_missing", "wallet.interaction.error.oid4vci_redirect_uri_missing", retryable = true)
        val clientId = privateValues["iae_client_id"] ?: options.clientId
        val token =
            holder.exchangeAuthorizationCode(
                tokenEndpoint = tokenEndpoint,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                clientId = clientId,
            )
        if (token.isErr) {
            return failed("oid4vci.authorization_code_exchange_failed", "wallet.interaction.error.oid4vci_authorization_code_exchange_failed", token.error)
        }
        context.storePrivate(
            mapOf(
                "access_token" to token.value.accessToken,
                "token_endpoint" to tokenEndpoint,
                "iae_pending" to "false",
            ),
        )
        val cNonce = resolveProofNonce(resolvedOffer, token.value.cNonce)
        if (cNonce.isErr) {
            return failed("oid4vci.nonce_request_failed", "wallet.interaction.error.oid4vci_nonce_request_failed", cNonce.error)
        }
        val proof =
            holder.createCredentialRequestProof(
                issuerUrl = resolvedOffer.offer.credentialIssuer,
                cNonce = cNonce.value,
                signingKeyId = options.signingKeyId,
                signingAlgorithm = options.signingAlgorithm,
                clientId = clientId,
            )
        if (proof.isErr) {
            return failed("oid4vci.proof_creation_failed", "wallet.interaction.error.oid4vci_proof_creation_failed", proof.error)
        }
        val credentialConfigurationId =
            options.credentialConfigurationId
                ?: state.credentialOffer?.credentialConfigurationIds?.firstOrNull()
                ?: resolvedOffer.offer.credentialConfigurationIds.firstOrNull()
        context.storePrivate(
            mapOf(
                "holder_key_alias" to options.signingKeyId,
                "credential_configuration_id" to credentialConfigurationId.orEmpty(),
            ),
        )
        val credential =
            holder.requestCredential(
                credentialEndpoint = resolvedOffer.issuerMetadata.credentialEndpoint,
                accessToken = token.value.accessToken,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proof.value.proofs,
            )
        if (credential.isErr) {
            return failed("oid4vci.credential_request_failed", "wallet.interaction.error.oid4vci_credential_request_failed", credential.error)
        }
        return handleCredentialResponse(context, state, resolvedOffer, credential.value)
    }

    private fun ResolvedCredentialOffer.authorizationDetails(): List<JsonElement> =
        offer.credentialConfigurationIds.map { id ->
            buildJsonObject {
                put("type", JsonPrimitive("openid_credential"))
                put("credential_configuration_id", JsonPrimitive(id))
            }
        }

    private suspend fun exchangeAuthorizationCodeAndRequestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        callback: String,
    ): Oid4vciIssuanceExecutionResult {
        val privateValues = context.privateValues()
        val code =
            callback.parameter("code")
                ?: return failed("oid4vci.authorization_code_missing", "wallet.interaction.error.oid4vci_authorization_code_missing")
        val expectedState = privateValues["authorization_state"].orEmpty()
        val returnedState = callback.parameter("state").orEmpty()
        if (expectedState.isNotBlank() && returnedState != expectedState) {
            return failed("oid4vci.authorization_state_mismatch", "wallet.interaction.error.oid4vci_authorization_state_mismatch")
        }
        val tokenEndpoint =
            privateValues["authorization_token_endpoint"]
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing")
        val codeVerifier =
            privateValues["authorization_code_verifier"]
                ?: return failed("oid4vci.code_verifier_missing", "wallet.interaction.error.oid4vci_code_verifier_missing")
        val redirectUri =
            privateValues["authorization_redirect_uri"] ?: options.redirectUri
                ?: return failed("oid4vci.redirect_uri_missing", "wallet.interaction.error.oid4vci_redirect_uri_missing")
        val clientId = privateValues["authorization_client_id"] ?: options.clientId
        val token =
            holder.exchangeAuthorizationCode(
                tokenEndpoint = tokenEndpoint,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                clientId = clientId,
            )
        if (token.isErr) {
            return failed("oid4vci.authorization_code_exchange_failed", "wallet.interaction.error.oid4vci_authorization_code_exchange_failed", token.error)
        }
        context.storePrivate(
            mapOf(
                "access_token" to token.value.accessToken,
                "token_endpoint" to tokenEndpoint,
            ),
        )
        val cNonce = resolveProofNonce(resolvedOffer, token.value.cNonce)
        if (cNonce.isErr) {
            return failed("oid4vci.nonce_request_failed", "wallet.interaction.error.oid4vci_nonce_request_failed", cNonce.error)
        }
        val proof =
            holder.createCredentialRequestProof(
                issuerUrl = resolvedOffer.offer.credentialIssuer,
                cNonce = cNonce.value,
                signingKeyId = options.signingKeyId,
                signingAlgorithm = options.signingAlgorithm,
                clientId = clientId,
            )
        if (proof.isErr) {
            return failed("oid4vci.proof_creation_failed", "wallet.interaction.error.oid4vci_proof_creation_failed", proof.error)
        }
        val credentialConfigurationId =
            options.credentialConfigurationId
                ?: state.credentialOffer?.credentialConfigurationIds?.firstOrNull()
                ?: resolvedOffer.offer.credentialConfigurationIds.firstOrNull()
        context.storePrivate(
            mapOf(
                "holder_key_alias" to options.signingKeyId,
                "credential_configuration_id" to credentialConfigurationId.orEmpty(),
            ),
        )
        val credential =
            holder.requestCredential(
                credentialEndpoint = resolvedOffer.issuerMetadata.credentialEndpoint,
                accessToken = token.value.accessToken,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proof.value.proofs,
            )
        if (credential.isErr) {
            return failed("oid4vci.credential_request_failed", "wallet.interaction.error.oid4vci_credential_request_failed", credential.error)
        }
        return handleCredentialResponse(context, state, resolvedOffer, credential.value)
    }

    private suspend fun resolveProofNonce(
        resolvedOffer: ResolvedCredentialOffer,
        fallback: String?,
    ): IdkResult<String?, IdkError> {
        val nonceEndpoint = resolvedOffer.issuerMetadata.nonceEndpoint
        if (nonceEndpoint == null) {
            return Ok(fallback)
        }
        val nonce = holder.requestNonce(nonceEndpoint)
        if (nonce.isErr) {
            return Err(nonce.error)
        }
        return Ok(nonce.value.cNonce)
    }

    private suspend fun handleCredentialResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        credentialResponse: CredentialResponse,
    ): Oid4vciIssuanceExecutionResult {
        context.storePrivate(
            mapOf(
                "notification_id" to credentialResponse.notificationId.orEmpty(),
                "notification_endpoint" to resolvedOffer.issuerMetadata.notificationEndpoint.orEmpty(),
            ),
        )
        val transactionId = credentialResponse.transactionId
        if (transactionId != null) {
            context.storePrivate(
                mapOf(
                    "deferred_transaction_id" to transactionId,
                    "deferred_credential_endpoint" to resolvedOffer.issuerMetadata.deferredCredentialEndpoint.orEmpty(),
                ),
            )
            return Oid4vciIssuanceExecutionResult.Deferred(intervalSeconds = credentialResponse.interval)
        }
        if (credentialResponse.credentials.isNullOrEmpty()) {
            return failed("oid4vci.empty_credential_response", "wallet.interaction.error.oid4vci_empty_credential_response")
        }
        val previews =
            try {
                credentialReceiver.receiveCredentialResponse(context, state, resolvedOffer, credentialResponse)
            } catch (_: Exception) {
                return failed(
                    code = "oid4vci.credential_receiver_failed",
                    messageKey = "wallet.interaction.error.oid4vci_credential_receiver_failed",
                    retryable = true,
                )
            }
        return Oid4vciIssuanceExecutionResult.Received(credentialPreview = previews)
    }

    private suspend fun notifyIssuer(
        context: WalletInteractionContext,
        event: CredentialNotificationEvent,
        eventDescription: String? = null,
    ): Oid4vciIssuerNotificationResult {
        val privateValues = context.privateValues()
        val notificationEndpoint = privateValues["notification_endpoint"].orEmpty()
        val accessToken = privateValues["access_token"].orEmpty()
        val notificationId = privateValues["notification_id"].orEmpty()
        if (notificationEndpoint.isBlank() || accessToken.isBlank() || notificationId.isBlank()) {
            return Oid4vciIssuerNotificationResult.NotSupported
        }
        val result =
            holder.sendNotification(
                notificationEndpoint = notificationEndpoint,
                accessToken = accessToken,
                notificationId = notificationId,
                event = event,
                eventDescription = eventDescription,
            )
        if (result.isErr) {
            return Oid4vciIssuerNotificationResult.Failed(
                code = "oid4vci.notification_failed",
                messageKey = "wallet.interaction.error.oid4vci_notification_failed",
                arguments = mapOf("providerErrorCode" to result.error.code),
            )
        }
        return Oid4vciIssuerNotificationResult.Sent
    }

    private suspend fun WalletInteractionContext.privateValues(): Map<String, String> =
        privateSessionStore
            .get(sessionId, Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID)
            ?.values
            .orEmpty()

    private suspend fun WalletInteractionContext.storePrivate(values: Map<String, String>) {
        val sanitized = values.filterValues { it.isNotBlank() }
        if (sanitized.isEmpty()) return
        val existing = privateValues()
        privateSessionStore.put(
            sessionId,
            WalletInteractionPrivateSessionData(
                namespace = Oid4vciWalletInteractionProtocolAdapter.ADAPTER_ID,
                values = existing + sanitized,
            ),
        )
    }

    private fun failed(
        code: String,
        messageKey: String,
        error: IdkError,
        retryable: Boolean = false,
    ): Oid4vciIssuanceExecutionResult.Failed =
        failed(
            code = code,
            messageKey = messageKey,
            retryable = retryable,
            arguments = mapOf("providerErrorCode" to error.code),
        )

    private fun failed(
        code: String,
        messageKey: String,
        retryable: Boolean = false,
        arguments: Map<String, String> = emptyMap(),
    ): Oid4vciIssuanceExecutionResult.Failed =
        Oid4vciIssuanceExecutionResult.Failed(
            code = code,
            messageKey = messageKey,
            retryable = retryable,
            arguments = arguments,
        )

    private fun String.parameter(name: String): String? {
        val parameterText =
            when {
                "?" in this -> substringAfter("?").substringBefore("#")
                "#" in this -> substringAfter("#")
                else -> this
            }
        return parameterText
            .split("&")
            .firstOrNull { it.substringBefore("=") == name }
            ?.substringAfter("=", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }
}

private const val IAE_OPENID4VP_PRESENTATION: String = "urn:openid:dcp:iae:openid4vp_presentation"

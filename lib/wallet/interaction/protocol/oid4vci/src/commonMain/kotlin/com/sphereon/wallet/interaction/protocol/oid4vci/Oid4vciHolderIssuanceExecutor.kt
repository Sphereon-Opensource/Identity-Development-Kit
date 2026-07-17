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
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.holder.CreatedProof
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialRefreshMethod
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutionResult
import com.sphereon.wallet.interaction.WalletNestedPresentationExecutor
import com.sphereon.wallet.interaction.WalletNestedPresentationRequest
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletProviderAttestationSignerRef
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaClientAttestationAuthRequest
import com.sphereon.wallet.wsca.WscaDpopProofRequest
import com.sphereon.core.api.encodeToBase64Url
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

data class Oid4vciHolderIssuanceOptions(
    val signingKeyId: String,
    val operationBinding: String? = null,
    val signingAlgorithm: String = "ES256",
    val clientId: String? = null,
    val redirectUri: String? = null,
    val credentialConfigurationId: String? = null,
    val usePar: Boolean = false,
    val iaeCodeVerifier: String? = null,
    val iaeCodeChallenge: String? = null,
    val iaeCodeChallengeMethod: String? = null,
    val haipTokenProofs: Oid4vciHaipTokenProofOptions? = null,
)

data class Oid4vciHaipTokenProofOptions(
    val walletUnitId: String,
    val walletAccountId: String,
    val walletName: String,
    val walletVersion: String,
    val walletLink: String? = null,
    val clientInstanceKeyAlias: String? = null,
    val clientInstanceKeyAlgorithm: String = "ES256",
    val signer: WalletProviderAttestationSignerRef? = null,
    val attestationChallenge: String? = null,
    val evidence: Map<String, String> = emptyMap(),
)

data class Oid4vciTokenEndpointProofRequest(
    val context: WalletInteractionContext,
    val state: WalletInteractionState,
    val resolvedOffer: ResolvedCredentialOffer,
    val options: Oid4vciHolderIssuanceOptions,
    val tokenEndpoint: String,
    val authorizationServerIssuer: String? = null,
    val nonce: String? = null,
)

data class Oid4vciDpopProofRequest(
    val context: WalletInteractionContext,
    val state: WalletInteractionState,
    val resolvedOffer: ResolvedCredentialOffer,
    val options: Oid4vciHolderIssuanceOptions,
    val httpMethod: String,
    val httpUrl: String,
    val nonce: String? = null,
    val accessToken: String? = null,
)

data class Oid4vciTokenEndpointProofs(
    val dpopProofJwt: String? = null,
    val clientAttestationJwt: String? = null,
    val clientAttestationPopJwt: String? = null,
    val clientAuthentication: ClientAuthenticationConfig? = null,
) {
    init {
        require(clientAuthentication == null || clientAuthentication !is ClientAuthenticationConfig.AttestationJwt) {
            "attest_jwt_client_auth must use clientAttestationJwt/clientAttestationPopJwt headers in OID4VCI token proofs"
        }
    }
}

interface Oid4vciIssuanceOptionsProvider {
    suspend fun options(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
    ): Oid4vciHolderIssuanceOptions
}

interface Oid4vciTokenEndpointProofsProvider {
    suspend fun proofs(request: Oid4vciTokenEndpointProofRequest): IdkResult<Oid4vciTokenEndpointProofs, IdkError>

    suspend fun dpopProof(request: Oid4vciDpopProofRequest): IdkResult<String?, IdkError> = Ok(null)

    companion object {
        val none: Oid4vciTokenEndpointProofsProvider =
            object : Oid4vciTokenEndpointProofsProvider {
                override suspend fun proofs(request: Oid4vciTokenEndpointProofRequest): IdkResult<Oid4vciTokenEndpointProofs, IdkError> =
                    Ok(Oid4vciTokenEndpointProofs())
            }
    }
}

class SecureComponentOid4vciTokenEndpointProofsProvider(
    private val secureComponentCryptoSurface: Wsca,
) : Oid4vciTokenEndpointProofsProvider {
    override suspend fun dpopProof(request: Oid4vciDpopProofRequest): IdkResult<String?, IdkError> {
        val haip = request.options.haipTokenProofs ?: return Ok(null)
        val clientInstanceKey =
            secureComponentCryptoSurface
                .ensureKey(
                    walletUnitId = haip.walletUnitId,
                    usage = SecureComponentUsage.WALLET_ATTESTATION,
                    algorithm = signatureAlgorithm(haip.clientInstanceKeyAlgorithm),
                    keyAlias = haip.clientInstanceKeyAlias,
                ).getOrElse { return Err(it) }
        val dpop =
            secureComponentCryptoSurface
                .createDpopProof(
                    WscaDpopProofRequest(
                        walletUnitId = haip.walletUnitId,
                        operationBinding = requireOperationBinding(request.options),
                        keyRef = clientInstanceKey,
                        httpMethod = request.httpMethod,
                        httpUrl = request.httpUrl,
                        nonce = request.nonce,
                        accessToken = request.accessToken,
                    ),
                ).getOrElse { return Err(it) }
        return Ok(dpop.proofJwt)
    }

    override suspend fun proofs(request: Oid4vciTokenEndpointProofRequest): IdkResult<Oid4vciTokenEndpointProofs, IdkError> {
        val haip = request.options.haipTokenProofs ?: return Ok(Oid4vciTokenEndpointProofs())
        val clientId =
            request.options.clientId
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "HAIP token proofs require an OID4VCI client_id"))
        val clientInstanceKey =
            secureComponentCryptoSurface
                .ensureKey(
                    walletUnitId = haip.walletUnitId,
                    usage = SecureComponentUsage.WALLET_ATTESTATION,
                    algorithm = signatureAlgorithm(haip.clientInstanceKeyAlgorithm),
                    keyAlias = haip.clientInstanceKeyAlias,
                ).getOrElse { return Err(it) }
        val dpop =
            dpopProof(
                Oid4vciDpopProofRequest(
                    context = request.context,
                    state = request.state,
                    resolvedOffer = request.resolvedOffer,
                    options = request.options,
                    httpMethod = "POST",
                    httpUrl = request.tokenEndpoint,
                    nonce = request.nonce,
                ),
            ).getOrElse { return Err(it) }
        val clientAuth =
            secureComponentCryptoSurface
                .createClientAttestationAuth(
                    WscaClientAttestationAuthRequest(
                        walletUnitId = haip.walletUnitId,
                        operationBinding = requireOperationBinding(request.options),
                        walletAccountId = haip.walletAccountId,
                        clientId = clientId,
                        audience = request.tokenEndpoint,
                        clientInstanceKey = clientInstanceKey,
                        walletName = haip.walletName,
                        walletVersion = haip.walletVersion,
                        walletLink = haip.walletLink,
                        signer = haip.signer,
                        challenge = haip.attestationChallenge,
                        evidence = haip.evidence,
                    ),
                ).getOrElse { return Err(it) }
        return Ok(
            Oid4vciTokenEndpointProofs(
                dpopProofJwt = dpop,
                clientAttestationJwt = clientAuth.clientAttestationJwt,
                clientAttestationPopJwt = clientAuth.clientAttestationPopJwt,
            ),
        )
    }
}

data class Oid4vciCredentialRequestProofRequest(
    val walletUnitId: String,
    val operationBinding: String? = null,
    val issuerUrl: String,
    val cNonce: String? = null,
    val signingKeyId: String,
    val signingAlgorithm: String = "ES256",
    val clientId: String? = null,
    val keyAttestationJwt: String? = null,
)

/**
 * Creates the OID4VCI 1.0 Appendix F.1 credential-request JWT proof (`typ=openid4vci-proof+jwt`).
 * Kept as an injectable seam - mirroring [Oid4vciTokenEndpointProofsProvider] - because WHERE the
 * signing key actually lives differs by composition root: the product wallet stack mints and holds
 * holder keys through [Wsca]/[Wscd] (see [SecureComponentOid4vciCredentialRequestProofProvider]),
 * while a bare [Oid4vciHolderService] consumer that provisions keys directly in a
 * `KeyManagerService` uses [HolderServiceOid4vciCredentialRequestProofProvider] instead. Every
 * composition root MUST wire exactly one of the two; there is no default.
 */
interface Oid4vciCredentialRequestProofProvider {
    suspend fun createProof(request: Oid4vciCredentialRequestProofRequest): IdkResult<CreatedProof, IdkError>
}

/**
 * Delegates credential-request proof creation to the generic [Oid4vciHolderService], which resolves
 * [Oid4vciCredentialRequestProofRequest.signingKeyId] as a managed KMS key identifier. Correct ONLY
 * when the signing key was provisioned directly in a `KeyManagerService` (the standalone OID4VCI
 * holder library used outside the wallet product, and JVM-only test/integration harnesses that mint
 * keys straight into the KMS). Wallet products that mint holder keys through [Wsca] MUST use
 * [SecureComponentOid4vciCredentialRequestProofProvider] instead: a Wscd-custodied key (e.g.
 * non-extractable browser WebCrypto keys on the js target) does not round-trip through the KMS, so
 * this provider cannot resolve it and fails with a key-not-found error.
 */
class HolderServiceOid4vciCredentialRequestProofProvider(
    private val holder: Oid4vciHolderService,
) : Oid4vciCredentialRequestProofProvider {
    override suspend fun createProof(request: Oid4vciCredentialRequestProofRequest): IdkResult<CreatedProof, IdkError> =
        holder.createCredentialRequestProof(
            issuerUrl = request.issuerUrl,
            cNonce = request.cNonce,
            signingKeyIds = listOf(request.signingKeyId),
            signingAlgorithm = request.signingAlgorithm,
            clientId = request.clientId,
            keyAttestationJwt = request.keyAttestationJwt,
        )
}

/**
 * WSCA/WSCD-backed credential-request proof creation: resolves
 * [Oid4vciCredentialRequestProofRequest.signingKeyId] through [Wsca.ensureKey] (idempotent
 * alias-based resolution - repeated calls with the same non-null alias return the SAME key; this is
 * the established pattern [Oid4vciKeyAttestationProvider] implementations already use to re-resolve
 * a previously-minted holder key by its opaque alias) and signs the JWT with [Wsca.sign]. This is
 * the ONLY correct path when the signing key was minted via [Wsca] (the product wallet stack): the
 * private key never leaves, and may not even be resolvable through, a `KeyManagerService` (e.g.
 * non-extractable browser WebCrypto custody on the js target).
 */
class SecureComponentOid4vciCredentialRequestProofProvider(
    private val secureComponentCryptoSurface: Wsca,
) : Oid4vciCredentialRequestProofProvider {
    override suspend fun createProof(request: Oid4vciCredentialRequestProofRequest): IdkResult<CreatedProof, IdkError> {
        val operationBinding =
            request.operationBinding?.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VCI credential proof requires attended operation binding"))
        val keyRef =
            secureComponentCryptoSurface
                .ensureKey(
                    walletUnitId = request.walletUnitId,
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = signatureAlgorithm(request.signingAlgorithm),
                    keyAlias = request.signingKeyId,
                ).getOrElse { return Err(it) }
        val publicJwk =
            keyRef.publicKeyJwk
                ?: return Err(
                    IdkError.fromString(
                        code = "oid4vci.credential_proof_key_missing_jwk",
                        message = "Secure-component holder key '${keyRef.keyId}' does not carry a public JWK",
                    ),
                )
        val header =
            buildJsonObject {
                put("typ", JsonPrimitive(PROOF_JWT_TYP))
                put("alg", JsonPrimitive(request.signingAlgorithm))
                put("jwk", Json.parseToJsonElement(publicJwk))
                request.keyAttestationJwt?.takeIf { it.isNotBlank() }?.let { put("key_attestation", JsonPrimitive(it)) }
            }
        val payload =
            buildJsonObject {
                put("aud", JsonPrimitive(request.issuerUrl))
                put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
                request.cNonce?.let { put("nonce", JsonPrimitive(it)) }
                request.clientId?.let { put("iss", JsonPrimitive(it)) }
            }
        val encodedHeader = json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
        val encodedPayload = json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        val signingInput = "$encodedHeader.$encodedPayload".encodeToByteArray()
        val signature =
            secureComponentCryptoSurface
                .sign(
                    walletUnitId = request.walletUnitId,
                    keyRef = keyRef,
                    signingInput = signingInput,
                    operationBinding = operationBinding,
                )
                .getOrElse { return Err(it) }
        val jwt = "$encodedHeader.$encodedPayload.${signature.encodeToBase64Url()}"
        return Ok(CreatedProof(proofs = CredentialRequestProofs.jwt(listOf(jwt))))
    }

    private companion object {
        const val PROOF_JWT_TYP = "openid4vci-proof+jwt"
        val json = Json { encodeDefaults = false; explicitNulls = false }
    }
}

private fun requireOperationBinding(options: Oid4vciHolderIssuanceOptions): String =
    options.operationBinding?.takeIf { it.isNotBlank() }
        ?: error("OID4VCI secure-component operation requires attended authorization binding")

interface Oid4vciCredentialResponseReceiver {
    suspend fun receiveCredentialResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        credentialResponse: CredentialResponse,
    ): List<WalletCredentialPreview>
}

data class Oid4vciRefreshTokenGrantRequest(
    val tokenEndpoint: String,
    val refreshToken: String,
    val clientId: String? = null,
    val dpopProofJwt: String? = null,
    val clientAttestationJwt: String? = null,
    val clientAttestationPopJwt: String? = null,
    val clientAuthentication: ClientAuthenticationConfig? = null,
)

/**
 * Executes the OAuth2 `refresh_token` grant (RFC 6749 Section 6) for wallet-initiated credential
 * refresh. Kept as an injectable seam - mirroring
 * [Oid4vciTokenEndpointProofsProvider] - rather than a new [Oid4vciHolderService] command: no OID4VCI
 * holder command exposes this grant type today (only `exchangePreAuthorizedCode`/
 * `exchangeAuthorizationCode`), and the actual HTTP/token-endpoint mechanics belong at the same
 * composition-root layer (oauth2-client) that already backs those two commands, not duplicated
 * inside the wallet-interaction module.
 */
interface Oid4vciRefreshTokenGrantProvider {
    suspend fun exchangeRefreshToken(request: Oid4vciRefreshTokenGrantRequest): IdkResult<TokenResponseWithContext, IdkError>

    companion object {
        /** Safe default: refresh fails with a typed, non-throwing error when no grant provider is wired. */
        val unsupported: Oid4vciRefreshTokenGrantProvider =
            object : Oid4vciRefreshTokenGrantProvider {
                override suspend fun exchangeRefreshToken(request: Oid4vciRefreshTokenGrantRequest): IdkResult<TokenResponseWithContext, IdkError> =
                    Err(
                        IdkError.fromString(
                            code = "oid4vci.refresh_token_grant_unsupported",
                            message = "No refresh-token grant provider is configured for this wallet deployment",
                        ),
                    )
            }
    }
}

class Oid4vciHolderIssuanceExecutor(
    private val holder: Oid4vciHolderService,
    private val optionsProvider: Oid4vciIssuanceOptionsProvider,
    private val credentialReceiver: Oid4vciCredentialResponseReceiver,
    private val nestedPresentationExecutor: WalletNestedPresentationExecutor = WalletNestedPresentationExecutor.notConfigured,
    private val tokenEndpointProofsProvider: Oid4vciTokenEndpointProofsProvider = Oid4vciTokenEndpointProofsProvider.none,
    /**
     * Wallet-initiated refresh ([refreshCredential]) reads the existing credential record and its
     * stored refresh token through these two stores.
     */
    private val credentialStore: WalletCredentialStore,
    private val issuanceSessionStore: WalletIssuanceSessionStore,
    private val refreshTokenGrantProvider: Oid4vciRefreshTokenGrantProvider,
    /** KA-on-demand (OID4VCI 1.0 proof_types_supported.jwt.key_attestations_required). */
    private val keyAttestationProvider: Oid4vciKeyAttestationProvider,
    /**
     * Creates the credential-request JWT proof. See [Oid4vciCredentialRequestProofProvider] KDoc
     * for which of the two implementations a given composition root must wire.
     */
    private val credentialRequestProofProvider: Oid4vciCredentialRequestProofProvider,
) : Oid4vciIssuanceExecutor {
    override suspend fun requestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        action: WalletInteractionAction?,
    ): Oid4vciIssuanceExecutionResult {
        val sessionState = context.oid4vciState()
        if (state.status == WalletInteractionStatus.DeferredRetrievalPending) {
            return requestDeferredCredential(context, state, sessionState)
        }
        if (sessionState.iae?.pending == true) {
            return continueInteractiveAuthorization(context, state, sessionState, action)
        }

        val rawOffer =
            sessionState.entryPointRaw?.takeIf { it.isNotBlank() }
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
            sessionState.authorizationCallback?.takeIf { it.isNotBlank() }?.let { callback ->
                return exchangeAuthorizationCodeAndRequestCredential(context, state, resolved.value, options, callback)
            }
            return buildAuthorizationRequest(context, state, resolved.value, options)
        }

        val authorizationServer = holder.selectAuthorizationServer(resolved.value.issuerMetadata, preAuthorizedGrant.authorizationServer)
        if (authorizationServer.isErr) {
            return failed("oid4vci.authorization_server_resolve_failed", "wallet.interaction.error.oid4vci_authorization_server_resolve_failed", authorizationServer.error)
        }
        val tokenEndpoint = authorizationServer.value.tokenEndpoint
        val tokenProofs = tokenEndpointProofs(context, state, resolved.value, options, tokenEndpoint, authorizationServer.value.issuer).getOrElse {
            return failed("oid4vci.token_proofs_failed", "wallet.interaction.error.oid4vci_token_proofs_failed", it)
        }

        val token =
            exchangePreAuthorizedCodeWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolved.value,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorizationServer.value.issuer,
                preAuthorizedCode = preAuthorizedGrant.preAuthorizedCode,
                txCode = sessionState.txCode,
                clientId = options.clientId,
                redirectUri = options.redirectUri,
                dpopProofJwt = tokenProofs.dpopProofJwt,
                clientAttestationJwt = tokenProofs.clientAttestationJwt,
                clientAttestationPopJwt = tokenProofs.clientAttestationPopJwt,
                clientAuthentication = tokenProofs.clientAuthentication,
            )
        if (token.isErr) {
            return failed("oid4vci.token_exchange_failed", "wallet.interaction.error.oid4vci_token_exchange_failed", token.error)
        }
        context.updateOid4vciState {
            it.copy(
                tokens =
                    Oid4vciPrivateSessionState.TokenLeg(
                        accessToken = token.value.accessToken,
                        refreshToken = token.value.refreshToken,
                        tokenEndpoint = tokenEndpoint,
                    ),
            )
        }

        val cNonce = resolveProofNonce(resolved.value, token.value.cNonce)
        if (cNonce.isErr) {
            return failed("oid4vci.nonce_request_failed", "wallet.interaction.error.oid4vci_nonce_request_failed", cNonce.error)
        }
        val credentialConfigurationId = selectCredentialConfigurationId(options, state, resolved.value)
        val keyAttestationJwt =
            keyAttestationJwtIfRequired(
                context,
                resolved.value,
                credentialConfigurationId,
                options.signingKeyId,
                options.signingAlgorithm,
                cNonce.value,
                options.operationBinding,
            )
                .getOrElse { return failed("oid4vci.key_attestation_failed", "wallet.interaction.error.oid4vci_key_attestation_failed", it) }
        val proof =
            credentialRequestProofProvider.createProof(
                Oid4vciCredentialRequestProofRequest(
                    walletUnitId = context.walletUnitId,
                    operationBinding = options.operationBinding,
                    issuerUrl = resolved.value.offer.credentialIssuer,
                    cNonce = cNonce.value,
                    signingKeyId = options.signingKeyId,
                    signingAlgorithm = options.signingAlgorithm,
                    clientId = options.clientId,
                    keyAttestationJwt = keyAttestationJwt,
                ),
            )
        if (proof.isErr) {
            return failed("oid4vci.proof_creation_failed", "wallet.interaction.error.oid4vci_proof_creation_failed", proof.error)
        }

        val credentialDpopProof = credentialEndpointDpopProof(context, state, resolved.value, options, token.value.accessToken).getOrElse {
            return failed("oid4vci.credential_dpop_proof_failed", "wallet.interaction.error.oid4vci_credential_dpop_proof_failed", it)
        }
        context.updateOid4vciState {
            it.copy(
                holderKeyAliases = listOf(options.signingKeyId),
                credentialConfigurationId = credentialConfigurationId,
            )
        }
        val credential =
            requestCredentialWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolved.value,
                options = options,
                accessToken = token.value.accessToken,
                dpopProofJwt = credentialDpopProof,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proof.value.proofs,
            )
        if (credential.isErr) {
            return failed("oid4vci.credential_request_failed", "wallet.interaction.error.oid4vci_credential_request_failed", credential.error)
        }

        return handleCredentialResponse(context, state, resolved.value, credential.value)
    }

    /**
     * Wallet-initiated credential refresh: loads the existing record ->
     * looks up its stored OAuth2 refresh token -> re-resolves the authorization server (issuer
     * endpoints are never cached on the record) -> exchanges the refresh token for a fresh access
     * token -> re-requests the credential with the record's EXISTING active-instance holder key
     * (REUSED, not freshly minted - see the holderKeyAlias derivation below) -> stores through the
     * SAME [credentialReceiver] used for normal issuance, so supersede semantics live in one place.
     *
     * Every failure path returns a typed [Oid4vciIssuanceExecutionResult.Failed]; nothing throws.
     */
    override suspend fun refreshCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        credentialRecordId: String,
    ): Oid4vciIssuanceExecutionResult {
        val recordResult = credentialStore.getCredential(context.walletUnitId, credentialRecordId)
        if (recordResult.isErr) {
            return failed("oid4vci.refresh_record_lookup_failed", "wallet.interaction.error.oid4vci_refresh_record_lookup_failed", recordResult.error, retryable = true)
        }
        val record =
            recordResult.value
                ?: return failed("oid4vci.refresh_record_not_found", "wallet.interaction.error.oid4vci_refresh_record_not_found")

        val refreshState =
            record.refreshState
                ?: return failed("oid4vci.refresh_state_missing", "wallet.interaction.error.oid4vci_refresh_state_missing")
        if (refreshState.refreshMethod != CredentialRefreshMethod.OID4VCI_REISSUANCE) {
            return failed("oid4vci.refresh_method_unsupported", "wallet.interaction.error.oid4vci_refresh_method_unsupported")
        }
        val provenance =
            record.issuanceProvenance
                ?: return failed("oid4vci.refresh_provenance_missing", "wallet.interaction.error.oid4vci_refresh_provenance_missing")
        val credentialIssuer = provenance.credentialIssuerUrl
        val credentialConfigurationId = provenance.credentialConfigurationId

        // REUSE (not mint): the EXISTING active instance's holder key is re-presented on
        // reissuance. This is the OPPOSITE
        // of normal issuance/obtainCredential, which mints a fresh unlinkability key per credential;
        // a reissued credential intentionally keeps the SAME public key as the credential it replaces.
        val holderKeyAlias =
            record.instances
                .lastOrNull { it.lifecycleState == CredentialLifecycleState.ACTIVE }
                ?.holderKeyRef
                ?.alias
                ?: record.instances.lastOrNull()?.holderKeyRef?.alias
                ?: return failed("oid4vci.refresh_holder_key_missing", "wallet.interaction.error.oid4vci_refresh_holder_key_missing")

        val refreshTokenResult = issuanceSessionStore.getRefreshToken(context.walletUnitId, credentialRecordId)
        if (refreshTokenResult.isErr) {
            return failed("oid4vci.refresh_token_lookup_failed", "wallet.interaction.error.oid4vci_refresh_token_lookup_failed", refreshTokenResult.error, retryable = true)
        }
        val refreshToken =
            refreshTokenResult.value?.takeIf { it.isNotBlank() }
                ?: return failed("oid4vci.refresh_token_missing", "wallet.interaction.error.oid4vci_refresh_token_missing")

        val metadataResult = holder.resolveIssuerMetadata(credentialIssuer)
        if (metadataResult.isErr) {
            return failed("oid4vci.refresh_issuer_metadata_failed", "wallet.interaction.error.oid4vci_refresh_issuer_metadata_failed", metadataResult.error, retryable = true)
        }
        val metadata = metadataResult.value
        val credConfig =
            metadata.credentialConfigurationsSupported[credentialConfigurationId]
                ?: return failed("oid4vci.refresh_credential_configuration_unknown", "wallet.interaction.error.oid4vci_refresh_credential_configuration_unknown")
        if (CredentialFormat.fromValueLenient(credConfig.format) == null) {
            return failed("oid4vci.refresh_credential_format_unsupported", "wallet.interaction.error.oid4vci_refresh_credential_format_unsupported")
        }

        // Do NOT persist AS endpoints on the record: re-resolve the authorization server fresh from
        // issuer metadata every refresh, exactly like the offer-based flow always re-derives it
        // rather than trusting a stale cached endpoint.
        val authorizationServer = holder.selectAuthorizationServer(metadata, null)
        if (authorizationServer.isErr) {
            return failed(
                "oid4vci.refresh_authorization_server_resolve_failed",
                "wallet.interaction.error.oid4vci_refresh_authorization_server_resolve_failed",
                authorizationServer.error,
                retryable = true,
            )
        }
        val tokenEndpoint = authorizationServer.value.tokenEndpoint

        // Effective credential/refresh endpoint: refreshState.refreshEndpoint overrides the issuer's
        // default credential_endpoint when the issuer advertises a distinct reissuance endpoint.
        val effectiveMetadata =
            refreshState.refreshEndpoint
                ?.takeIf { it.isNotBlank() }
                ?.let { metadata.copy(credentialEndpoint = it) }
                ?: metadata
        // Synthetic offer/resolved-offer: a wallet-initiated refresh has no CredentialOffer at all, but
        // every downstream helper (options provider, DPoP proof provider, requestCredentialWithDpopNonceRetry)
        // is shaped around ResolvedCredentialOffer. `offer` here is a placeholder carrying only the
        // issuer/config the refresh targets; nothing reads its (absent) grants.
        val syntheticOffer = CredentialOffer(credentialIssuer = credentialIssuer, credentialConfigurationIds = listOf(credentialConfigurationId), grants = null)
        val resolvedOfferForRefresh = ResolvedCredentialOffer(offer = syntheticOffer, issuerMetadata = effectiveMetadata)

        val baseOptions =
            try {
                optionsProvider.options(context, state, resolvedOfferForRefresh)
            } catch (_: Exception) {
                return failed(code = "oid4vci.refresh_options_unavailable", messageKey = "wallet.interaction.error.oid4vci_refresh_options_unavailable", retryable = true)
            }
        // Override signingKeyId to the REUSED holder key regardless of what the options provider would
        // otherwise mint for a NEW-key issuance flow (see the holderKeyAlias derivation/comment above).
        val options = baseOptions.copy(signingKeyId = holderKeyAlias, credentialConfigurationId = credentialConfigurationId)

        context.updateOid4vciState {
            it.copy(
                refreshTargetCredentialRecordId = credentialRecordId,
                credentialConfigurationId = credentialConfigurationId,
                holderKeyAliases = listOf(holderKeyAlias),
            )
        }

        val tokenResult =
            exchangeRefreshTokenWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolvedOfferForRefresh,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorizationServer.value.issuer,
                refreshToken = refreshToken,
            )
        if (tokenResult.isErr) {
            return failed("oid4vci.refresh_token_exchange_failed", "wallet.interaction.error.oid4vci_refresh_token_exchange_failed", tokenResult.error, retryable = true)
        }
        val token = tokenResult.value
        context.updateOid4vciState {
            it.copy(
                tokens =
                    Oid4vciPrivateSessionState.TokenLeg(
                        accessToken = token.accessToken,
                        // RFC 6749 Section 6: reuse the presented refresh token when the AS does not
                        // rotate it; the receiver persists whatever ends up here as the record's next
                        // refresh token.
                        refreshToken = token.refreshToken ?: refreshToken,
                        tokenEndpoint = tokenEndpoint,
                    ),
            )
        }

        val cNonce = resolveProofNonce(resolvedOfferForRefresh, token.cNonce)
        if (cNonce.isErr) {
            return failed("oid4vci.refresh_nonce_request_failed", "wallet.interaction.error.oid4vci_refresh_nonce_request_failed", cNonce.error)
        }
        val keyAttestationJwt =
            keyAttestationJwtIfRequired(
                context,
                resolvedOfferForRefresh,
                credentialConfigurationId,
                holderKeyAlias,
                options.signingAlgorithm,
                cNonce.value,
                options.operationBinding,
            )
                .getOrElse { return failed("oid4vci.refresh_key_attestation_failed", "wallet.interaction.error.oid4vci_refresh_key_attestation_failed", it, retryable = true) }
        val proof =
            credentialRequestProofProvider.createProof(
                Oid4vciCredentialRequestProofRequest(
                    walletUnitId = context.walletUnitId,
                    operationBinding = options.operationBinding,
                    issuerUrl = credentialIssuer,
                    cNonce = cNonce.value,
                    signingKeyId = holderKeyAlias,
                    signingAlgorithm = options.signingAlgorithm,
                    clientId = options.clientId,
                    keyAttestationJwt = keyAttestationJwt,
                ),
            )
        if (proof.isErr) {
            return failed("oid4vci.refresh_proof_creation_failed", "wallet.interaction.error.oid4vci_refresh_proof_creation_failed", proof.error)
        }

        val credentialDpopProof =
            credentialEndpointDpopProof(context, state, resolvedOfferForRefresh, options, token.accessToken).getOrElse {
                return failed("oid4vci.refresh_credential_dpop_proof_failed", "wallet.interaction.error.oid4vci_refresh_credential_dpop_proof_failed", it, retryable = true)
            }
        val credential =
            requestCredentialWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolvedOfferForRefresh,
                options = options,
                accessToken = token.accessToken,
                dpopProofJwt = credentialDpopProof,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proof.value.proofs,
            )
        if (credential.isErr) {
            return failed("oid4vci.refresh_credential_request_failed", "wallet.interaction.error.oid4vci_refresh_credential_request_failed", credential.error, retryable = true)
        }
        val credentialResponse = credential.value
        if (credentialResponse.transactionId != null) {
            // Deferred reissuance is not supported: a refresh must complete synchronously.
            return failed("oid4vci.refresh_deferred_unsupported", "wallet.interaction.error.oid4vci_refresh_deferred_unsupported")
        }
        return handleCredentialResponse(context, state, resolvedOfferForRefresh, credentialResponse)
    }

    private suspend fun requestDeferredCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        sessionState: Oid4vciPrivateSessionState,
    ): Oid4vciIssuanceExecutionResult {
        val deferred =
            sessionState.deferred
                ?: return failed("oid4vci.deferred_endpoint_missing", "wallet.interaction.error.oid4vci_deferred_endpoint_missing", retryable = true)
        val accessToken =
            sessionState.tokens?.accessToken
                ?: return failed("oid4vci.access_token_missing", "wallet.interaction.error.oid4vci_access_token_missing", retryable = true)
        val credential =
            holder.requestDeferredCredential(
                deferredCredentialEndpoint = deferred.deferredCredentialEndpoint,
                accessToken = accessToken,
                transactionId = deferred.transactionId,
            )
        if (credential.isErr) {
            return failed("oid4vci.deferred_request_failed", "wallet.interaction.error.oid4vci_deferred_request_failed", credential.error, retryable = true)
        }

        val rawOffer =
            sessionState.entryPointRaw?.takeIf { it.isNotBlank() }
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
                credentialConfigurationIds = state.effectiveOfferedCredentialConfigurationIds(resolvedOffer),
                issuerState = authorizationGrant.issuerState,
                usePar = options.usePar,
                parEndpoint = authorizationServer.value.pushedAuthorizationRequestEndpoint,
                locations = listOf(resolvedOffer.offer.credentialIssuer),
            )
        if (authorizationRequest.isErr) {
            return failed("oid4vci.authorization_request_failed", "wallet.interaction.error.oid4vci_authorization_request_failed", authorizationRequest.error)
        }
        context.updateOid4vciState {
            it.copy(
                authorization =
                    Oid4vciPrivateSessionState.AuthorizationLeg(
                        authorizationServerIssuer = authorizationServer.value.issuer,
                        tokenEndpoint = authorizationServer.value.tokenEndpoint,
                        codeVerifier = authorizationRequest.value.codeVerifier,
                        state = authorizationRequest.value.state,
                        redirectUri = redirectUri,
                        clientId = clientId,
                    ),
            )
        }
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
        val tokenEndpoint = authorizationServer.tokenEndpoint
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
        context.updateOid4vciState {
            it.copy(
                iae =
                    Oid4vciPrivateSessionState.IaeLeg(
                        authorizationServerIssuer = authorizationServer.issuer,
                        tokenEndpoint = tokenEndpoint,
                        iaeEndpoint = iaeEndpoint,
                        redirectUri = redirectUri,
                        clientId = clientId,
                        codeVerifier = options.iaeCodeVerifier,
                        pending = false,
                    ),
            )
        }
        return handleIaeResult(context, state, resolvedOffer, options, result.value)
    }

    private suspend fun continueInteractiveAuthorization(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        sessionState: Oid4vciPrivateSessionState,
        action: WalletInteractionAction?,
    ): Oid4vciIssuanceExecutionResult {
        val iae =
            sessionState.iae
                ?: return failed("oid4vci.iae_endpoint_missing", "wallet.interaction.error.oid4vci_iae_endpoint_missing", retryable = true)
        val iaeEndpoint =
            iae.iaeEndpoint
                ?: return failed("oid4vci.iae_endpoint_missing", "wallet.interaction.error.oid4vci_iae_endpoint_missing", retryable = true)
        val authSession =
            iae.authSession
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
                    codeVerifier = iae.codeVerifier?.takeIf { it.isNotBlank() },
                ),
            )
        if (result.isErr) {
            return failed("oid4vci.iae_follow_up_failed", "wallet.interaction.error.oid4vci_iae_follow_up_failed", result.error, retryable = true)
        }

        val rawOffer =
            sessionState.entryPointRaw?.takeIf { it.isNotBlank() }
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
        context.updateOid4vciState {
            it.copy(
                iae =
                    (it.iae ?: Oid4vciPrivateSessionState.IaeLeg()).copy(
                        pending = true,
                        authSession = result.authSession,
                        requestUri = result.requestUri,
                    ),
            )
        }
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
        val sessionState = context.oid4vciState()
        val iae =
            sessionState.iae
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing", retryable = true)
        val tokenEndpoint =
            iae.tokenEndpoint
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing", retryable = true)
        val codeVerifier =
            iae.codeVerifier?.takeIf { it.isNotBlank() }
                ?: return failed("oid4vci.iae_code_verifier_missing", "wallet.interaction.error.oid4vci_iae_code_verifier_missing", retryable = true)
        val redirectUri =
            iae.redirectUri
                ?: return failed("oid4vci.redirect_uri_missing", "wallet.interaction.error.oid4vci_redirect_uri_missing", retryable = true)
        val clientId = iae.clientId ?: options.clientId
        val tokenProofs =
            tokenEndpointProofs(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = iae.authorizationServerIssuer,
            ).getOrElse {
            return failed("oid4vci.token_proofs_failed", "wallet.interaction.error.oid4vci_token_proofs_failed", it, retryable = true)
        }
        val token =
            exchangeAuthorizationCodeWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = iae.authorizationServerIssuer,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                clientId = clientId,
                dpopProofJwt = tokenProofs.dpopProofJwt,
                clientAttestationJwt = tokenProofs.clientAttestationJwt,
                clientAttestationPopJwt = tokenProofs.clientAttestationPopJwt,
                clientAuthentication = tokenProofs.clientAuthentication,
            )
        if (token.isErr) {
            return failed("oid4vci.authorization_code_exchange_failed", "wallet.interaction.error.oid4vci_authorization_code_exchange_failed", token.error)
        }
        context.updateOid4vciState {
            it.copy(
                tokens =
                    Oid4vciPrivateSessionState.TokenLeg(
                        accessToken = token.value.accessToken,
                        refreshToken = token.value.refreshToken,
                        tokenEndpoint = tokenEndpoint,
                    ),
                iae = it.iae?.copy(pending = false),
            )
        }
        val cNonce = resolveProofNonce(resolvedOffer, token.value.cNonce)
        if (cNonce.isErr) {
            return failed("oid4vci.nonce_request_failed", "wallet.interaction.error.oid4vci_nonce_request_failed", cNonce.error)
        }
        val credentialConfigurationId = selectCredentialConfigurationId(options, state, resolvedOffer)
        val keyAttestationJwt =
            keyAttestationJwtIfRequired(
                context,
                resolvedOffer,
                credentialConfigurationId,
                options.signingKeyId,
                options.signingAlgorithm,
                cNonce.value,
                options.operationBinding,
            )
                .getOrElse { return failed("oid4vci.key_attestation_failed", "wallet.interaction.error.oid4vci_key_attestation_failed", it) }
        val proof =
            credentialRequestProofProvider.createProof(
                Oid4vciCredentialRequestProofRequest(
                    walletUnitId = context.walletUnitId,
                    operationBinding = options.operationBinding,
                    issuerUrl = resolvedOffer.offer.credentialIssuer,
                    cNonce = cNonce.value,
                    signingKeyId = options.signingKeyId,
                    signingAlgorithm = options.signingAlgorithm,
                    clientId = clientId,
                    keyAttestationJwt = keyAttestationJwt,
                ),
            )
        if (proof.isErr) {
            return failed("oid4vci.proof_creation_failed", "wallet.interaction.error.oid4vci_proof_creation_failed", proof.error)
        }
        val credentialDpopProof = credentialEndpointDpopProof(context, state, resolvedOffer, options, token.value.accessToken).getOrElse {
            return failed("oid4vci.credential_dpop_proof_failed", "wallet.interaction.error.oid4vci_credential_dpop_proof_failed", it, retryable = true)
        }
        context.updateOid4vciState {
            it.copy(
                holderKeyAliases = listOf(options.signingKeyId),
                credentialConfigurationId = credentialConfigurationId,
            )
        }
        val credential =
            requestCredentialWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                accessToken = token.value.accessToken,
                dpopProofJwt = credentialDpopProof,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proof.value.proofs,
            )
        if (credential.isErr) {
            return failed("oid4vci.credential_request_failed", "wallet.interaction.error.oid4vci_credential_request_failed", credential.error)
        }
        return handleCredentialResponse(context, state, resolvedOffer, credential.value)
    }

    private fun selectCredentialConfigurationId(
        options: Oid4vciHolderIssuanceOptions,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
    ): String? =
        options.credentialConfigurationId
            ?: preferredCredentialConfigurationId(state.effectiveOfferedCredentialConfigurationIds(resolvedOffer), resolvedOffer)
            ?: preferredCredentialConfigurationId(resolvedOffer.offer.credentialConfigurationIds, resolvedOffer)

    private fun preferredCredentialConfigurationId(
        credentialConfigurationIds: List<String>,
        resolvedOffer: ResolvedCredentialOffer,
    ): String? =
        credentialConfigurationIds
            .firstOrNull { id -> resolvedOffer.issuerMetadata.credentialConfigurationsSupported[id]?.format == HAIP_PREFERRED_CREDENTIAL_FORMAT }
            ?: credentialConfigurationIds.firstOrNull()

    private fun WalletInteractionState.effectiveOfferedCredentialConfigurationIds(
        resolvedOffer: ResolvedCredentialOffer,
    ): List<String> =
        selectedCredentialConfigurationIds.ifEmpty {
            credentialOffer?.credentialConfigurationIds.orEmpty().ifEmpty { resolvedOffer.offer.credentialConfigurationIds }
        }

    private fun ResolvedCredentialOffer.authorizationDetails(): List<JsonElement> =
        offer.credentialConfigurationIds.map { id ->
            buildJsonObject {
                put("type", JsonPrimitive("openid_credential"))
                put("credential_configuration_id", JsonPrimitive(id))
            }
        }

    private suspend fun requestCredentialWithDpopNonceRetry(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        accessToken: String,
        dpopProofJwt: String?,
        credentialConfigurationId: String?,
        proofs: CredentialRequestProofs?,
    ): IdkResult<CredentialResponse, IdkError> {
        val initial =
            holder.requestCredential(
                credentialEndpoint = resolvedOffer.issuerMetadata.credentialEndpoint,
                accessToken = accessToken,
                dpopProofJwt = dpopProofJwt,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proofs,
            )
        val nonce = initial.errorDpopNonceOrNull() ?: return initial
        val retryDpop =
            credentialEndpointDpopProof(context, state, resolvedOffer, options, accessToken, nonce)
                .getOrElse { return Err(it) }
                ?: return initial
        return holder.requestCredential(
            credentialEndpoint = resolvedOffer.issuerMetadata.credentialEndpoint,
            accessToken = accessToken,
            dpopProofJwt = retryDpop,
            credentialConfigurationId = credentialConfigurationId,
            proofs = proofs,
        )
    }

    private suspend fun exchangeAuthorizationCodeAndRequestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        callback: String,
    ): Oid4vciIssuanceExecutionResult {
        val sessionState = context.oid4vciState()
        val code =
            callback.parameter("code")
                ?: return failed("oid4vci.authorization_code_missing", "wallet.interaction.error.oid4vci_authorization_code_missing")
        val expectedState = sessionState.authorization?.state.orEmpty()
        val returnedState = callback.parameter("state").orEmpty()
        if (expectedState.isNotBlank() && returnedState != expectedState) {
            return failed("oid4vci.authorization_state_mismatch", "wallet.interaction.error.oid4vci_authorization_state_mismatch")
        }
        val authorization =
            sessionState.authorization
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing")
        val tokenEndpoint = authorization.tokenEndpoint
        val codeVerifier =
            authorization.codeVerifier
                ?: return failed("oid4vci.code_verifier_missing", "wallet.interaction.error.oid4vci_code_verifier_missing")
        val redirectUri =
            authorization.redirectUri ?: options.redirectUri
                ?: return failed("oid4vci.redirect_uri_missing", "wallet.interaction.error.oid4vci_redirect_uri_missing")
        val clientId = authorization.clientId ?: options.clientId
        val tokenProofs =
            tokenEndpointProofs(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorization.authorizationServerIssuer,
            ).getOrElse {
            return failed("oid4vci.token_proofs_failed", "wallet.interaction.error.oid4vci_token_proofs_failed", it)
        }
        val token =
            exchangeAuthorizationCodeWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorization.authorizationServerIssuer,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                clientId = clientId,
                dpopProofJwt = tokenProofs.dpopProofJwt,
                clientAttestationJwt = tokenProofs.clientAttestationJwt,
                clientAttestationPopJwt = tokenProofs.clientAttestationPopJwt,
                clientAuthentication = tokenProofs.clientAuthentication,
            )
        if (token.isErr) {
            return failed("oid4vci.authorization_code_exchange_failed", "wallet.interaction.error.oid4vci_authorization_code_exchange_failed", token.error)
        }
        context.updateOid4vciState {
            it.copy(
                tokens =
                    Oid4vciPrivateSessionState.TokenLeg(
                        accessToken = token.value.accessToken,
                        refreshToken = token.value.refreshToken,
                        tokenEndpoint = tokenEndpoint,
                    ),
            )
        }
        val cNonce = resolveProofNonce(resolvedOffer, token.value.cNonce)
        if (cNonce.isErr) {
            return failed("oid4vci.nonce_request_failed", "wallet.interaction.error.oid4vci_nonce_request_failed", cNonce.error)
        }
        val credentialConfigurationId = selectCredentialConfigurationId(options, state, resolvedOffer)
        val keyAttestationJwt =
            keyAttestationJwtIfRequired(
                context,
                resolvedOffer,
                credentialConfigurationId,
                options.signingKeyId,
                options.signingAlgorithm,
                cNonce.value,
                options.operationBinding,
            )
                .getOrElse { return failed("oid4vci.key_attestation_failed", "wallet.interaction.error.oid4vci_key_attestation_failed", it) }
        val proof =
            credentialRequestProofProvider.createProof(
                Oid4vciCredentialRequestProofRequest(
                    walletUnitId = context.walletUnitId,
                    operationBinding = options.operationBinding,
                    issuerUrl = resolvedOffer.offer.credentialIssuer,
                    cNonce = cNonce.value,
                    signingKeyId = options.signingKeyId,
                    signingAlgorithm = options.signingAlgorithm,
                    clientId = clientId,
                    keyAttestationJwt = keyAttestationJwt,
                ),
            )
        if (proof.isErr) {
            return failed("oid4vci.proof_creation_failed", "wallet.interaction.error.oid4vci_proof_creation_failed", proof.error)
        }
        val credentialDpopProof = credentialEndpointDpopProof(context, state, resolvedOffer, options, token.value.accessToken).getOrElse {
            return failed("oid4vci.credential_dpop_proof_failed", "wallet.interaction.error.oid4vci_credential_dpop_proof_failed", it)
        }
        context.updateOid4vciState {
            it.copy(
                holderKeyAliases = listOf(options.signingKeyId),
                credentialConfigurationId = credentialConfigurationId,
            )
        }
        val credential =
            requestCredentialWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                accessToken = token.value.accessToken,
                dpopProofJwt = credentialDpopProof,
                credentialConfigurationId = credentialConfigurationId,
                proofs = proof.value.proofs,
            )
        if (credential.isErr) {
            return failed("oid4vci.credential_request_failed", "wallet.interaction.error.oid4vci_credential_request_failed", credential.error)
        }
        return handleCredentialResponse(context, state, resolvedOffer, credential.value)
    }

    private suspend fun exchangePreAuthorizedCodeWithDpopNonceRetry(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        tokenEndpoint: String,
        authorizationServerIssuer: String?,
        preAuthorizedCode: String,
        txCode: String?,
        clientId: String?,
        redirectUri: String?,
        dpopProofJwt: String?,
        clientAttestationJwt: String?,
        clientAttestationPopJwt: String?,
        clientAuthentication: ClientAuthenticationConfig?,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        val initial =
            holder.exchangePreAuthorizedCode(
                tokenEndpoint = tokenEndpoint,
                preAuthorizedCode = preAuthorizedCode,
                txCode = txCode,
                clientId = clientId,
                redirectUri = redirectUri,
                dpopProofJwt = dpopProofJwt,
                clientAttestationJwt = clientAttestationJwt,
                clientAttestationPopJwt = clientAttestationPopJwt,
                clientAuthentication = clientAuthentication,
        )
        val nonce = initial.errorDpopNonceOrNull() ?: return initial
        val retryProofs =
            tokenEndpointProofs(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorizationServerIssuer,
                nonce = nonce,
            ).getOrElse { return Err(it) }
        val retryDpop = retryProofs.dpopProofJwt ?: return initial
        return holder.exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthorizedCode,
            txCode = txCode,
            clientId = clientId,
            redirectUri = redirectUri,
            dpopProofJwt = retryDpop,
            clientAttestationJwt = retryProofs.clientAttestationJwt,
            clientAttestationPopJwt = retryProofs.clientAttestationPopJwt,
            clientAuthentication = retryProofs.clientAuthentication,
        )
    }

    private suspend fun exchangeAuthorizationCodeWithDpopNonceRetry(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        tokenEndpoint: String,
        authorizationServerIssuer: String?,
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String?,
        dpopProofJwt: String?,
        clientAttestationJwt: String?,
        clientAttestationPopJwt: String?,
        clientAuthentication: ClientAuthenticationConfig?,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        val initial =
            holder.exchangeAuthorizationCode(
                tokenEndpoint = tokenEndpoint,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = redirectUri,
                clientId = clientId,
                dpopProofJwt = dpopProofJwt,
                clientAttestationJwt = clientAttestationJwt,
                clientAttestationPopJwt = clientAttestationPopJwt,
                clientAuthentication = clientAuthentication,
        )
        val nonce = initial.errorDpopNonceOrNull() ?: return initial
        val retryProofs =
            tokenEndpointProofs(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorizationServerIssuer,
                nonce = nonce,
            ).getOrElse { return Err(it) }
        val retryDpop = retryProofs.dpopProofJwt ?: return initial
        return holder.exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = code,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri,
            clientId = clientId,
            dpopProofJwt = retryDpop,
            clientAttestationJwt = retryProofs.clientAttestationJwt,
            clientAttestationPopJwt = retryProofs.clientAttestationPopJwt,
            clientAuthentication = retryProofs.clientAuthentication,
        )
    }

    /**
     * Mirrors [exchangeAuthorizationCodeWithDpopNonceRetry]'s shape exactly (initial attempt ->
     * `use_dpop_nonce` detection -> proof regeneration with the server-supplied nonce -> single
     * retry), but drives [refreshTokenGrantProvider] instead of a holder-service command, since no
     * OID4VCI holder command exposes the `refresh_token` grant.
     */
    private suspend fun exchangeRefreshTokenWithDpopNonceRetry(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        tokenEndpoint: String,
        authorizationServerIssuer: String?,
        refreshToken: String,
    ): IdkResult<TokenResponseWithContext, IdkError> {
        val initialProofs =
            tokenEndpointProofs(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorizationServerIssuer,
            ).getOrElse { return Err(it) }
        val initial =
            refreshTokenGrantProvider.exchangeRefreshToken(
                Oid4vciRefreshTokenGrantRequest(
                    tokenEndpoint = tokenEndpoint,
                    refreshToken = refreshToken,
                    clientId = options.clientId,
                    dpopProofJwt = initialProofs.dpopProofJwt,
                    clientAttestationJwt = initialProofs.clientAttestationJwt,
                    clientAttestationPopJwt = initialProofs.clientAttestationPopJwt,
                    clientAuthentication = initialProofs.clientAuthentication,
                ),
            )
        val nonce = initial.errorDpopNonceOrNull() ?: return initial
        val retryProofs =
            tokenEndpointProofs(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorizationServerIssuer,
                nonce = nonce,
            ).getOrElse { return Err(it) }
        val retryDpop = retryProofs.dpopProofJwt ?: return initial
        return refreshTokenGrantProvider.exchangeRefreshToken(
            Oid4vciRefreshTokenGrantRequest(
                tokenEndpoint = tokenEndpoint,
                refreshToken = refreshToken,
                clientId = options.clientId,
                dpopProofJwt = retryDpop,
                clientAttestationJwt = retryProofs.clientAttestationJwt,
                clientAttestationPopJwt = retryProofs.clientAttestationPopJwt,
                clientAuthentication = retryProofs.clientAuthentication,
            ),
        )
    }

    private suspend fun tokenEndpointDpopProof(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        tokenEndpoint: String,
        nonce: String,
    ): IdkResult<String?, IdkError> =
        tokenEndpointProofsProvider.dpopProof(
            Oid4vciDpopProofRequest(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                httpMethod = "POST",
                httpUrl = tokenEndpoint,
                nonce = nonce,
            ),
        )

    private fun IdkResult<*, IdkError>.errorDpopNonceOrNull(): String? {
        if (!isErr || error.code != "use_dpop_nonce") {
            return null
        }
        return error.meta["dpop_nonce"] as? String
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

    private suspend fun tokenEndpointProofs(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        tokenEndpoint: String,
        authorizationServerIssuer: String?,
        nonce: String? = null,
    ): IdkResult<Oid4vciTokenEndpointProofs, IdkError> =
        tokenEndpointProofsProvider.proofs(
            Oid4vciTokenEndpointProofRequest(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                tokenEndpoint = tokenEndpoint,
                authorizationServerIssuer = authorizationServerIssuer,
                nonce = nonce,
            ),
        )

    /**
     * KA-on-demand: when the resolved credential configuration's `jwt` proof type declares
     * `key_attestations_required`, obtains a key attestation for the credential-request signing
     * key via [keyAttestationProvider] and returns its compact JWT for attachment to the proof's
     * `key_attestation` header (OID4VCI 1.0 Section 7.2/11.2.3, TS 119 472-3). Returns `null` (no
     * attachment) when the configuration does not require one - the wallet never attaches a KA
     * uninvited.
     */
    private suspend fun keyAttestationJwtIfRequired(
        context: WalletInteractionContext,
        resolvedOffer: ResolvedCredentialOffer,
        credentialConfigurationId: String?,
        signingKeyId: String,
        signingAlgorithm: String,
        cNonce: String?,
        operationBinding: String?,
    ): IdkResult<String?, IdkError> {
        val requirement =
            credentialConfigurationId
                ?.let { resolvedOffer.issuerMetadata.credentialConfigurationsSupported[it] }
                ?.proofTypesSupported
                ?.get(JWT_PROOF_TYPE)
                ?.keyAttestationsRequired
                ?: return Ok(null)
        val attendedOperationBinding =
            operationBinding?.takeIf { it.isNotBlank() }
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Credential configuration '$credentialConfigurationId' requires a key attestation, but the attended operation binding is missing",
                    ),
                )
        val nonce =
            cNonce
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Credential configuration '$credentialConfigurationId' requires a key attestation, but no proof nonce was issued for this credential request",
                    ),
                )
        val attestationJwt =
            keyAttestationProvider
                .attest(
                    Oid4vciKeyAttestationRequest(
                        walletUnitId = context.walletUnitId,
                        operationBinding = attendedOperationBinding,
                        signingKeyId = signingKeyId,
                        signingAlgorithm = signingAlgorithm,
                        audience = resolvedOffer.offer.credentialIssuer,
                        nonce = nonce,
                        requirement = requirement,
                    ),
                ).getOrElse { return Err(it) }
        return Ok(attestationJwt)
    }

    private suspend fun credentialEndpointDpopProof(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        accessToken: String,
        nonce: String? = null,
    ): IdkResult<String?, IdkError> =
        tokenEndpointProofsProvider.dpopProof(
            Oid4vciDpopProofRequest(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                httpMethod = "POST",
                httpUrl = resolvedOffer.issuerMetadata.credentialEndpoint,
                nonce = nonce,
                accessToken = accessToken,
            ),
        )

    private suspend fun handleCredentialResponse(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        credentialResponse: CredentialResponse,
    ): Oid4vciIssuanceExecutionResult {
        val notificationId = credentialResponse.notificationId
        val notificationEndpoint = resolvedOffer.issuerMetadata.notificationEndpoint
        if (!notificationId.isNullOrBlank() && !notificationEndpoint.isNullOrBlank()) {
            context.updateOid4vciState {
                it.copy(
                    notification =
                        Oid4vciPrivateSessionState.NotificationLeg(
                            notificationEndpoint = notificationEndpoint,
                            notificationId = notificationId,
                        ),
                )
            }
        }
        val transactionId = credentialResponse.transactionId
        if (transactionId != null) {
            val deferredCredentialEndpoint = resolvedOffer.issuerMetadata.deferredCredentialEndpoint
            // A blank transaction_id must not produce a deferred leg: retrieval would send an empty
            // id to the issuer instead of failing fast at the wallet.
            if (!deferredCredentialEndpoint.isNullOrBlank() && transactionId.isNotBlank()) {
                context.updateOid4vciState {
                    it.copy(
                        deferred =
                            Oid4vciPrivateSessionState.DeferredLeg(
                                deferredCredentialEndpoint = deferredCredentialEndpoint,
                                transactionId = transactionId,
                            ),
                    )
                }
            }
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
        val sessionState = context.oid4vciState()
        val notificationEndpoint = sessionState.notification?.notificationEndpoint.orEmpty()
        val accessToken = sessionState.tokens?.accessToken.orEmpty()
        val notificationId = sessionState.notification?.notificationId.orEmpty()
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
private const val HAIP_PREFERRED_CREDENTIAL_FORMAT: String = "dc+sd-jwt"
private const val JWT_PROOF_TYPE: String = "jwt"

private fun signatureAlgorithm(joseAlgorithm: String): SignatureAlgorithm =
    when (joseAlgorithm.uppercase()) {
        "ES256" -> SignatureAlgorithm.ECDSA_SHA256
        "ES384" -> SignatureAlgorithm.ECDSA_SHA384
        "ES512" -> SignatureAlgorithm.ECDSA_SHA512
        "EDDSA" -> SignatureAlgorithm.ED25519
        else -> SignatureAlgorithm.ECDSA_SHA256
    }

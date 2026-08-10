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
import com.sphereon.openid.oid4vci.holder.AuthorizationRequestResult
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.IaeHolderResult
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import com.sphereon.oauth2.common.command.PrivateKeyJwtAssertionAssembly
import com.sphereon.oauth2.common.command.PrivateKeyJwtAssertionAssemblyRequest
import com.sphereon.oauth2.common.model.ClientAssertion
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.crypto.core.jose.Jwk
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock

data class Oid4vciHolderIssuanceOptions(
    val signingKeyId: String,
    val operationBinding: String? = null,
    val signingAlgorithm: String = "ES256",
    val clientId: String? = null,
    val clientAuthenticationMethod: ClientAuthenticationMethod? = null,
    val dpopEnabled: Boolean = false,
    val redirectUri: String? = null,
    val credentialConfigurationId: String? = null,
    /** Number of credentials explicitly requested for this interaction. */
    val batchSize: Int = 1,
    val usePar: Boolean = false,
    val encryptCredentialRequest: Boolean = false,
    val iaeCodeVerifier: String? = null,
    val iaeCodeChallenge: String? = null,
    val iaeCodeChallengeMethod: String? = null,
    val haipTokenProofs: Oid4vciHaipTokenProofOptions? = null,
)

private data class CredentialRequestEncryptionSelection(
    val jwk: JsonObject,
    val alg: String,
    val enc: String,
)

/**
 * Namespaced launch attributes accepted by the OID4VCI wallet interaction API. These are
 * per-interaction protocol inputs, not process configuration. Composition roots may map them to
 * [Oid4vciHolderIssuanceOptions], while security-sensitive key ownership and attended-operation
 * authorization remain enforced by the wallet-unit/security-gate surfaces.
 */
object Oid4vciInteractionLaunchAttributes {
    const val SIGNING_ALGORITHM: String = "oid4vci.signing.algorithm"
    const val CLIENT_ID: String = "oid4vci.client.id"
    const val CLIENT_AUTHENTICATION_METHOD: String = "oid4vci.client.authentication.method"
    const val DPOP_ENABLED: String = "oid4vci.dpop.enabled"
    const val REDIRECT_URI: String = "oid4vci.redirect.uri"
    const val CREDENTIAL_CONFIGURATION_ID: String = "oid4vci.credential.configuration.id"
    const val CREDENTIAL_BATCH_SIZE: String = "oid4vci.credential.batch.size"
    const val USE_PAR: String = "oid4vci.use.par"
    const val ENCRYPT_CREDENTIAL_REQUEST: String = "oid4vci.credential.request.encrypt"
    const val IAE_CODE_VERIFIER: String = "oid4vci.iae.code.verifier"
    const val IAE_CODE_CHALLENGE: String = "oid4vci.iae.code.challenge"
    const val IAE_CODE_CHALLENGE_METHOD: String = "oid4vci.iae.code.challenge.method"
    const val HAIP_ENABLED: String = "oid4vci.haip.enabled"
    const val HAIP_WALLET_ACCOUNT_ID: String = "oid4vci.haip.wallet.account.id"
    const val HAIP_WALLET_NAME: String = "oid4vci.haip.wallet.name"
    const val HAIP_WALLET_VERSION: String = "oid4vci.haip.wallet.version"
    const val HAIP_CLIENT_INSTANCE_KEY_ALIAS: String = "oid4vci.haip.client.instance.key.alias"
    const val HAIP_CLIENT_INSTANCE_KEY_ALGORITHM: String = "oid4vci.haip.client.instance.key.algorithm"
    const val HAIP_ATTESTATION_SIGNER_ID: String = "oid4vci.haip.attestation.signer.id"
    const val HAIP_ATTESTATION_ISSUER: String = "oid4vci.haip.attestation.issuer"
    const val HAIP_ATTESTATION_PROVIDER_ID: String = "oid4vci.haip.attestation.provider.id"
    const val HAIP_ATTESTATION_KEY_ID: String = "oid4vci.haip.attestation.key.id"
    const val HAIP_ATTESTATION_SIGNING_ALGORITHM: String = "oid4vci.haip.attestation.signing.algorithm"
    const val HAIP_ATTESTATION_SIGNER_PROFILE: String = "oid4vci.haip.attestation.signer.profile"
    const val HAIP_ATTESTATION_X5C: String = "oid4vci.haip.attestation.x5c"
}

/**
 * Applies the non-secret, per-interaction OID4VCI launch attributes supplied by the wallet client.
 * These values travel with [WalletInteractionInput] and survive session restoration; they are not
 * process configuration. Key ownership and attended-operation authorization deliberately remain
 * outside this mapping.
 */
fun Oid4vciHolderIssuanceOptions.withLaunchAttributes(attributes: Map<String, String>): Oid4vciHolderIssuanceOptions {
    fun value(name: String): String? = attributes[name]?.trim()?.takeIf(String::isNotEmpty)

    fun boolean(
        name: String,
        fallback: Boolean,
    ): Boolean =
        value(name)?.let { raw ->
            when (raw.lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("oid4vci.launch_attribute_boolean_invalid:$name")
            }
        } ?: fallback

    fun positiveInteger(
        name: String,
        fallback: Int,
    ): Int =
        value(name)?.let { raw ->
            raw.toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("oid4vci.launch_attribute_positive_integer_invalid:$name")
        } ?: fallback

    return copy(
        signingAlgorithm = value(Oid4vciInteractionLaunchAttributes.SIGNING_ALGORITHM) ?: signingAlgorithm,
        clientId = value(Oid4vciInteractionLaunchAttributes.CLIENT_ID) ?: clientId,
        clientAuthenticationMethod =
            value(Oid4vciInteractionLaunchAttributes.CLIENT_AUTHENTICATION_METHOD)?.let { method ->
                ClientAuthenticationMethod.fromValue(method)
                    ?: throw IllegalArgumentException("oid4vci.launch_attribute_client_authentication_method_invalid:$method")
            } ?: clientAuthenticationMethod,
        dpopEnabled = boolean(Oid4vciInteractionLaunchAttributes.DPOP_ENABLED, dpopEnabled),
        redirectUri = value(Oid4vciInteractionLaunchAttributes.REDIRECT_URI) ?: redirectUri,
        credentialConfigurationId =
            value(Oid4vciInteractionLaunchAttributes.CREDENTIAL_CONFIGURATION_ID) ?: credentialConfigurationId,
        batchSize = positiveInteger(Oid4vciInteractionLaunchAttributes.CREDENTIAL_BATCH_SIZE, batchSize),
        usePar = boolean(Oid4vciInteractionLaunchAttributes.USE_PAR, usePar),
        encryptCredentialRequest =
            boolean(Oid4vciInteractionLaunchAttributes.ENCRYPT_CREDENTIAL_REQUEST, encryptCredentialRequest),
        iaeCodeVerifier = value(Oid4vciInteractionLaunchAttributes.IAE_CODE_VERIFIER) ?: iaeCodeVerifier,
        iaeCodeChallenge = value(Oid4vciInteractionLaunchAttributes.IAE_CODE_CHALLENGE) ?: iaeCodeChallenge,
        iaeCodeChallengeMethod =
            value(Oid4vciInteractionLaunchAttributes.IAE_CODE_CHALLENGE_METHOD) ?: iaeCodeChallengeMethod,
    )
}

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
    private val privateKeyJwtAssertionAssembly: PrivateKeyJwtAssertionAssembly,
) : Oid4vciTokenEndpointProofsProvider {
    override suspend fun dpopProof(request: Oid4vciDpopProofRequest): IdkResult<String?, IdkError> {
        val haip = request.options.haipTokenProofs
        if (haip == null && !request.options.dpopEnabled) return Ok(null)
        val clientInstanceKey =
            secureComponentCryptoSurface
                .ensureKey(
                    walletUnitId = haip?.walletUnitId ?: request.context.walletUnitId,
                    usage = if (haip == null) SecureComponentUsage.OAUTH_CLIENT_AUTHENTICATION else SecureComponentUsage.WALLET_ATTESTATION,
                    algorithm = signatureAlgorithm(haip?.clientInstanceKeyAlgorithm ?: "ES256"),
                    keyAlias = haip?.clientInstanceKeyAlias,
                ).getOrElse { return Err(it) }
        val dpop =
            secureComponentCryptoSurface
                .createDpopProof(
                    WscaDpopProofRequest(
                        walletUnitId = haip?.walletUnitId ?: request.context.walletUnitId,
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
        val haip = request.options.haipTokenProofs
        if (haip == null) return finalProfileProofs(request)
        val clientId =
            request.options.clientId
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "HAIP token proofs require an OID4VCI client_id"))
        val authorizationServerIssuer =
            request.authorizationServerIssuer
                ?.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "HAIP token proofs require the authorization server issuer"))
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
                        audience = authorizationServerIssuer,
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

    private suspend fun finalProfileProofs(request: Oid4vciTokenEndpointProofRequest): IdkResult<Oid4vciTokenEndpointProofs, IdkError> {
        val clientAuthenticationMethod = request.options.clientAuthenticationMethod
        if (clientAuthenticationMethod != ClientAuthenticationMethod.PRIVATE_KEY_JWT && !request.options.dpopEnabled) {
            return Ok(Oid4vciTokenEndpointProofs())
        }
        val clientId =
            request.options.clientId?.takeIf(String::isNotBlank)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VCI client authentication requires client_id"))
        val keyRef =
            secureComponentCryptoSurface
                .ensureKey(
                    walletUnitId = request.context.walletUnitId,
                    usage = SecureComponentUsage.OAUTH_CLIENT_AUTHENTICATION,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
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
        if (clientAuthenticationMethod != ClientAuthenticationMethod.PRIVATE_KEY_JWT) {
            return Ok(Oid4vciTokenEndpointProofs(dpopProofJwt = dpop))
        }
        val audience =
            request.authorizationServerIssuer?.takeIf(String::isNotBlank)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "private_key_jwt requires the authorization server issuer audience"))
        val publicJwk =
            try {
                Json.decodeFromString(
                    Jwk.serializer(),
                    keyRef.publicKeyJwk
                        ?: return Err(IdkError.INVALID_STATE(message = "OAuth client key omitted its public JWK")),
                )
            } catch (expected: Exception) {
                return Err(IdkError.INVALID_STATE(message = "OAuth client key public JWK is invalid", throwable = expected))
            }
        val assembled =
            try {
                privateKeyJwtAssertionAssembly.assemble(
                    PrivateKeyJwtAssertionAssemblyRequest(clientId = clientId, audience = audience),
                    publicJwk,
                )
            } catch (expected: IllegalArgumentException) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = expected.message ?: "private_key_jwt assembly failed", throwable = expected))
            }
        val signature =
            secureComponentCryptoSurface
                .sign(
                    walletUnitId = request.context.walletUnitId,
                    keyRef = keyRef,
                    signingInput = assembled.signingInput,
                    operationBinding = requireOperationBinding(request.options),
                ).getOrElse { return Err(it) }
        val assertion = privateKeyJwtAssertionAssembly.finish(assembled, signature)
        return Ok(
            Oid4vciTokenEndpointProofs(
                dpopProofJwt = dpop,
                clientAuthentication =
                    ClientAuthenticationConfig.PrivateKeyJwt(
                        ClientAssertion(
                            clientId = clientId,
                            assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            assertion = assertion,
                        ),
                    ),
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
 * Kept as an injectable seam - mirroring [Oid4vciTokenEndpointProofsProvider] - so protocol
 * orchestration does not own key custody. Every production implementation must use [Wsca]; WSCA
 * selects the WSCD and the selected WSCD uses its configured KMS. There is no direct-KMS provider.
 */
interface Oid4vciCredentialRequestProofProvider {
    suspend fun createProof(request: Oid4vciCredentialRequestProofRequest): IdkResult<CreatedProof, IdkError>

    /**
     * Creates one proof per distinct holder key and combines them into the OID4VCI `proofs`
     * container. Implementations may override this for a native bulk operation; the default keeps
     * the same custody boundary as [createProof] and fails if providers return mixed proof types.
     */
    suspend fun createProofs(requests: List<Oid4vciCredentialRequestProofRequest>): IdkResult<CreatedProof, IdkError> {
        if (requests.isEmpty()) {
            return Err(
                IdkError.fromString(
                    code = "oid4vci.credential_proof_requests_empty",
                    message = "At least one OID4VCI credential proof request is required",
                ),
            )
        }
        var proofType: String? = null
        val proofValues = mutableListOf<JsonElement>()
        for (request in requests) {
            val created = createProof(request)
            if (created.isErr) return Err(created.error)
            val current = created.value.proofs
            if (proofType != null && current.proofType != proofType) {
                return Err(
                    IdkError.fromString(
                        code = "oid4vci.credential_proof_types_mixed",
                        message = "OID4VCI batch credential proofs must use one proof type",
                    ),
                )
            }
            proofType = current.proofType
            proofValues += current.proofValues
        }
        return Ok(CreatedProof(CredentialRequestProofs(proofType = proofType!!, proofValues = proofValues)))
    }
}

/**
 * Delegates to [Oid4vciHolderService]. The holder service itself signs through its injected [Wsca];
 * this adapter exists only for consumers of that command surface and is not an alternate custody
 * path.
 */
class HolderServiceOid4vciCredentialRequestProofProvider(
    private val holder: Oid4vciHolderService,
) : Oid4vciCredentialRequestProofProvider {
    override suspend fun createProof(request: Oid4vciCredentialRequestProofRequest): IdkResult<CreatedProof, IdkError> =
        holder.createCredentialRequestProof(
            walletUnitId = request.walletUnitId,
            operationBinding = request.operationBinding,
            issuerUrl = request.issuerUrl,
            cNonce = request.cNonce,
            signingKeyIds = listOf(request.signingKeyId),
            signingAlgorithm = request.signingAlgorithm,
            clientId = request.clientId,
            keyAttestationJwt = request.keyAttestationJwt,
        )
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
 * refresh. The production adapter delegates to [Oid4vciHolderService.exchangeRefreshToken], while
 * this seam keeps the interaction executor independently testable.
 */
fun interface Oid4vciRefreshTokenGrantProvider {
    suspend fun exchangeRefreshToken(request: Oid4vciRefreshTokenGrantRequest): IdkResult<TokenResponseWithContext, IdkError>

    companion object
}

private data class Oid4vciCredentialProofSelection(
    val createdProof: CreatedProof,
    val holderKeyAliases: List<String>,
)

class Oid4vciHolderIssuanceExecutor(
    private val holder: Oid4vciHolderService,
    private val optionsProvider: Oid4vciIssuanceOptionsProvider,
    private val credentialReceiver: Oid4vciCredentialResponseReceiver,
    private val nestedPresentationExecutor: WalletNestedPresentationExecutor,
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
        val challengedOptions = options.withAttestationChallenge(authorizationServer.value).getOrElse {
            return failed("oid4vci.attestation_challenge_failed", "wallet.interaction.error.oid4vci_attestation_challenge_failed", it)
        }
        val tokenEndpoint = authorizationServer.value.tokenEndpoint
        val tokenProofs = tokenEndpointProofs(context, state, resolved.value, challengedOptions, tokenEndpoint, authorizationServer.value.issuer).getOrElse {
            return failed("oid4vci.token_proofs_failed", "wallet.interaction.error.oid4vci_token_proofs_failed", it)
        }

        val token =
            exchangePreAuthorizedCodeWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolved.value,
                options = challengedOptions,
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
        val proofSelection =
            createCredentialRequestProofs(
                context,
                resolved.value,
                credentialConfigurationId,
                cNonce.value,
                options,
                options.clientId,
            )
                .getOrElse { return credentialRequestProofFailure(it) }

        val credentialDpopProof = credentialEndpointDpopProof(context, state, resolved.value, options, token.value.accessToken).getOrElse {
            return failed("oid4vci.credential_dpop_proof_failed", "wallet.interaction.error.oid4vci_credential_dpop_proof_failed", it)
        }
        context.updateOid4vciState {
            it.copy(
                holderKeyAliases = proofSelection.holderKeyAliases,
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
                proofs = proofSelection.createdProof.proofs,
                tokenAuthorizationDetails = token.value.authorizationDetails,
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
                options.haipTokenProofs,
            )
                .getOrElse { return failed("oid4vci.refresh_key_attestation_failed", "wallet.interaction.error.oid4vci_refresh_key_attestation_failed", it, retryable = true) }
        val proof =
            credentialRequestProofProvider.createProof(
                Oid4vciCredentialRequestProofRequest(
                    walletUnitId = context.walletUnitId,
                    operationBinding = options.operationBinding,
                    issuerUrl = effectiveMetadata.credentialIssuer,
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
                tokenAuthorizationDetails = token.authorizationDetails,
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
        val encryptCredentialRequest =
            context.attributes[Oid4vciInteractionLaunchAttributes.ENCRYPT_CREDENTIAL_REQUEST]
                ?.trim()
                ?.lowercase()
                ?.let { value ->
                    when (value) {
                        "true" -> true
                        "false" -> false
                        else -> return failed("oid4vci.options_unavailable", "wallet.interaction.error.oid4vci_options_unavailable", retryable = true)
                    }
                } ?: false
        val requestEncryption = credentialRequestEncryption(resolved.value, encryptCredentialRequest)
        val deferredDpopProof =
            deferredEndpointDpopProof(
                context = context,
                state = state,
                resolvedOffer = resolved.value,
                options = options,
                deferredCredentialEndpoint = deferred.deferredCredentialEndpoint,
                accessToken = accessToken,
            ).getOrElse {
                return failed("oid4vci.deferred_dpop_proof_failed", "wallet.interaction.error.oid4vci_deferred_dpop_proof_failed", it, retryable = true)
            }
        val credential =
            requestDeferredCredentialWithDpopNonceRetry(
                context = context,
                state = state,
                resolvedOffer = resolved.value,
                options = options,
                deferredCredentialEndpoint = deferred.deferredCredentialEndpoint,
                accessToken = accessToken,
                dpopProofJwt = deferredDpopProof,
                transactionId = deferred.transactionId,
                requestEncryptionJwk = requestEncryption?.jwk,
                requestEncryptionAlg = requestEncryption?.alg,
                requestEncryptionEnc = requestEncryption?.enc,
            )
        if (credential.isErr) {
            return failed("oid4vci.deferred_request_failed", "wallet.interaction.error.oid4vci_deferred_request_failed", credential.error, retryable = true)
        }
        return handleCredentialResponse(context, state, resolved.value, credential.value)
    }

    override suspend fun notifyCredentialAccepted(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vciIssuerNotificationResult = notifyIssuer(context, state, CredentialNotificationEvent.CREDENTIAL_ACCEPTED)

    override suspend fun notifyCredentialDeclined(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): Oid4vciIssuerNotificationResult = notifyIssuer(context, state, CredentialNotificationEvent.CREDENTIAL_FAILURE, eventDescription = "credential_declined")

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
        val challengedOptions = options.withAttestationChallenge(authorizationServer.value).getOrElse {
            return failed("oid4vci.attestation_challenge_failed", "wallet.interaction.error.oid4vci_attestation_challenge_failed", it)
        }
        authorizationServer.value.interactiveAuthorizationEndpoint?.takeIf { it.isNotBlank() }?.let { iaeEndpoint ->
            return initiateInteractiveAuthorization(context, state, resolvedOffer, challengedOptions, authorizationServer.value, iaeEndpoint)
        }
        val authorizationEndpoint =
            authorizationServer.value.authorizationEndpoint
                ?: return failed("oid4vci.authorization_endpoint_missing", "wallet.interaction.error.oid4vci_authorization_endpoint_missing")
        val usePar = challengedOptions.usePar || authorizationServer.value.metadata.requirePushedAuthorizationRequests == true
        val credentialConfigurationIds = state.effectiveOfferedCredentialConfigurationIds(resolvedOffer)
        val scope =
            credentialConfigurationIds
                .mapNotNull { resolvedOffer.issuerMetadata.credentialConfigurationsSupported[it]?.scope }
                .distinct()
                .joinToString(" ")
                .takeIf(String::isNotBlank)
        val authorizationRequest =
            if (usePar) {
                val parEndpoint =
                    authorizationServer.value.pushedAuthorizationRequestEndpoint
                        ?: return failed("oid4vci.par_endpoint_missing", "wallet.interaction.error.oid4vci_par_endpoint_missing")
                buildParAuthorizationRequestWithDpopNonceRetry(
                    context = context,
                    state = state,
                    resolvedOffer = resolvedOffer,
                    options = challengedOptions,
                    authorizationServer = authorizationServer.value,
                    authorizationEndpoint = authorizationEndpoint,
                    parEndpoint = parEndpoint,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    credentialConfigurationIds = credentialConfigurationIds,
                    scope = scope,
                    issuerState = authorizationGrant.issuerState,
                )
            } else {
                holder.buildAuthorizationRequest(
                    authorizationEndpoint = authorizationEndpoint,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    credentialConfigurationIds = credentialConfigurationIds,
                    scope = scope,
                    issuerState = authorizationGrant.issuerState,
                    locations = listOf(resolvedOffer.issuerMetadata.credentialIssuer),
                )
            }
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
                        attestationChallenge = challengedOptions.haipTokenProofs?.attestationChallenge,
                    ),
            )
        }
        return Oid4vciIssuanceExecutionResult.AuthorizationRequired(authorizationRequest.value.authorizationUrl)
    }

    private suspend fun buildParAuthorizationRequestWithDpopNonceRetry(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        authorizationServer: ResolvedAuthorizationServer,
        authorizationEndpoint: String,
        parEndpoint: String,
        clientId: String,
        redirectUri: String,
        credentialConfigurationIds: List<String>,
        scope: String?,
        issuerState: String?,
    ): IdkResult<AuthorizationRequestResult, IdkError> {
        suspend fun attempt(nonce: String?): IdkResult<AuthorizationRequestResult, IdkError> {
            val proofs =
                tokenEndpointProofs(
                    context = context,
                    state = state,
                    resolvedOffer = resolvedOffer,
                    options = options,
                    tokenEndpoint = parEndpoint,
                    authorizationServerIssuer = authorizationServer.issuer,
                    nonce = nonce,
                )
            if (proofs.isErr) return Err(proofs.error)
            return holder.buildAuthorizationRequest(
                authorizationEndpoint = authorizationEndpoint,
                clientId = clientId,
                redirectUri = redirectUri,
                credentialConfigurationIds = credentialConfigurationIds,
                scope = scope,
                issuerState = issuerState,
                usePar = true,
                parEndpoint = parEndpoint,
                locations = listOf(resolvedOffer.issuerMetadata.credentialIssuer),
                clientAuthentication = proofs.value.clientAuthentication,
                dpopProofJwt = proofs.value.dpopProofJwt,
                clientAttestationJwt = proofs.value.clientAttestationJwt,
                clientAttestationPopJwt = proofs.value.clientAttestationPopJwt,
            )
        }

        val first = attempt(nonce = null)
        if (first.isOk) return first
        val nonce = first.error.meta["dpop_nonce"] as? String
        return if (first.error.code == "use_dpop_nonce" && !nonce.isNullOrBlank()) {
            attempt(nonce)
        } else {
            first
        }
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
        val proofSelection =
            createCredentialRequestProofs(
                context,
                resolvedOffer,
                credentialConfigurationId,
                cNonce.value,
                options,
                clientId,
            )
                .getOrElse { return credentialRequestProofFailure(it) }
        val credentialDpopProof = credentialEndpointDpopProof(context, state, resolvedOffer, options, token.value.accessToken).getOrElse {
            return failed("oid4vci.credential_dpop_proof_failed", "wallet.interaction.error.oid4vci_credential_dpop_proof_failed", it, retryable = true)
        }
        context.updateOid4vciState {
            it.copy(
                holderKeyAliases = proofSelection.holderKeyAliases,
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
                proofs = proofSelection.createdProof.proofs,
                tokenAuthorizationDetails = token.value.authorizationDetails,
            )
        if (credential.isErr) {
            return failed("oid4vci.credential_request_failed", "wallet.interaction.error.oid4vci_credential_request_failed", credential.error)
        }
        return handleCredentialResponse(context, state, resolvedOffer, credential.value)
    }

    private suspend fun createCredentialRequestProofs(
        context: WalletInteractionContext,
        resolvedOffer: ResolvedCredentialOffer,
        credentialConfigurationId: String?,
        cNonce: String?,
        options: Oid4vciHolderIssuanceOptions,
        clientId: String?,
    ): IdkResult<Oid4vciCredentialProofSelection, IdkError> {
        val requestedBatchSize = options.batchSize
        if (requestedBatchSize <= 0) {
            return Err(
                IdkError.fromString(
                    code = "oid4vci.credential_batch_size_invalid",
                    message = "OID4VCI credential batch size must be greater than zero",
                ),
            )
        }
        if (requestedBatchSize > 1) {
            val advertisedBatchSize = resolvedOffer.issuerMetadata.batchCredentialIssuance?.batchSize
                ?: return Err(
                    IdkError.fromString(
                        code = "oid4vci.credential_batch_unsupported",
                        message = "The credential issuer does not advertise batch credential issuance",
                    ),
                )
            if (requestedBatchSize > advertisedBatchSize) {
                return Err(
                    IdkError.fromString(
                        code = "oid4vci.credential_batch_size_exceeded",
                        message = "Requested OID4VCI batch size $requestedBatchSize exceeds issuer maximum $advertisedBatchSize",
                    ),
                )
            }
        }

        val holderKeyAliases =
            (1..requestedBatchSize).map { index ->
                if (index == 1) options.signingKeyId else "${options.signingKeyId}-batch-$index"
            }
        val proofRequests = mutableListOf<Oid4vciCredentialRequestProofRequest>()
        for (holderKeyAlias in holderKeyAliases) {
            val keyAttestationJwt =
                keyAttestationJwtIfRequired(
                    context,
                    resolvedOffer,
                    credentialConfigurationId,
                    holderKeyAlias,
                    options.signingAlgorithm,
                    cNonce,
                    options.operationBinding,
                    options.haipTokenProofs,
                ).getOrElse { error ->
                    return Err(
                        IdkError
                            .fromString(
                                code = "oid4vci.key_attestation_failed",
                                message = error.message.defaultMessage,
                            ).addCause(error),
                    )
                }
            proofRequests +=
                Oid4vciCredentialRequestProofRequest(
                    walletUnitId = context.walletUnitId,
                    operationBinding = options.operationBinding,
                    // OID4VCI 1.0 Final Appendix F.1 requires `aud` to be the exact
                    // Credential Issuer Identifier. The resolved metadata value is authoritative;
                    // the launch/discovery URL can be a different URL spelling (for example a
                    // trailing slash) and MUST NOT be copied into the proof.
                    issuerUrl = resolvedOffer.issuerMetadata.credentialIssuer,
                    cNonce = cNonce,
                    signingKeyId = holderKeyAlias,
                    signingAlgorithm = options.signingAlgorithm,
                    clientId = clientId,
                    keyAttestationJwt = keyAttestationJwt,
                )
        }
        val createdProof = credentialRequestProofProvider.createProofs(proofRequests)
        if (createdProof.isErr) return Err(createdProof.error)
        if (createdProof.value.proofs.proofValues.size != requestedBatchSize) {
            return Err(
                IdkError.fromString(
                    code = "oid4vci.credential_batch_proof_count_mismatch",
                    message = "OID4VCI proof provider returned ${createdProof.value.proofs.proofValues.size} proofs for batch size $requestedBatchSize",
                ),
            )
        }
        return Ok(
            Oid4vciCredentialProofSelection(
                createdProof = createdProof.value,
                holderKeyAliases = holderKeyAliases,
            ),
        )
    }

    private fun credentialRequestProofFailure(error: IdkError): Oid4vciIssuanceExecutionResult =
        if (error.code == "oid4vci.key_attestation_failed") {
            failed(
                code = "oid4vci.key_attestation_failed",
                messageKey = "wallet.interaction.error.oid4vci_key_attestation_failed",
                error = error,
            )
        } else {
            failed(
                code = "oid4vci.proof_creation_failed",
                messageKey = "wallet.interaction.error.oid4vci_proof_creation_failed",
                error = error,
            )
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
        tokenAuthorizationDetails: List<JsonElement>? = null,
    ): IdkResult<CredentialResponse, IdkError> {
        // OID4VCI 1.0: when the token's authorization_details authorize credential_identifiers,
        // the credential request MUST reference one of them and MUST NOT carry
        // credential_configuration_id (the two are mutually exclusive on the wire).
        val credentialIdentifier = authorizedCredentialIdentifier(tokenAuthorizationDetails, credentialConfigurationId)
        val requestConfigurationId = credentialConfigurationId.takeIf { credentialIdentifier == null }
        val requestEncryption = credentialRequestEncryption(resolvedOffer, options.encryptCredentialRequest)
        val initial =
            holder.requestCredential(
                credentialEndpoint = resolvedOffer.issuerMetadata.credentialEndpoint,
                accessToken = accessToken,
                dpopProofJwt = dpopProofJwt,
                credentialConfigurationId = requestConfigurationId,
                credentialIdentifier = credentialIdentifier,
                proofs = proofs,
                requestEncryptionJwk = requestEncryption?.jwk,
                requestEncryptionAlg = requestEncryption?.alg,
                requestEncryptionEnc = requestEncryption?.enc,
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
            credentialConfigurationId = requestConfigurationId,
            credentialIdentifier = credentialIdentifier,
            proofs = proofs,
            requestEncryptionJwk = requestEncryption?.jwk,
            requestEncryptionAlg = requestEncryption?.alg,
            requestEncryptionEnc = requestEncryption?.enc,
        )
    }

    private suspend fun requestDeferredCredentialWithDpopNonceRetry(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        deferredCredentialEndpoint: String,
        accessToken: String,
        dpopProofJwt: String?,
        transactionId: String,
        requestEncryptionJwk: JsonObject?,
        requestEncryptionAlg: String?,
        requestEncryptionEnc: String?,
    ): IdkResult<CredentialResponse, IdkError> {
        val initial =
            holder.requestDeferredCredential(
                deferredCredentialEndpoint = deferredCredentialEndpoint,
                accessToken = accessToken,
                dpopProofJwt = dpopProofJwt,
                transactionId = transactionId,
                requestEncryptionJwk = requestEncryptionJwk,
                requestEncryptionAlg = requestEncryptionAlg,
                requestEncryptionEnc = requestEncryptionEnc,
            )
        val nonce = initial.errorDpopNonceOrNull() ?: return initial
        val retryDpop =
            deferredEndpointDpopProof(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = options,
                deferredCredentialEndpoint = deferredCredentialEndpoint,
                accessToken = accessToken,
                nonce = nonce,
            ).getOrElse { return Err(it) }
                ?: return initial
        return holder.requestDeferredCredential(
            deferredCredentialEndpoint = deferredCredentialEndpoint,
            accessToken = accessToken,
            dpopProofJwt = retryDpop,
            transactionId = transactionId,
            requestEncryptionJwk = requestEncryptionJwk,
            requestEncryptionAlg = requestEncryptionAlg,
            requestEncryptionEnc = requestEncryptionEnc,
        )
    }

    private fun credentialRequestEncryption(
        resolvedOffer: ResolvedCredentialOffer,
        requested: Boolean,
    ): CredentialRequestEncryptionSelection? {
        val metadata = resolvedOffer.issuerMetadata.credentialRequestEncryption ?: return null
        if (!requested && !metadata.encryptionRequired) return null
        val jwk =
            (metadata.jwks["keys"] as? JsonArray)
                ?.firstOrNull()
                ?.let { it as? JsonObject }
                ?: throw IllegalArgumentException("oid4vci.credential_request_encryption_key_missing")
        val alg =
            (jwk["alg"] as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("oid4vci.credential_request_encryption_alg_missing")
        val enc =
            metadata.encValuesSupported.firstOrNull()
                ?: throw IllegalArgumentException("oid4vci.credential_request_encryption_enc_missing")
        return CredentialRequestEncryptionSelection(jwk = jwk, alg = alg, enc = enc)
    }

    /**
     * The `openid_credential` authorization detail matching the requested configuration (or the
     * only authorized detail) supplies the exact credential identifier the issuer expects.
     */
    private fun authorizedCredentialIdentifier(
        tokenAuthorizationDetails: List<JsonElement>?,
        credentialConfigurationId: String?,
    ): String? {
        val details =
            tokenAuthorizationDetails
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "openid_credential" }
        if (details.isEmpty()) return null
        val matching =
            details.firstOrNull {
                (it["credential_configuration_id"] as? JsonPrimitive)?.contentOrNull == credentialConfigurationId
            } ?: details.singleOrNull()
        return (matching?.get("credential_identifiers") as? JsonArray)
            ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
    }

    private suspend fun exchangeAuthorizationCodeAndRequestCredential(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        callback: String,
    ): Oid4vciIssuanceExecutionResult {
        val sessionState = context.oid4vciState()
        val authorization =
            sessionState.authorization
                ?: return failed("oid4vci.token_endpoint_missing", "wallet.interaction.error.oid4vci_token_endpoint_missing")
        val authorizationResponse =
            holder.parseAndValidateAuthorizationResponse(
                callbackUrl = callback,
                expectedState = authorization.state?.takeIf(String::isNotBlank),
                expectedIssuer = authorization.authorizationServerIssuer,
                requireIssuer = options.haipTokenProofs != null,
            )
        if (authorizationResponse.isErr) {
            return failed(
                "oid4vci.authorization_response_invalid",
                "wallet.interaction.error.oid4vci_authorization_response_invalid",
                authorizationResponse.error,
            )
        }
        val code = authorizationResponse.value.code
        val tokenEndpoint = authorization.tokenEndpoint
        val codeVerifier =
            authorization.codeVerifier
                ?: return failed("oid4vci.code_verifier_missing", "wallet.interaction.error.oid4vci_code_verifier_missing")
        val redirectUri =
            authorization.redirectUri ?: options.redirectUri
                ?: return failed("oid4vci.redirect_uri_missing", "wallet.interaction.error.oid4vci_redirect_uri_missing")
        val clientId = authorization.clientId ?: options.clientId
        val challengedOptions =
            authorization.attestationChallenge?.let { challenge ->
                options.copy(haipTokenProofs = options.haipTokenProofs?.copy(attestationChallenge = challenge))
            } ?: options
        val tokenProofs =
            tokenEndpointProofs(
                context = context,
                state = state,
                resolvedOffer = resolvedOffer,
                options = challengedOptions,
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
                options = challengedOptions,
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
        val proofSelection =
            createCredentialRequestProofs(
                context,
                resolvedOffer,
                credentialConfigurationId,
                cNonce.value,
                options,
                clientId,
            )
                .getOrElse { return credentialRequestProofFailure(it) }
        val credentialDpopProof = credentialEndpointDpopProof(context, state, resolvedOffer, options, token.value.accessToken).getOrElse {
            return failed("oid4vci.credential_dpop_proof_failed", "wallet.interaction.error.oid4vci_credential_dpop_proof_failed", it)
        }
        context.updateOid4vciState {
            it.copy(
                holderKeyAliases = proofSelection.holderKeyAliases,
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
                proofs = proofSelection.createdProof.proofs,
                tokenAuthorizationDetails = token.value.authorizationDetails,
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

    private suspend fun Oid4vciHolderIssuanceOptions.withAttestationChallenge(
        authorizationServer: ResolvedAuthorizationServer,
    ): IdkResult<Oid4vciHolderIssuanceOptions, IdkError> {
        val haip = haipTokenProofs ?: return Ok(this)
        val endpoint = authorizationServer.metadata.challengeEndpoint?.takeIf(String::isNotBlank) ?: return Ok(this)
        val response = holder.requestAttestationChallenge(endpoint)
        return if (response.isErr) {
            Err(response.error)
        } else {
            Ok(copy(haipTokenProofs = haip.copy(attestationChallenge = response.value.attestationChallenge)))
        }
    }

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
        haipOptions: Oid4vciHaipTokenProofOptions?,
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
                        walletAccountId = haipOptions?.walletAccountId ?: context.walletUnitId,
                        operationBinding = attendedOperationBinding,
                        signingKeyId = signingKeyId,
                        signingAlgorithm = signingAlgorithm,
                        audience = resolvedOffer.issuerMetadata.credentialIssuer,
                        nonce = nonce,
                        signer = haipOptions?.signer,
                        evidence = haipOptions?.evidence.orEmpty(),
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

    private suspend fun deferredEndpointDpopProof(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        deferredCredentialEndpoint: String,
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
                httpUrl = deferredCredentialEndpoint,
                nonce = nonce,
                accessToken = accessToken,
            ),
        )

    private suspend fun notificationEndpointDpopProof(
        context: WalletInteractionContext,
        state: WalletInteractionState,
        resolvedOffer: ResolvedCredentialOffer,
        options: Oid4vciHolderIssuanceOptions,
        notificationEndpoint: String,
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
                httpUrl = notificationEndpoint,
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
            } catch (expected: Exception) {
                return failed(
                    code = "oid4vci.credential_receiver_failed",
                    messageKey = "wallet.interaction.error.oid4vci_credential_receiver_failed",
                    retryable = true,
                    arguments =
                        mapOf(
                            "causeType" to (expected::class.simpleName ?: "Exception"),
                            "cause" to expected.message.orEmpty(),
                        ),
                )
            }
        return Oid4vciIssuanceExecutionResult.Received(credentialPreview = previews)
    }

    private suspend fun notifyIssuer(
        context: WalletInteractionContext,
        state: WalletInteractionState,
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
        val rawOffer = sessionState.entryPointRaw?.takeIf { it.isNotBlank() } ?: return notificationFailed("credential_offer_missing")
        val offer = holder.parseCredentialOffer(rawOffer)
        if (offer.isErr) return notificationFailed(offer.error.code)
        val resolved = holder.resolveCredentialOffer(offer.value)
        if (resolved.isErr) return notificationFailed(resolved.error.code)
        val options =
            try {
                optionsProvider.options(context, state, resolved.value)
            } catch (_: Exception) {
                return notificationFailed("options_unavailable")
            }
        val initialDpop =
            notificationEndpointDpopProof(context, state, resolved.value, options, notificationEndpoint, accessToken)
                .getOrElse { return notificationFailed(it.code) }
        val initial =
            holder.sendNotification(
                notificationEndpoint = notificationEndpoint,
                accessToken = accessToken,
                dpopProofJwt = initialDpop,
                notificationId = notificationId,
                event = event,
                eventDescription = eventDescription,
            )
        val nonce = initial.errorDpopNonceOrNull()
        val result =
            if (nonce == null) {
                initial
            } else {
                val retryDpop =
                    notificationEndpointDpopProof(context, state, resolved.value, options, notificationEndpoint, accessToken, nonce)
                        .getOrElse { return notificationFailed(it.code) }
                        ?: return notificationFailed(initial.error.code)
                holder.sendNotification(
                    notificationEndpoint = notificationEndpoint,
                    accessToken = accessToken,
                    dpopProofJwt = retryDpop,
                    notificationId = notificationId,
                    event = event,
                    eventDescription = eventDescription,
                )
            }
        if (result.isErr) {
            return notificationFailed(result.error.code)
        }
        return Oid4vciIssuerNotificationResult.Sent
    }

    private fun notificationFailed(providerErrorCode: String): Oid4vciIssuerNotificationResult.Failed =
        Oid4vciIssuerNotificationResult.Failed(
            code = "oid4vci.notification_failed",
            messageKey = "wallet.interaction.error.oid4vci_notification_failed",
            arguments = mapOf("providerErrorCode" to providerErrorCode),
        )

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

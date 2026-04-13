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
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.impl.mapper.Oid4vciDesignMapper
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.CredentialResponseItem
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SdPolicy
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.impl.proof.ProofVerifier
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedProof
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStatus
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import com.sphereon.data.store.credential.design.model.SdPolicy as DesignSdPolicy

/**
 * Orchestrates credential issuance per OID4VCI 1.0 Section 8.
 *
 * Immediate issuance flow (Phase 4a):
 * 1. Validate access token via AS bridge
 * 2. Parse + validate credential request
 * 3. Resolve credential configuration
 * 4. Verify proof of possession → extract holder binding key, consume nonce
 * 5. Call CredentialAttributeContributor → additional attributes
 * 6. Merge attributes: preSeeded → accumulatedAttributes → oauthClaims → contributed
 * 7. Dispatch to CredentialFormatHandler
 * 8. Issue fresh nonce for next request
 * 9. Return CredentialResponse
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleCredentialRequestCommand>())
class HandleCredentialRequestCommandImpl(
    execution: SessionExecution,
    private val asBridge: Oid4vciAuthorizationServerBridge,
    private val proofVerifiers: Set<ProofVerifier>,
    private val nonceManager: NonceManager,
    private val formatHandlers: Set<CredentialFormatHandler>,
    private val attributeContributor: CredentialAttributeContributor,
    private val sessionStore: CredentialIssuanceSessionStore,
    private val deferredStore: DeferredCredentialStore,
    private val encryptor: CredentialResponseEncryptor,
    private val issuerConfigProvider: Oid4vciIssuerConfigProvider,
    /**
     * Optional credential-design service. When present, design metadata is resolved
     * at issuance time to populate [IssuanceContext.sdPolicies] and
     * [IssuanceContext.mandatoryClaims]. Null-safe — deployments without the
     * credential-design module work unchanged.
     */
    private val credentialDesignService: CredentialDesignService? = null,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<HandleCredentialRequestArgs, CredentialResponse>(
        commandId = HandleCredentialRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleCredentialRequestArgs>(),
        outputTypeToken = typeToken<CredentialResponse>(),
    ),
    HandleCredentialRequestCommand {
    override val commandId: String get() = HandleCredentialRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleCredentialRequestArgs

    override suspend fun doExecute(
        args: HandleCredentialRequestArgs,
        applyDuring: (HandleCredentialRequestArgs) -> HandleCredentialRequestArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        val result = doExecuteInternal(args, applyDuring)
        emitOutcome(args, result)
        return result
    }

    private suspend fun emitOutcome(
        args: HandleCredentialRequestArgs,
        result: IdkResult<CredentialResponse, IdkError>,
    ) {
        val request = args.credentialRequest
        val type = if (result.isOk) EventTypes.OID4VCI_CREDENTIAL_ISSUED else EventTypes.OID4VCI_CREDENTIAL_FAILED
        val category = if (result.isOk) EventCategories.OPERATION else EventCategories.ERROR
        val payload =
            buildJsonObject {
                request.credentialConfigurationId?.let { put("credentialConfigurationId", it) }
                request.credentialIdentifier?.let { put("credentialIdentifier", it) }
                request.format?.let { put("format", it.toString()) }
            }
        val es = eventService ?: return
        es.emit(
            es
                .eventBuilder()
                .type(type)
                .subsystem(EventSubsystems.OID4VCI)
                .category(category)
                .origin(HandleCredentialRequestCommand.COMMAND_ID)
                .payload(payload)
                .build(),
        )
    }

    private suspend fun doExecuteInternal(
        args: HandleCredentialRequestArgs,
        applyDuring: (HandleCredentialRequestArgs) -> HandleCredentialRequestArgs,
    ): IdkResult<CredentialResponse, IdkError> {
        val applied = applyDuring(args)
        val request = applied.credentialRequest

        // 1. Validate access token
        val tokenContext =
            asBridge
                .validateAccessToken(
                    ValidateAccessTokenArgs(accessToken = applied.accessToken, dpopProof = applied.dpopProof),
                ).getOrElse { return Err(it) }

        // 2. Validate credential request basics
        if (request.credentialConfigurationId == null && request.credentialIdentifier == null && request.format == null) {
            return Err(IdkError.fromString(code = "invalid_credential_request", message = "credential_configuration_id, credential_identifier, or format is required"))
        }
        // OID4VCI Section 8.2: credential_configuration_id and credential_identifier are mutually exclusive
        if (request.credentialConfigurationId != null && request.credentialIdentifier != null) {
            return Err(IdkError.fromString(code = "invalid_credential_request", message = "credential_configuration_id and credential_identifier are mutually exclusive"))
        }
        // 2b. Validate credential_identifier against token authorization_details (OID4VCI 1.1 Section 9.3.1.2)
        val requestedIdentifier = request.credentialIdentifier
        if (requestedIdentifier != null) {
            val tokenIdentifiers = tokenContext.credentialIdentifiers
            if (!tokenIdentifiers.isNullOrEmpty() && requestedIdentifier !in tokenIdentifiers) {
                return Err(
                    IdkError.fromString(
                        code = "UNKNOWN_CREDENTIAL_IDENTIFIER",
                        message = "Requested credential_identifier '$requestedIdentifier' is not in token authorization_details",
                    ),
                )
            }
        }

        // 3. Resolve credential configuration
        val configId =
            request.credentialConfigurationId
                ?: tokenContext.credentialConfigurationIds.firstOrNull()
                ?: return Err(IdkError.fromString(code = "unknown_credential_configuration", message = "Cannot resolve credential configuration"))

        // 4. Look up issuance session (pre-seeded attributes)
        val sessionLookupId =
            request.credentialIdentifier
                ?: tokenContext.credentialIdentifiers?.firstOrNull()
        val session =
            sessionLookupId?.let { id ->
                sessionStore.get(id).getOrElse { null }
            } ?: sessionStore.findByCredentialConfigurationId(configId).getOrElse { null }

        if (session != null && session.status.ordinal < IssuanceSessionStatus.CREDENTIAL_REQUESTED.ordinal) {
            sessionStore.update(session.copy(status = IssuanceSessionStatus.CREDENTIAL_REQUESTED))
        }

        val configuration =
            applied.credentialConfigurations[configId]
                ?: resolveMinimalConfiguration(request.format ?: CredentialFormat.SD_JWT_DC.value, request.vct, request.doctype)

        // 5. Verify proof of possession
        val expectedAudience = applied.issuerIdentifier ?: tokenContext.subject

        val proofs = request.proofs
        val isBatch = proofs != null && proofs.proofValues.size > 1

        val batchVerifiedProofs =
            if (proofs != null) {
                // §F.1: if the credential configuration declares proof_signing_alg_values_supported,
                // the proof's `alg` MUST match one of those values. Thread the list through to the
                // verifier so it can enforce the allowlist.
                val supportedAlgorithms =
                    configuration.proofTypesSupported?.get(proofs.proofType)?.proofSigningAlgValuesSupported
                val results =
                    coroutineScope {
                        proofs.proofValues
                            .map { proofValue ->
                                async {
                                    verifyProof(
                                        proofType = proofs.proofType,
                                        proofValue = proofValue,
                                        audience = expectedAudience,
                                        supportedAlgorithms = supportedAlgorithms,
                                    )
                                }
                            }.awaitAll()
                    }
                results.map { result -> result.getOrElse { return Err(it) } }
            } else {
                null
            }
        val contributedAttributes =
            if (session != null) {
                attributeContributor
                    .contribute(session, tokenContext, configId)
                    .getOrElse { return Err(it) }
            } else {
                emptyMap()
            }

        // 6. Merge attributes (priority: preSeeded → accumulated → contributed)
        val mergedAttributes = mutableMapOf<String, JsonElement>()
        session?.preSeededAttributes?.let { mergedAttributes.putAll(it) }
        session?.accumulatedAttributes?.let { mergedAttributes.putAll(it) }
        mergedAttributes.putAll(contributedAttributes)

        // 6b. Resolve credential design if a design service is available
        val tenantId = execution.sessionContext.context.tenant.tenantId
        val resolvedDesign =
            credentialDesignService?.let { service ->
                val designs =
                    service
                        .findCredentialDesignByBindingKey(
                            tenantId,
                            DesignBindingKey.CREDENTIAL_CONFIGURATION_ID,
                            configId,
                        ).getOrElse { emptyList() }

                designs.firstOrNull()?.let { designRecord ->
                    service
                        .resolveCredentialDesign(
                            tenantId,
                            ResolveCredentialDesignInput(designId = designRecord.id),
                        ).getOrNull()
                }
            }

        // Extract SD policies from resolved design (design SdPolicy → issuer SdPolicy)
        val sdPolicies: Map<String, SdPolicy> =
            resolvedDesign
                ?.design
                ?.claims
                ?.associate { claim ->
                    val pathStr = Oid4vciDesignMapper.claimPathString(claim)
                    val issuerPolicy =
                        when (claim.sdPolicy) {
                            DesignSdPolicy.ALWAYS -> SdPolicy.ALWAYS_DISCLOSED
                            DesignSdPolicy.ALLOWED -> SdPolicy.SELECTIVELY_DISCLOSABLE
                            DesignSdPolicy.NEVER -> SdPolicy.NEVER_DISCLOSED
                        }
                    pathStr to issuerPolicy
                } ?: emptyMap()

        // Extract mandatory claims from resolved design
        val mandatoryClaims: Set<String> =
            resolvedDesign
                ?.design
                ?.claims
                ?.filter { it.mandatory }
                ?.map { claim -> Oid4vciDesignMapper.claimPathString(claim) }
                ?.toSet() ?: emptySet()

        // 7. Resolve signing configuration for this credential type
        val signingConfig = issuerConfigProvider.credentialSigningConfigs[configId]

        // 8. Dispatch to format handler
        val handler =
            formatHandlers.firstOrNull { it.canHandle(request, configuration) }
                ?: return Err(IdkError.fromString(code = "unsupported_credential_format", message = "No format handler for format: ${configuration.format}"))

        // 8. Issue credential(s)
        val response =
            if (isBatch && batchVerifiedProofs != null) {
                // Batch issuance: issue one credential per proof in parallel
                val issuanceResults =
                    coroutineScope {
                        batchVerifiedProofs
                            .map { verifiedProof ->
                                async {
                                    val issuanceContext =
                                        IssuanceContext(
                                            subject = tokenContext.subject,
                                            clientId = tokenContext.clientId,
                                            issuerIdentifier = expectedAudience,
                                            credentialConfigurationId = configId,
                                            credentialConfiguration = configuration,
                                            holderBindingKey = verifiedProof.holderBindingKey,
                                            holderIdentifier = verifiedProof.holderIdentifier,
                                            holderKeyId = verifiedProof.keyId,
                                            attributes = mergedAttributes,
                                            sdPolicies = sdPolicies,
                                            mandatoryClaims = mandatoryClaims,
                                            signingKeyAlias = signingConfig?.signingKeyAlias,
                                            signingKeyMode = signingConfig?.signingKeyMode ?: SigningKeyMode.None,
                                            signingCertChainPath = signingConfig?.signingCertChainPath,
                                            issuanceClockSkewInSeconds = issuerConfigProvider.issuanceClockSkewInSeconds,
                                            expirationInDays = signingConfig?.expirationInDays,
                                        )
                                    handler.issueCredential(request, issuanceContext)
                                }
                            }.awaitAll()
                    }

                // Check for errors after all complete
                val envelopes =
                    issuanceResults.map { result ->
                        result.getOrElse { return Err(it) }
                    }

                // If any credential in the batch is deferred, the entire response is deferred
                if (envelopes.any { it.deferred }) {
                    val transactionId = Uuid.random().toString()
                    val now = Clock.System.now()
                    val entry =
                        DeferredCredentialEntry(
                            transactionId = transactionId,
                            issuanceSessionId = session?.sessionId ?: "",
                            credentialConfigurationId = configId,
                            status = DeferredCredentialStatus.PENDING,
                            retryAfterSeconds = 5,
                            createdAt = now.toEpochMilliseconds(),
                            expiresAt = now.plus(1.hours).toEpochMilliseconds(),
                        )
                    deferredStore.create(entry).getOrElse { return Err(it) }

                    return Ok(
                        CredentialResponse(
                            transactionId = transactionId,
                            interval = entry.retryAfterSeconds,
                        ),
                    )
                }

                var notificationId: String? = null
                val items =
                    envelopes.map { envelope ->
                        if (notificationId == null) {
                            notificationId = envelope.notificationId
                        }
                        CredentialResponseItem(credential = envelope.credential)
                    }

                val newNonce = nonceManager.issue().getOrElse { return Err(it) }
                CredentialResponse(
                    credentials = items,
                    notificationId = notificationId,
                    cNonce = newNonce.cNonce,
                    cNonceExpiresIn = newNonce.cNonceExpiresIn,
                )
            } else {
                // Single issuance (single-proof or no-proof)
                val verifiedProof = batchVerifiedProofs?.firstOrNull()

                val issuanceContext =
                    IssuanceContext(
                        subject = tokenContext.subject,
                        clientId = tokenContext.clientId,
                        issuerIdentifier = expectedAudience,
                        credentialConfigurationId = configId,
                        credentialConfiguration = configuration,
                        holderBindingKey = verifiedProof?.holderBindingKey,
                        holderIdentifier = verifiedProof?.holderIdentifier,
                        holderKeyId = verifiedProof?.keyId,
                        attributes = mergedAttributes,
                        sdPolicies = sdPolicies,
                        mandatoryClaims = mandatoryClaims,
                        signingKeyAlias = signingConfig?.signingKeyAlias,
                        signingKeyMode = signingConfig?.signingKeyMode ?: SigningKeyMode.None,
                        signingCertChainPath = signingConfig?.signingCertChainPath,
                    )

                val envelope =
                    handler
                        .issueCredential(request, issuanceContext)
                        .getOrElse { return Err(it) }

                if (envelope.deferred) {
                    val transactionId = Uuid.random().toString()
                    val now = Clock.System.now()
                    val entry =
                        DeferredCredentialEntry(
                            transactionId = transactionId,
                            issuanceSessionId = session?.sessionId ?: "",
                            credentialConfigurationId = configId,
                            status = DeferredCredentialStatus.PENDING,
                            retryAfterSeconds = 5,
                            createdAt = now.toEpochMilliseconds(),
                            expiresAt = now.plus(1.hours).toEpochMilliseconds(),
                        )
                    deferredStore.create(entry).getOrElse { return Err(it) }

                    return Ok(
                        CredentialResponse(
                            transactionId = transactionId,
                            interval = entry.retryAfterSeconds,
                        ),
                    )
                }

                val newNonce = nonceManager.issue().getOrElse { return Err(it) }
                CredentialResponse(
                    credential = envelope.credential,
                    notificationId = envelope.notificationId,
                    cNonce = newNonce.cNonce,
                    cNonceExpiresIn = newNonce.cNonceExpiresIn,
                )
            }

        // 9. Update session status to issued
        if (session != null) {
            sessionStore.update(session.copy(status = IssuanceSessionStatus.CREDENTIAL_ISSUED))
        }

        // 10. Encrypt response if requested
        return encryptor.encryptIfRequested(response, request.credentialResponseEncryption)
    }

    /**
     * Dispatch proof verification to the appropriate [ProofVerifier] based on proof type.
     *
     * [proofValue] is a [JsonElement] — for JWT/CWT/attestation this is a [JsonPrimitive] string,
     * for di_vp it is a [JsonObject].
     */
    private suspend fun verifyProof(
        proofType: String,
        proofValue: JsonElement,
        audience: String,
        supportedAlgorithms: List<String>? = null,
    ): IdkResult<VerifiedProof, IdkError> {
        val verifier =
            proofVerifiers.firstOrNull { it.supportedProofType == proofType }
                ?: return Err(IdkError.fromString(code = "UNSUPPORTED_PROOF_TYPE", message = "Proof type '$proofType' is not supported"))
        return verifier.verify(proofValue, audience, supportedAlgorithms)
    }

    /**
     * Minimal configuration for when no configuration store is available.
     * Phase 8 will resolve from the credential-design store.
     */
    private fun resolveMinimalConfiguration(
        format: String,
        vct: String?,
        doctype: String?,
    ): CredentialConfigurationSupported =
        CredentialConfigurationSupported(
            format = format,
            vct = vct,
            doctype = doctype,
        )
}

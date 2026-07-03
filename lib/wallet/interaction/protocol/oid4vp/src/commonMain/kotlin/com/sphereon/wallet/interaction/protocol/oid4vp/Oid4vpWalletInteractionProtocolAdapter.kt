/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.interaction.WalletClaimDescriptor
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCounterpartyTrustRequest
import com.sphereon.wallet.interaction.WalletCredentialRequirement
import com.sphereon.wallet.interaction.WalletCredentialSelectionRequest
import com.sphereon.wallet.interaction.WalletCredentialSetOption
import com.sphereon.wallet.interaction.WalletCredentialSetRequirement
import com.sphereon.wallet.interaction.WalletDisclosureSummary
import com.sphereon.wallet.interaction.WalletDisplayMessage
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionActionType
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionError
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolCapability
import com.sphereon.wallet.interaction.WalletProtocolExecutionRequest
import com.sphereon.wallet.interaction.WalletProtocolMatch
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Oid4vpWalletInteractionProtocolAdapter(
    private val holder: Oid4vpHolderService? = null,
    private val presentationExecutor: Oid4vpPresentationExecutor = Oid4vpPresentationExecutor.notConfigured,
    private val candidateResolver: Oid4vpCredentialCandidateResolver = Oid4vpCredentialCandidateResolver.none,
    private val securityContextResolver: Oid4vpPresentationSecurityContextResolver = Oid4vpPresentationSecurityContextResolver.none,
    private val walletConfigProvider: Oid4vpWalletConfigProvider = Oid4vpWalletConfigProvider.none,
    priority: Int = 90,
) : WalletInteractionProtocolAdapter {
    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = ADAPTER_ID,
            protocol = WalletProtocol.OID4VP,
            flowKinds = listOf(WalletInteractionFlowKind.CredentialPresent),
            priority = priority,
            labelKey = "wallet.interaction.adapter.oid4vp",
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch {
        val raw = entryPoint.raw ?: return WalletProtocolMatch.none
        val lower = raw.lowercase()
        return when {
            lower.startsWith("openid4vp://") -> WalletProtocolMatch.strong(capability.priority, "oid4vp.match.scheme")
            "response_type=vp_token" in lower || "dcql_query=" in lower -> WalletProtocolMatch.strong(capability.priority, "oid4vp.match.authorization_request")
            "request_uri=" in lower && ("openid" in lower || "vp" in lower) -> WalletProtocolMatch.weak(capability.priority, "oid4vp.match.request_uri_candidate")
            else -> WalletProtocolMatch.none
        }
    }

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        val launchState =
            context.baseState(
                status = WalletInteractionStatus.ResolvingEntryPoint,
                flowKind = WalletInteractionFlowKind.CredentialPresent,
                protocol = WalletProtocol.OID4VP,
                adapterId = capability.adapterId,
                entryPoint = entryPoint,
            )
        context.storePrivate(
            mapOf(
                "entry_point.raw" to entryPoint.raw.orEmpty(),
                "entry_point.fingerprint" to entryPoint.summary().fingerprint.orEmpty(),
            ),
        )
        val parsed = entryPoint.raw?.let { holder?.parseAuthorizationRequest(it, walletConfigProvider.walletConfig(context, launchState)) }
        if (parsed != null && parsed.isErr) {
            val state =
                context
                    .baseState(
                        status = WalletInteractionStatus.Failed,
                        flowKind = WalletInteractionFlowKind.CredentialPresent,
                        protocol = WalletProtocol.OID4VP,
                        adapterId = capability.adapterId,
                        entryPoint = entryPoint,
                    ).copy(
                        terminal = true,
                        error =
                            WalletInteractionError(
                                code = "oid4vp.request_parse_failed",
                                messageKey = "wallet.interaction.error.oid4vp_request_parse_failed",
                            ),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }

        val parsedRequest = parsed?.takeIf { it.isOk }?.value
        if (parsedRequest != null) {
            context.storePrivate(mapOf("authorization_request" to json.encodeToString(AuthorizationRequest.serializer(), parsedRequest)))
        }
        val resolved = parsedRequest?.let { holder?.resolveAuthorizationRequest(it) }?.takeIf { it.isOk }?.value
        val verifier =
            resolved?.verifierInfo?.let {
                WalletCounterpartySummary(
                    role = WalletCounterpartyRole.VERIFIER,
                    identifier = it.clientId,
                    displayName = it.displayName ?: it.clientId,
                    logoUri = it.logoUri,
                    metadata = mapOf("client_id_scheme" to it.clientIdScheme.name),
                )
            } ?: fallbackVerifier(entryPoint)

        val candidateCredentialIds =
            resolved?.let { candidateResolver.candidateCredentialIds(context, it) }.orEmpty()
        val requirements =
            resolved?.dcqlQuery?.toCredentialSelectionRequest(candidateCredentialIds)
                ?: WalletCredentialSelectionRequest(emptyList(), satisfiable = false)
        val requestedClaims = requirements.requirements.flatMap { requirement -> requirement.requiredClaimPaths.map { WalletClaimDescriptor(path = it) } }
        context.storePrivate(
            mapOf(
                "verifier_id" to verifier.identifier,
                "dcql_requirement_ids" to requirements.requirements.joinToString(",") { it.id },
            ),
        )
        val trust =
            context.trustResolver
                .resolve(
                    WalletCounterpartyTrustRequest(
                        counterparty = verifier,
                        protocol = WalletProtocol.OID4VP,
                    ),
                ).let { trust -> trust.copy(policyAction = context.trustPolicy.evaluate(trust).action) }
        if (trust.policyAction == WalletTrustPolicyAction.BLOCK) {
            val state =
                context
                    .baseState(
                        status = WalletInteractionStatus.Failed,
                        flowKind = WalletInteractionFlowKind.CredentialPresent,
                        protocol = WalletProtocol.OID4VP,
                        adapterId = capability.adapterId,
                        entryPoint = entryPoint,
                    ).copy(
                        counterparty = verifier,
                        trust = trust,
                        credentialSelection = requirements,
                        disclosure = WalletDisclosureSummary(verifier = verifier, requestedClaims = requestedClaims),
                        terminal = true,
                        error =
                            WalletInteractionError(
                                code = "oid4vp.verifier_blocked",
                                messageKey = "wallet.interaction.error.verifier_blocked",
                            ),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }
        val reviewStatus =
            if (trust.policyAction == WalletTrustPolicyAction.ASK_USER ||
                trust.policyAction == WalletTrustPolicyAction.FIRST_CONTACT_PROMPT
            ) {
                WalletInteractionStatus.TrustReview
            } else if (requirements.requirements.isEmpty()) {
                WalletInteractionStatus.DisclosureConsent
            } else {
                WalletInteractionStatus.CredentialSelection
            }
        val state =
            context
                .baseState(
                    status = reviewStatus,
                    flowKind = WalletInteractionFlowKind.CredentialPresent,
                    protocol = WalletProtocol.OID4VP,
                    adapterId = capability.adapterId,
                    entryPoint = entryPoint,
                ).copy(
                    counterparty = verifier,
                    trust = trust,
                    credentialSelection = requirements,
                    disclosure = WalletDisclosureSummary(verifier = verifier, requestedClaims = requestedClaims),
                )
        return WalletInteractionSession(context.sessionId, state)
    }

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState =
        when (action.type) {
            WalletInteractionActionType.DECLINE -> {
                sessionState.next(WalletInteractionStatus.Cancelled, terminal = true)
            }

            WalletInteractionActionType.SELECT_CREDENTIALS,
            WalletInteractionActionType.CONTINUE,
            -> {
                if (sessionState.status == WalletInteractionStatus.TrustReview) {
                    sessionState.copy(
                        status =
                            if (sessionState.credentialSelection
                                    ?.requirements
                                    .orEmpty()
                                    .isEmpty()
                            ) {
                                WalletInteractionStatus.DisclosureConsent
                            } else {
                                WalletInteractionStatus.CredentialSelection
                            },
                        revision = sessionState.revision + 1,
                    )
                } else {
                    val invalidSelection = sessionState.validateCredentialSelection(action)
                    if (invalidSelection != null) {
                        sessionState.copy(
                            status = WalletInteractionStatus.CredentialSelection,
                            revision = sessionState.revision + 1,
                            error = invalidSelection,
                        )
                    } else {
                        val selectedCredentialIds =
                            action.selection
                                ?.selectedCredentialIdsByRequirement
                                ?.values
                                .orEmpty()
                                .flatten()
                        val existingSelectedCredentialIds = sessionState.disclosure?.selectedCredentialIds.orEmpty()
                        if (selectedCredentialIds.isNotEmpty()) {
                            context.storePrivate(
                                mapOf(
                                    "selected_credential_ids" to selectedCredentialIds.joinToString(","),
                                    "selected_credential_ids_by_requirement" to json.encodeToString(action.selection?.selectedCredentialIdsByRequirement.orEmpty()),
                                ),
                            )
                        }
                        context.authorizePresentationSharing(
                            sessionState.copy(
                                disclosure =
                                    sessionState.disclosure?.copy(
                                        selectedCredentialIds = selectedCredentialIds.ifEmpty { existingSelectedCredentialIds },
                                    ),
                            ),
                        )
                    }
                }
            }

            WalletInteractionActionType.REVEAL_CLAIM_VALUES -> {
                sessionState.copy(
                    revision = sessionState.revision + 1,
                    disclosure = sessionState.disclosure?.copy(claimValuesRevealed = true),
                )
            }

            WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE -> {
                action.securityGrant?.let { grant -> context.storePrivate(mapOf("security_grant_id" to grant.grantId)) }
                context.applyPresentationResult(
                    sessionState.copy(
                        revision = sessionState.revision + 1,
                        securityChallenge = null,
                    ),
                )
            }

            else -> {
                if (sessionState.status == WalletInteractionStatus.Sharing) {
                    sessionState.next(WalletInteractionStatus.Completed, terminal = true)
                } else {
                    sessionState.next()
                }
            }
        }

    private suspend fun WalletInteractionContext.authorizePresentationSharing(sessionState: WalletInteractionState): WalletInteractionState {
        val securityContext = securityContextResolver.resolve(this, sessionState)
        val result =
            authorizeProtocolOperation(
                WalletProtocolExecutionRequest(
                    operationId = "${sessionState.sessionId.value}-share",
                    sessionId = sessionState.sessionId,
                    walletInstanceId = sessionState.walletInstanceId,
                    protocol = WalletProtocol.OID4VP,
                    operation = WalletSecurityOperation.PRESENTATION_SHARING,
                    audience = sessionState.counterparty?.identifier,
                    keyRef = securityContext.keyRef,
                    walletUnitId = securityContext.walletUnitId,
                    walletAccountId = securityContext.walletAccountId,
                    activationDecisionId = securityContext.activationDecisionId,
                    operationType = securityContext.operationType,
                    operationHash = securityContext.operationHash,
                    nonce = securityContext.nonce,
                ),
            )
        return when (result) {
            is WalletSecurityGateResult.Authorized -> {
                storePrivate(mapOf("security_grant_id" to result.grant.grantId))
                applyPresentationResult(sessionState.copy(revision = sessionState.revision + 1, securityChallenge = null))
            }

            is WalletSecurityGateResult.ChallengeRequired -> {
                sessionState.copy(
                    status = WalletInteractionStatus.SecurityUnlockRequired,
                    revision = sessionState.revision + 1,
                    securityChallenge = result.challenge,
                )
            }

            is WalletSecurityGateResult.Denied -> {
                sessionState.next(
                    status = WalletInteractionStatus.Failed,
                    terminal = true,
                    error =
                        WalletInteractionError(
                            code = "oid4vp.security_denied",
                            messageKey = result.reasonKey,
                            arguments = result.arguments,
                        ),
                )
            }
        }
    }

    private suspend fun WalletInteractionContext.applyPresentationResult(sessionState: WalletInteractionState): WalletInteractionState =
        when (val result = presentationExecutor.submitPresentation(this, sessionState)) {
            is Oid4vpPresentationExecutionResult.Submitted -> {
                sessionState.next(
                    status = WalletInteractionStatus.Completed,
                    terminal = true,
                    message = WalletDisplayMessage(titleKey = result.titleKey),
                )
            }

            is Oid4vpPresentationExecutionResult.Sharing -> {
                sessionState.copy(
                    status = WalletInteractionStatus.Sharing,
                    message = WalletDisplayMessage(titleKey = result.titleKey),
                    error = null,
                )
            }

            is Oid4vpPresentationExecutionResult.RedirectRequired -> {
                sessionState.copy(
                    status = WalletInteractionStatus.AuthorizationRequired,
                    authorizationUrl = result.redirectUri,
                    error = null,
                )
            }

            is Oid4vpPresentationExecutionResult.Failed -> {
                sessionState.next(
                    status = WalletInteractionStatus.Failed,
                    terminal = !result.retryable,
                    error =
                        WalletInteractionError(
                            code = result.code,
                            messageKey = result.messageKey,
                            retryable = result.retryable,
                            arguments = result.arguments,
                        ),
                )
            }
        }

    private fun WalletInteractionState.validateCredentialSelection(action: WalletInteractionAction): WalletInteractionError? {
        val request = credentialSelection ?: return null
        val requirements = request.requirements
        if (requirements.isEmpty()) return null

        fun error(
            code: String,
            arguments: Map<String, String> = emptyMap(),
        ): WalletInteractionError =
            WalletInteractionError(
                code = code,
                messageKey = "wallet.interaction.error.oid4vp_credential_selection_invalid",
                retryable = true,
                arguments = arguments,
            )

        if (!request.satisfiable) {
            return error("oid4vp.selection_unsatisfiable")
        }

        val selectedByRequirement = action.selection?.selectedCredentialIdsByRequirement.orEmpty()
        val requirementIds = requirements.map { it.id }.toSet()
        val unknownRequirementId = selectedByRequirement.keys.firstOrNull { it !in requirementIds }
        if (unknownRequirementId != null) {
            return error("oid4vp.selection_unknown_requirement", mapOf("requirementId" to unknownRequirementId))
        }

        requirements.forEach { requirement ->
            val selectedCredentialIds = selectedByRequirement[requirement.id].orEmpty()
            if (selectedCredentialIds.isEmpty()) {
                return error("oid4vp.selection_missing_requirement", mapOf("requirementId" to requirement.id))
            }
            if (!requirement.multipleAllowed && selectedCredentialIds.size > 1) {
                return error("oid4vp.selection_multiple_not_allowed", mapOf("requirementId" to requirement.id))
            }
            if (requirement.candidateCredentialIds.isNotEmpty()) {
                val unavailableCredentialId = selectedCredentialIds.firstOrNull { it !in requirement.candidateCredentialIds }
                if (unavailableCredentialId != null) {
                    return error(
                        "oid4vp.selection_credential_not_candidate",
                        mapOf(
                            "requirementId" to requirement.id,
                            "credentialId" to unavailableCredentialId,
                        ),
                    )
                }
            }
        }

        return null
    }

    private suspend fun WalletInteractionContext.storePrivate(values: Map<String, String>) {
        val sanitized = values.filterValues { it.isNotBlank() }
        if (sanitized.isEmpty()) return
        val existing = privateSessionStore.get(sessionId, capability.adapterId)?.values.orEmpty()
        privateSessionStore.put(
            sessionId,
            WalletInteractionPrivateSessionData(
                namespace = capability.adapterId,
                values = existing + sanitized,
            ),
        )
    }

    companion object {
        const val ADAPTER_ID: String = "oid4vp"
        internal val json: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

        fun walletStoreBacked(
            holder: Oid4vpHolderService,
            credentialStore: WalletCredentialStore,
            walletConfigProvider: Oid4vpWalletConfigProvider = Oid4vpWalletConfigProvider.none,
            responseMode: ResponseMode? = null,
            priority: Int = 90,
        ): Oid4vpWalletInteractionProtocolAdapter {
            val resolver = WalletStoreOid4vpCredentialResolver(credentialStore)
            return Oid4vpWalletInteractionProtocolAdapter(
                holder = holder,
                presentationExecutor =
                    Oid4vpHolderPresentationExecutor(
                        holder = holder,
                        selectedCredentialResolver = resolver,
                        walletConfigProvider = walletConfigProvider,
                        responseMode = responseMode,
                    ),
                candidateResolver = resolver,
                securityContextResolver = resolver,
                walletConfigProvider = walletConfigProvider,
                priority = priority,
            )
        }
    }
}

interface Oid4vpCredentialCandidateResolver {
    suspend fun candidateCredentialIds(
        context: WalletInteractionContext,
        resolvedRequest: ResolvedOid4vpRequest,
    ): Map<String, List<String>>

    companion object {
        val none: Oid4vpCredentialCandidateResolver =
            object : Oid4vpCredentialCandidateResolver {
                override suspend fun candidateCredentialIds(
                    context: WalletInteractionContext,
                    resolvedRequest: ResolvedOid4vpRequest,
                ): Map<String, List<String>> = emptyMap()
            }
    }
}

fun DcqlQuery.toCredentialSelectionRequest(candidateCredentialIds: Map<String, List<String>>): WalletCredentialSelectionRequest {
    val requirements =
        credentials.orEmpty().map { credential ->
            WalletCredentialRequirement(
                id = credential.id,
                format = credential.format,
                multipleAllowed = credential.multiple,
                requiredClaimPaths = credential.claims.orEmpty().map { it.path },
                candidateCredentialIds = candidateCredentialIds[credential.id].orEmpty(),
            )
        }
    val credentialSets =
        credential_sets.orEmpty().mapIndexed { index, set ->
            WalletCredentialSetRequirement(
                id = "dcql_credential_set_$index",
                required = set.required,
                options =
                    set.options.map { option ->
                        WalletCredentialSetOption(
                            requirementIds = option.credential_ids,
                            satisfiable = option.credential_ids.all { id -> candidateCredentialIds[id].orEmpty().isNotEmpty() },
                        )
                    },
            )
        }
    val credentialIdsInSets =
        credential_sets
            .orEmpty()
            .flatMap { set -> set.options.flatMap { option -> option.credential_ids } }
            .toSet()
    val standaloneRequirementIds = requirements.map { it.id }.filter { it !in credentialIdsInSets }
    val standaloneRequirementsSatisfied = standaloneRequirementIds.all { id -> candidateCredentialIds[id].orEmpty().isNotEmpty() }
    val requiredSetsSatisfied =
        credentialSets
            .filter { it.required }
            .all { set -> set.options.any { option -> option.satisfiable } }
    return WalletCredentialSelectionRequest(
        requirements = requirements,
        satisfiable = standaloneRequirementsSatisfied && requiredSetsSatisfied,
        credentialSets = credentialSets,
    )
}

private fun fallbackVerifier(entryPoint: WalletEntryPoint): WalletCounterpartySummary =
    WalletCounterpartySummary(
        role = WalletCounterpartyRole.VERIFIER,
        identifier = entryPoint.raw?.substringAfter("client_id=", missingDelimiterValue = "unknown-verifier")?.substringBefore("&") ?: "unknown-verifier",
    )

/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.interaction.WalletClaimDescriptor
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartyAssociationRequest
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
import com.sphereon.wallet.interaction.WalletInteractionActivitySummary
import com.sphereon.wallet.interaction.WalletInteractionActivityType
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionError
import com.sphereon.wallet.interaction.WalletInteractionFlowKind
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionData
import com.sphereon.wallet.interaction.WalletInteractionProtocolAdapter
import com.sphereon.wallet.interaction.WalletInteractionSession
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputPurpose
import com.sphereon.wallet.interaction.WalletProtocol
import com.sphereon.wallet.interaction.WalletProtocolCapability
import com.sphereon.wallet.interaction.WalletProtocolExecutionRequest
import com.sphereon.wallet.interaction.WalletProtocolMatch
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.WalletSecurityGrantValidation
import com.sphereon.wallet.interaction.validateFor
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

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
            lower.startsWith("openid4vp://") ||
                lower.startsWith("haip-vp://") ||
                lower.startsWith("mdoc-openid4vp://") ->
                WalletProtocolMatch.strong(capability.priority, "oid4vp.match.scheme")
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
                        error = parsed.error.toOid4vpRequestError(),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }

        val parsedRequest = parsed?.takeIf { it.isOk }?.value
        if (parsedRequest != null) {
            context.storePrivate(mapOf("authorization_request" to json.encodeToString(AuthorizationRequest.serializer(), parsedRequest)))
        }
        val resolvedResult = parsedRequest?.let { holder?.resolveAuthorizationRequest(it) }
        if (resolvedResult != null && resolvedResult.isErr) {
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
                                code = "oid4vp.request_resolve_failed",
                                messageKey = "wallet.interaction.error.oid4vp_request_resolve_failed",
                                arguments = mapOf("providerErrorCode" to resolvedResult.error.code),
                            ),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }
        val resolved = resolvedResult?.takeIf { it.isOk }?.value
        val interactionPurpose = context.resolveInteractionPurpose(parsedRequest, resolved)
        val activityType =
            when (interactionPurpose) {
                Oid4vpInteractionPurpose.LOGIN -> WalletInteractionActivityType.LOGIN
                Oid4vpInteractionPurpose.PRESENTATION -> WalletInteractionActivityType.CREDENTIAL_PRESENTATION
            }
        val interactionContext = interactionPurpose.metadataValue
        val unresolvedVerifier =
            resolved?.verifierInfo?.let {
                WalletCounterpartySummary(
                    role = WalletCounterpartyRole.VERIFIER,
                    identifier = it.clientId,
                    displayName = it.displayName ?: it.clientId,
                    logoUri = it.logoUri,
                    metadata =
                        buildMap {
                            put("client_id_scheme", it.clientIdScheme.name)
                            put("interaction_context", interactionContext)
                            put("activity_type", activityType.name)
                            (resolved.request.requestUri ?: parsedRequest.requestUri)?.let { uri -> put("request_uri", uri) }
                            resolved.request.responseUri?.let { uri -> put("response_uri", uri) }
                            it.trustRoot?.let { uri -> put("trust_root", uri) }
                        },
                )
            } ?: fallbackVerifier(entryPoint, activityType, interactionContext)
        val encounter = context.recordCounterpartyEncounter(WalletProtocol.OID4VP, unresolvedVerifier)
        val verifier = encounter.counterparty
        val activity =
            WalletInteractionActivitySummary(
                type = activityType,
                counterparty = verifier,
                metadata =
                    mapOf(
                        "interaction_context" to interactionContext,
                        "protocol" to WalletProtocol.OID4VP.name,
                    ),
            )

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
                "interaction_context" to interactionContext,
                "activity_type" to activityType.name,
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
                .copy(counterparty = verifier)
        if (!encounter.organizationCreated && trust.policyAction == WalletTrustPolicyAction.BLOCK) {
            val state =
                context
                    .baseState(
                        status = WalletInteractionStatus.Failed,
                        flowKind = WalletInteractionFlowKind.CredentialPresent,
                        protocol = WalletProtocol.OID4VP,
                        adapterId = capability.adapterId,
                        entryPoint = entryPoint,
                    ).copy(
                        activity = activity,
                        counterparty = verifier,
                        counterpartyEncounter = encounter,
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
            if (encounter.organizationCreated) {
                WalletInteractionStatus.CounterpartyNotice
            } else if (encounter.firstInteraction ||
                trust.policyAction == WalletTrustPolicyAction.WARN ||
                trust.policyAction == WalletTrustPolicyAction.ASK_USER ||
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
                    activity = activity,
                    counterparty = verifier,
                    counterpartyEncounter = encounter,
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

            WalletInteractionActionType.RESOLVE_COUNTERPARTY_CONTACT -> {
                context.resolveCounterpartyContact(sessionState, action)
            }

            WalletInteractionActionType.SELECT_CREDENTIALS,
            WalletInteractionActionType.CONTINUE,
            -> {
                if (sessionState.status == WalletInteractionStatus.CounterpartyNotice) {
                    sessionState.rejectAction("oid4vp.counterparty_resolution_required")
                } else if (sessionState.status == WalletInteractionStatus.TrustReview) {
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
                val grant = action.sensitiveInputRef?.let { context.sensitiveInputAuthority.consumeSecurityGrant(sessionState.sessionId, it) }
                val validation =
                    grant?.validateFor(
                        sessionState.securityChallenge,
                        sessionState.walletUnitId,
                        sessionState.counterparty?.identifier,
                        Clock.System.now().epochSeconds,
                    )
                if (grant == null || validation is WalletSecurityGrantValidation.Invalid) {
                    sessionState.next(
                        status = WalletInteractionStatus.Failed,
                        error = WalletInteractionError("oid4vp.security_grant_ref_invalid", "wallet.interaction.error.security_grant_ref_invalid", retryable = true),
                    )
                } else {
                    context.storePrivate(
                        mapOf(
                            "security_grant_id" to grant.grantId,
                            SECURITY_OPERATION_BINDING_PRIVATE_KEY to grant.evidence["operation_binding"].orEmpty(),
                        ),
                    )
                    context.applyPresentationResult(
                        sessionState.copy(revision = sessionState.revision + 1, securityChallenge = null),
                    )
                }
            }

            else -> {
                if (sessionState.status == WalletInteractionStatus.Sharing) {
                    sessionState.next(WalletInteractionStatus.Completed, terminal = true)
                } else {
                    sessionState.next()
                }
            }
        }

    private suspend fun WalletInteractionContext.resolveCounterpartyContact(
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState {
        if (sessionState.status != WalletInteractionStatus.CounterpartyNotice) {
            return sessionState.rejectAction("oid4vp.action_counterparty_resolution_not_allowed")
        }
        val encounter = sessionState.counterpartyEncounter
            ?: return sessionState.rejectAction("oid4vp.counterparty_encounter_missing")
        val decision = action.counterpartyAssociation
            ?: return sessionState.rejectAction("oid4vp.counterparty_association_missing")
        val resolvedEncounter =
            counterpartyEncounterRegistry.resolveAssociation(
                WalletCounterpartyAssociationRequest(
                    walletUnitId = sessionState.walletUnitId,
                    encounter = encounter,
                    decision = decision,
                ),
            )
        require(resolvedEncounter.resolved) { "wallet_counterparty_association_unresolved" }
        require(resolvedEncounter.associationCandidates.isEmpty()) { "wallet_counterparty_association_candidates_remaining" }
        require(resolvedEncounter.counterparty.role == encounter.counterparty.role) { "wallet_counterparty_association_role_changed" }
        require(resolvedEncounter.counterparty.identifier == encounter.counterparty.identifier) { "wallet_counterparty_association_identifier_changed" }

        val counterparty = resolvedEncounter.counterparty
        val trust =
            trustResolver
                .resolve(
                    WalletCounterpartyTrustRequest(
                        counterparty = counterparty,
                        protocol = WalletProtocol.OID4VP,
                    ),
                ).let { resolved -> resolved.copy(policyAction = trustPolicy.evaluate(resolved).action) }
                .copy(counterparty = counterparty)
        val activity = sessionState.activity?.copy(counterparty = counterparty)
        val disclosure = sessionState.disclosure?.copy(verifier = counterparty)
        if (trust.policyAction == WalletTrustPolicyAction.BLOCK) {
            return sessionState.copy(
                status = WalletInteractionStatus.Failed,
                revision = sessionState.revision + 1,
                activity = activity,
                counterparty = counterparty,
                counterpartyEncounter = resolvedEncounter,
                trust = trust,
                disclosure = disclosure,
                terminal = true,
                error =
                    WalletInteractionError(
                        code = "oid4vp.verifier_blocked",
                        messageKey = "wallet.interaction.error.verifier_blocked",
                    ),
            )
        }
        val nextStatus =
            if (resolvedEncounter.firstInteraction ||
                trust.policyAction == WalletTrustPolicyAction.WARN ||
                trust.policyAction == WalletTrustPolicyAction.ASK_USER ||
                trust.policyAction == WalletTrustPolicyAction.FIRST_CONTACT_PROMPT
            ) {
                WalletInteractionStatus.TrustReview
            } else if (sessionState.credentialSelection?.requirements.orEmpty().isEmpty()) {
                WalletInteractionStatus.DisclosureConsent
            } else {
                WalletInteractionStatus.CredentialSelection
            }
        return sessionState.copy(
            status = nextStatus,
            revision = sessionState.revision + 1,
            activity = activity,
            counterparty = counterparty,
            counterpartyEncounter = resolvedEncounter,
            trust = trust,
            disclosure = disclosure,
            error = null,
        )
    }

    private fun WalletInteractionState.rejectAction(code: String): WalletInteractionState =
        copy(
            revision = revision + 1,
            error =
                WalletInteractionError(
                    code = code,
                    messageKey = "wallet.interaction.error.action_not_allowed",
                    retryable = true,
                ),
        )

    private suspend fun WalletInteractionContext.authorizePresentationSharing(sessionState: WalletInteractionState): WalletInteractionState {
        val securityContext = securityContextResolver.resolve(this, sessionState)
        val result =
            authorizeProtocolOperation(
                WalletProtocolExecutionRequest(
                    operationId = "${sessionState.sessionId.value}-share",
                    sessionId = sessionState.sessionId,
                    sessionWalletUnitId = sessionState.walletUnitId,
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
                storePrivate(
                    mapOf(
                        "security_grant_id" to result.grant.grantId,
                        SECURITY_OPERATION_BINDING_PRIVATE_KEY to result.grant.evidence["operation_binding"].orEmpty(),
                    ),
                )
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
                val handoffRef =
                    sensitiveInputAuthority.register(
                        sessionId = sessionState.sessionId,
                        purpose = WalletInteractionSensitiveInputPurpose.PROTOCOL_REDIRECT_HANDOFF,
                        value = result.redirectUri,
                    )
                sessionState.copy(
                    status = WalletInteractionStatus.Completed,
                    revision = sessionState.revision + 1,
                    completionHandoffRef = handoffRef,
                    message = WalletDisplayMessage(titleKey = "wallet.interaction.oid4vp.shared"),
                    error = null,
                    terminal = true,
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
        const val SECURITY_OPERATION_BINDING_PRIVATE_KEY: String = "security_operation_binding"
        internal val json: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

        fun walletStoreBacked(
            holder: Oid4vpHolderService,
            credentialStore: WalletCredentialStore,
            walletConfigProvider: Oid4vpWalletConfigProvider = Oid4vpWalletConfigProvider.none,
            jarmOptionsProvider: Oid4vpJarmOptionsProvider = Oid4vpJarmOptionsProvider.none,
            responseMode: ResponseMode? = null,
            priority: Int = 90,
            sdJwtHolderBindingProvider: Oid4vpSdJwtHolderBindingProvider = Oid4vpSdJwtHolderBindingProvider.passthrough,
        ): Oid4vpWalletInteractionProtocolAdapter {
            val resolver = WalletStoreOid4vpCredentialResolver(credentialStore)
            return Oid4vpWalletInteractionProtocolAdapter(
                holder = holder,
                presentationExecutor =
                    Oid4vpHolderPresentationExecutor(
                        holder = holder,
                        selectedCredentialResolver = resolver,
                        walletConfigProvider = walletConfigProvider,
                        jarmOptionsProvider = jarmOptionsProvider,
                        responseMode = responseMode,
                        sdJwtHolderBindingProvider = sdJwtHolderBindingProvider,
                    ),
                candidateResolver = resolver,
                securityContextResolver = resolver,
                walletConfigProvider = walletConfigProvider,
                priority = priority,
            )
        }
    }
}

private fun IdkError.toOid4vpRequestError(): WalletInteractionError {
    val (publicCode, retryable) =
        when {
            code == "HTTP_410" -> "oid4vp.request_uri_expired" to false
            code == "HTTP_404" -> "oid4vp.request_uri_not_found" to false
            code == "HTTP_REQUEST_FAILED" || code == "HTTP_408" || code == "HTTP_429" || code.startsWith("HTTP_5") ->
                "oid4vp.request_uri_fetch_failed" to true
            code.startsWith("HTTP_4") -> "oid4vp.request_uri_rejected" to false
            else -> "oid4vp.request_parse_failed" to false
        }
    return WalletInteractionError(
        code = publicCode,
        messageKey = "wallet.interaction.error.oid4vp_request_parse_failed",
        retryable = retryable,
        arguments = mapOf("providerErrorCode" to code),
    )
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

private fun WalletInteractionContext.resolveInteractionPurpose(
    parsedRequest: AuthorizationRequest?,
    resolvedRequest: ResolvedOid4vpRequest?,
): Oid4vpInteractionPurpose =
    if (attributes.indicateLogin() || parsedRequest.indicatesLogin() || resolvedRequest?.request.indicatesLogin()) {
        Oid4vpInteractionPurpose.LOGIN
    } else {
        Oid4vpInteractionPurpose.PRESENTATION
    }

private enum class Oid4vpInteractionPurpose(
    val metadataValue: String,
) {
    PRESENTATION("presentation"),
    LOGIN("login"),
}

private fun Map<String, String>.indicateLogin(): Boolean =
    any { (key, value) ->
        key in loginPurposeMetadataKeys && value.indicatesLoginPurpose()
    }

private fun AuthorizationRequest?.indicatesLogin(): Boolean {
    if (this == null) return false
    if (prompt?.split(' ')?.any { it.equals("login", ignoreCase = true) } == true) return true
    return additionalParameters.any { (key, value) ->
        key in loginPurposeMetadataKeys && value.asString().indicatesLoginPurpose()
    }
}

private fun String?.indicatesLoginPurpose(): Boolean =
    when (this?.trim()?.lowercase()) {
        "login",
        "sign_in",
        "signin",
        "sign-in",
        "authentication",
        "auth",
        "credential_login",
        "credential-based-login",
        "credential_based_login",
        -> true

        else -> false
    }

private fun kotlinx.serialization.json.JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull ?: runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private val loginPurposeMetadataKeys =
    setOf(
        "purpose",
        "interaction_purpose",
        "wallet.interaction.purpose",
        "wallet_interaction_purpose",
        "presentation_purpose",
        "oid4vp.purpose",
        "activity_type",
        "activityType",
        "presentation_variant",
        "variant",
        "flow",
        "use_case",
        "context",
        "client_purpose",
    )

private fun fallbackVerifier(
    entryPoint: WalletEntryPoint,
    activityType: WalletInteractionActivityType,
    interactionContext: String,
): WalletCounterpartySummary =
    WalletCounterpartySummary(
        role = WalletCounterpartyRole.VERIFIER,
        identifier = entryPoint.raw?.substringAfter("client_id=", missingDelimiterValue = "unknown-verifier")?.substringBefore("&") ?: "unknown-verifier",
        metadata =
            mapOf(
                "interaction_context" to interactionContext,
                "activity_type" to activityType.name,
            ),
    )

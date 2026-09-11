/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.openid.oid4vci.common.model.AuthorizationCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.oauth2.client.util.extractQueryParameters
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletAttendedAuthorizationRegistry
import com.sphereon.wallet.interaction.WalletCounterpartyAssociationRequest
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.selfAssertedDisplayNameSource
import com.sphereon.wallet.interaction.WalletCounterpartyTrustRequest
import com.sphereon.wallet.interaction.WalletCredentialBranding
import com.sphereon.wallet.interaction.WalletCredentialOfferInfoDescriptor
import com.sphereon.wallet.interaction.WalletCredentialOfferSummary
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletDeferredRetrievalSummary
import com.sphereon.wallet.interaction.WalletDisplayMessage
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletEntryPointKind
import com.sphereon.wallet.interaction.WalletInteractionAction
import com.sphereon.wallet.interaction.WalletInteractionActionType
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionFailureCodes
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
import com.sphereon.wallet.interaction.WalletSecurityContextAttributes
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.WalletSecurityGrantValidation
import com.sphereon.wallet.interaction.validateFor
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationRequest
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResult
import com.sphereon.wallet.interaction.admitForIssuer
import com.sphereon.wallet.interaction.WalletTxCodeSpec
import com.sphereon.wallet.interaction.classifiedWalletInteractionError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class Oid4vciWalletInteractionProtocolAdapter(
    private val holder: Oid4vciHolderService? = null,
    private val issuanceExecutor: Oid4vciIssuanceExecutor,
    private val attendedAuthorizationRegistry: WalletAttendedAuthorizationRegistry = WalletAttendedAuthorizationRegistry.none,
    priority: Int = 100,
) : WalletInteractionProtocolAdapter {
    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = ADAPTER_ID,
            protocol = WalletProtocol.OID4VCI,
            flowKinds = listOf(WalletInteractionFlowKind.CredentialReceive),
            priority = priority,
            labelKey = "wallet.interaction.adapter.oid4vci",
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch {
        if (entryPoint.kind == WalletEntryPointKind.PARSED_OBJECT && entryPoint.parsedType == ISSUE_PARSED_TYPE) {
            return WalletProtocolMatch.strong(capability.priority, "oid4vci.match.wallet_initiated_issuance")
        }
        if (entryPoint.kind == WalletEntryPointKind.PARSED_OBJECT && entryPoint.parsedType == REFRESH_PARSED_TYPE) {
            return WalletProtocolMatch.strong(capability.priority, "oid4vci.match.credential_refresh")
        }
        if (entryPoint.kind == WalletEntryPointKind.PARSED_OBJECT && entryPoint.parsedType == DIGITAL_CREDENTIAL_PROTOCOL) {
            return WalletProtocolMatch.strong(capability.priority, "oid4vci.match.digital_credentials_api")
        }
        val raw = entryPoint.raw ?: return WalletProtocolMatch.none
        val lower = raw.lowercase()
        return when {
            lower.startsWith("openid-credential-offer://") -> {
                WalletProtocolMatch.strong(capability.priority, "oid4vci.match.credential_offer_uri")
            }

            "credential_offer=" in lower || "credential_offer_uri=" in lower -> {
                WalletProtocolMatch.strong(capability.priority, "oid4vci.match.credential_offer_parameter")
            }

            entryPoint.kind == WalletEntryPointKind.HTTPS_LINK && "credential" in lower && "offer" in lower -> {
                WalletProtocolMatch.weak(capability.priority, "oid4vci.match.https_credential_offer_candidate")
            }

            else -> {
                WalletProtocolMatch.none
            }
        }
    }

    override suspend fun start(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        if (entryPoint.kind == WalletEntryPointKind.PARSED_OBJECT && entryPoint.parsedType == REFRESH_PARSED_TYPE) {
            return startRefresh(context, entryPoint)
        }
        val walletInitiatedOffer =
            if (entryPoint.kind == WalletEntryPointKind.PARSED_OBJECT && entryPoint.parsedType == ISSUE_PARSED_TYPE) {
                entryPoint.parsed.walletInitiatedCredentialOfferOrNull()
                    ?: return invalidWalletInitiatedIssuance(context, entryPoint)
            } else {
                null
            }
        val rawOffer =
            walletInitiatedOffer?.let { Json.encodeToString(CredentialOffer.serializer(), it) }
                ?: entryPoint.raw?.let(::unwrapBrowserCredentialOfferInvocation)
                ?: entryPoint.parsed
                    ?.takeIf { entryPoint.parsedType == DIGITAL_CREDENTIAL_PROTOCOL }
                    ?.toString()
        context.updateOid4vciState { it.copy(entryPointRaw = rawOffer) }
        context.storePrivate(mapOf("entry_point.fingerprint" to entryPoint.summary().fingerprint.orEmpty()))
        val parsed = rawOffer?.let { raw -> holder?.parseCredentialOffer(raw) }
        val offer = parsed?.takeIf { it.isOk }?.value
        val resolved = offer?.let { holder?.resolveCredentialOffer(it) }?.takeIf { it.isOk }?.value

        if (parsed != null && parsed.isErr) {
            val state =
                context
                    .baseState(
                        status = WalletInteractionStatus.Failed,
                        flowKind = WalletInteractionFlowKind.CredentialReceive,
                        protocol = WalletProtocol.OID4VCI,
                        adapterId = capability.adapterId,
                        entryPoint = entryPoint,
                    ).copy(
                        terminal = true,
                        error =
                            classifiedWalletInteractionError(
                                code = WalletInteractionFailureCodes.OID4VCI_OFFER_PARSE_FAILED,
                                messageKey = "wallet.interaction.error.oid4vci_offer_parse_failed",
                            ),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }

        val unresolvedSummary = (offer ?: fallbackOffer(entryPoint)).toSummary(resolved?.issuerMetadata)
        val encounter = context.recordCounterpartyEncounter(WalletProtocol.OID4VCI, unresolvedSummary.issuer)
        val summary = unresolvedSummary.copy(issuer = encounter.counterparty)
        context.storePrivate(
            mapOf(
                "credential_issuer" to summary.issuer.identifier,
                "credential_configuration_ids" to summary.credentialConfigurationIds.joinToString(","),
                "pre_authorized_code_available" to summary.preAuthorizedCodeAvailable.toString(),
                "authorization_code_available" to summary.authorizationCodeAvailable.toString(),
            ),
        )
        val trust =
            context.trustResolver
                .resolve(
                    WalletCounterpartyTrustRequest(
                        counterparty = summary.issuer,
                        protocol = WalletProtocol.OID4VCI,
                    ),
                ).let { trust -> trust.copy(policyAction = context.trustPolicy.evaluate(trust).action) }
                .copy(counterparty = summary.issuer)
        val issuerAuthentication = context.resolveIssuerAuthentication(summary.issuer, trust.issuerAuthentication)
        val resolvedTrust = trust.copy(issuerAuthentication = issuerAuthentication)
        if (!encounter.organizationCreated && resolvedTrust.policyAction == WalletTrustPolicyAction.BLOCK) {
            val state =
                context
                    .baseState(
                        status = WalletInteractionStatus.Failed,
                        flowKind = WalletInteractionFlowKind.CredentialReceive,
                        protocol = WalletProtocol.OID4VCI,
                        adapterId = capability.adapterId,
                        entryPoint = entryPoint,
                    ).copy(
                        counterparty = summary.issuer,
                        counterpartyEncounter = encounter,
                        trust = resolvedTrust,
                        issuerAuthentication = issuerAuthentication,
                        credentialOffer = summary,
                        terminal = true,
                        error =
                            classifiedWalletInteractionError(
                                code = WalletInteractionFailureCodes.OID4VCI_ISSUER_BLOCKED,
                                messageKey = "wallet.interaction.error.issuer_blocked",
                            ),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }
        val status =
            when {
                encounter.organizationCreated -> WalletInteractionStatus.CounterpartyNotice
                encounter.firstInteraction ||
                    resolvedTrust.policyAction == WalletTrustPolicyAction.WARN ||
                    resolvedTrust.policyAction == WalletTrustPolicyAction.ASK_USER ||
                    resolvedTrust.policyAction == WalletTrustPolicyAction.FIRST_CONTACT_PROMPT -> WalletInteractionStatus.TrustReview
                else -> WalletInteractionStatus.CredentialOfferReview
            }
        val state =
            context
                .baseState(
                    status = status,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = capability.adapterId,
                    entryPoint = entryPoint,
                ).copy(
                    counterparty = summary.issuer,
                    counterpartyEncounter = encounter,
                    trust = resolvedTrust,
                    issuerAuthentication = issuerAuthentication,
                    credentialOffer = summary,
                    txCode =
                        if (summary.txCodeRequired) {
                            WalletTxCodeSpec(descriptionKey = "wallet.interaction.tx_code.required")
                        } else {
                            null
                        },
                    credentialPreview = summary.credentialConfigurationIds.map { id -> WalletCredentialPreview(id = id, name = id) },
                )
        return WalletInteractionSession(context.sessionId, state)
    }

    /**
     * A browser wallet accepts the complete browser invocation as an opaque entry point. The
     * protocol adapter is therefore the first layer that interprets a `credential_offer_uri`
     * wrapper. When that wrapper carries an OID4VCI deep link, hand the actual offer link to the
     * holder parser. A normal HTTPS offer document remains unchanged so the holder can fetch it
     * according to OID4VCI.
     */
    private fun unwrapBrowserCredentialOfferInvocation(rawEntryPoint: String): String {
        val nestedOffer = extractQueryParameters(rawEntryPoint)["credential_offer_uri"] ?: return rawEntryPoint
        return nestedOffer.takeIf { it.startsWith(CREDENTIAL_OFFER_SCHEME, ignoreCase = true) } ?: rawEntryPoint
    }

    private fun invalidWalletInitiatedIssuance(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession =
        WalletInteractionSession(
            context.sessionId,
            context
                .baseState(
                    status = WalletInteractionStatus.Failed,
                    flowKind = WalletInteractionFlowKind.CredentialReceive,
                    protocol = WalletProtocol.OID4VCI,
                    adapterId = capability.adapterId,
                    entryPoint = entryPoint,
                ).copy(
                    terminal = true,
                    error =
                        classifiedWalletInteractionError(
                            code = WalletInteractionFailureCodes.OID4VCI_WALLET_INITIATED_INPUT_INVALID,
                            messageKey = "wallet.interaction.error.oid4vci_wallet_initiated_input_invalid",
                        ),
                ),
        )

    /**
     * Wallet-initiated credential refresh: a "normal engine session" per the
     * entry-point contract, but one that skips the offer/trust/tx-code pipeline entirely - there is
     * no issuer counterparty interaction to review, the wallet itself is driving reissuance of a
     * credential it already holds - and drives straight to a terminal state from this single
     * `start()` call, rather than waiting on a `handle()`-driven action like the offer flow does.
     */
    private suspend fun startRefresh(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionSession {
        val baseState =
            context.baseState(
                status = WalletInteractionStatus.Completed,
                flowKind = WalletInteractionFlowKind.CredentialReceive,
                protocol = WalletProtocol.OID4VCI,
                adapterId = capability.adapterId,
                entryPoint = entryPoint,
            )
        val credentialRecordId = entryPoint.parsed.refreshCredentialRecordIdOrNull()
        val credentialInstanceId = entryPoint.parsed.refreshCredentialInstanceIdOrNull()
        if (credentialRecordId == null) {
            val state =
                baseState.copy(
                    status = WalletInteractionStatus.Failed,
                    terminal = true,
                    error =
                        classifiedWalletInteractionError(
                            code = WalletInteractionFailureCodes.OID4VCI_REFRESH_CREDENTIAL_RECORD_ID_MISSING,
                            messageKey = "wallet.interaction.error.oid4vci_refresh_credential_record_id_missing",
                        ),
                )
            return WalletInteractionSession(context.sessionId, state)
        }
        val result = issuanceExecutor.refreshCredential(context, baseState, credentialRecordId, credentialInstanceId)
        val state =
            when (result) {
                is Oid4vciIssuanceExecutionResult.Received -> {
                    baseState.copy(
                        status = WalletInteractionStatus.Completed,
                        terminal = true,
                        credentialPreview = result.credentialPreview.ifEmpty { baseState.credentialPreview },
                        receivedCredentialPreview = result.credentialPreview,
                        message = WalletDisplayMessage(titleKey = result.titleKey),
                        error = null,
                    )
                }

                is Oid4vciIssuanceExecutionResult.Failed -> {
                    baseState.copy(
                        status = WalletInteractionStatus.Failed,
                        terminal = !result.retryable,
                        error =
                            classifiedWalletInteractionError(
                                code = result.code,
                                messageKey = result.messageKey,
                                arguments = result.arguments,
                            ),
                    )
                }

                is Oid4vciIssuanceExecutionResult.Deferred,
                is Oid4vciIssuanceExecutionResult.AuthorizationRequired,
                is Oid4vciIssuanceExecutionResult.NestedPresentationRequired,
                -> {
                    // A wallet-initiated refresh never involves deferred issuance, an authorization
                    // redirect, or a nested OID4VP presentation - an executor returning one of these
                    // here is an internal contract violation, surfaced as a typed terminal failure
                    // instead of propagating a mismatched UI state to the client.
                    baseState.copy(
                        status = WalletInteractionStatus.Failed,
                        terminal = true,
                        error =
                            classifiedWalletInteractionError(
                                code = WalletInteractionFailureCodes.OID4VCI_REFRESH_UNEXPECTED_RESULT,
                                messageKey = "wallet.interaction.error.oid4vci_refresh_unexpected_result",
                            ),
                    )
                }
            }
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

            WalletInteractionActionType.CONTINUE -> {
                when (sessionState.status) {
                    WalletInteractionStatus.TrustReview ->
                        sessionState.copy(
                            status = WalletInteractionStatus.CredentialOfferReview,
                            revision = sessionState.revision + 1,
                            error = null,
                        )
                    WalletInteractionStatus.DisclosureConsent -> context.authorizeHolderOperation(sessionState, action)
                    else -> sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_ACTION_CONTINUE_NOT_ALLOWED)
                }
            }

            WalletInteractionActionType.SELECT_CREDENTIALS -> {
                when (sessionState.status) {
                    WalletInteractionStatus.CredentialOfferReview -> context.acceptCredentialOffer(sessionState, action)
                    WalletInteractionStatus.CredentialSelection -> context.authorizeHolderOperation(sessionState, action)
                    else -> sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_ACTION_SELECTION_NOT_ALLOWED)
                }
            }

            WalletInteractionActionType.SUBMIT_TX_CODE -> {
                if (sessionState.status != WalletInteractionStatus.TxCodeRequired) {
                    sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_ACTION_TX_CODE_NOT_ALLOWED)
                } else {
                    val txCode =
                        action.sensitiveInputRef?.let { ref ->
                            context.sensitiveInputAuthority.consume(
                                sessionId = sessionState.sessionId,
                                purpose = WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE,
                                ref = ref,
                            )
                        }
                    if (txCode.isNullOrBlank()) {
                        sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_TX_CODE_REF_INVALID)
                    } else {
                        context.updateOid4vciState { it.copy(txCode = txCode) }
                        context.authorizeHolderOperation(sessionState, action)
                    }
                }
            }

            WalletInteractionActionType.AUTH_CALLBACK -> {
                if (sessionState.status != WalletInteractionStatus.AuthorizationRequired) {
                    sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_ACTION_AUTH_CALLBACK_NOT_ALLOWED)
                } else {
                    val callback =
                        action.sensitiveInputRef?.let { ref ->
                            context.sensitiveInputAuthority.consume(
                                sessionId = sessionState.sessionId,
                                purpose = WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_CALLBACK,
                                ref = ref,
                            )
                        }
                    if (callback.isNullOrBlank()) {
                        sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_AUTH_CALLBACK_REF_INVALID)
                    } else {
                        context.updateOid4vciState { it.copy(authorizationCallback = callback) }
                        context.applyIssuanceResult(
                            sessionState.copy(
                                revision = sessionState.revision + 1,
                                authorizationHandoffRef = null,
                                error = null,
                            ),
                            action,
                        )
                    }
                }
            }

            WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE -> {
                if (sessionState.status != WalletInteractionStatus.SecurityUnlockRequired) {
                    sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_ACTION_SECURITY_GRANT_NOT_ALLOWED)
                } else {
                    val grant =
                        action.sensitiveInputRef?.let { ref ->
                            context.sensitiveInputAuthority.consumeSecurityGrant(sessionState.sessionId, ref)
                        }
                    val validation =
                        grant?.validateFor(
                            sessionState.securityChallenge,
                            sessionState.walletUnitId,
                            sessionState.counterparty?.identifier,
                            Clock.System.now().epochSeconds,
                        )
                    if (grant == null || validation is WalletSecurityGrantValidation.Invalid) {
                        sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_SECURITY_GRANT_REF_INVALID)
                    } else {
                        val operationBinding = grant.evidence["operation_binding"].orEmpty()
                        context.storePrivate(
                            mapOf(
                                "security_grant_id" to grant.grantId,
                                SECURITY_OPERATION_BINDING_PRIVATE_KEY to operationBinding,
                            ),
                        )
                        attendedAuthorizationRegistry.authorize(sessionState.walletUnitId, operationBinding, grant)
                        context.applyIssuanceResult(
                            sessionState.copy(
                                revision = sessionState.revision + 1,
                                securityChallenge = null,
                            ),
                            action,
                        )
                    }
                }
            }

            WalletInteractionActionType.RETRY_DEFERRED_RETRIEVAL,
            WalletInteractionActionType.RESUME_DEFERRED_RETRIEVAL,
            -> {
                context.applyIssuanceResult(
                    sessionState.copy(
                        status = WalletInteractionStatus.DeferredRetrievalPending,
                        revision = sessionState.revision + 1,
                        error = null,
                    ),
                )
            }

            else -> {
                sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_ACTION_NOT_ALLOWED)
            }
        }

    private suspend fun WalletInteractionContext.resolveCounterpartyContact(
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState {
        if (sessionState.status != WalletInteractionStatus.CounterpartyNotice) {
            return sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_ACTION_COUNTERPARTY_RESOLUTION_NOT_ALLOWED)
        }
        val encounter = sessionState.counterpartyEncounter
            ?: return sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_COUNTERPARTY_ENCOUNTER_MISSING)
        val decision = action.counterpartyAssociation
            ?: return sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_COUNTERPARTY_ASSOCIATION_MISSING)
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
                        protocol = WalletProtocol.OID4VCI,
                    ),
                ).let { resolved -> resolved.copy(policyAction = trustPolicy.evaluate(resolved).action) }
                .copy(counterparty = counterparty)
        val issuerAuthentication = resolveIssuerAuthentication(counterparty, trust.issuerAuthentication)
        val resolvedTrust = trust.copy(issuerAuthentication = issuerAuthentication)
        val offer = sessionState.credentialOffer?.copy(issuer = counterparty)
        if (resolvedTrust.policyAction == WalletTrustPolicyAction.BLOCK) {
            return sessionState.copy(
                status = WalletInteractionStatus.Failed,
                revision = sessionState.revision + 1,
                counterparty = counterparty,
                counterpartyEncounter = resolvedEncounter,
                trust = resolvedTrust,
                issuerAuthentication = issuerAuthentication,
                credentialOffer = offer,
                terminal = true,
                error =
                    classifiedWalletInteractionError(
                        code = WalletInteractionFailureCodes.OID4VCI_ISSUER_BLOCKED,
                        messageKey = "wallet.interaction.error.issuer_blocked",
                    ),
            )
        }
        val nextStatus =
            if (resolvedEncounter.firstInteraction ||
                resolvedTrust.policyAction == WalletTrustPolicyAction.WARN ||
                resolvedTrust.policyAction == WalletTrustPolicyAction.ASK_USER ||
                resolvedTrust.policyAction == WalletTrustPolicyAction.FIRST_CONTACT_PROMPT
            ) {
                WalletInteractionStatus.TrustReview
            } else {
                WalletInteractionStatus.CredentialOfferReview
            }
        return sessionState.copy(
            status = nextStatus,
            revision = sessionState.revision + 1,
            counterparty = counterparty,
            counterpartyEncounter = resolvedEncounter,
            trust = resolvedTrust,
            issuerAuthentication = issuerAuthentication,
            credentialOffer = offer,
            error = null,
        )
    }

    private suspend fun WalletInteractionContext.acceptCredentialOffer(
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState {
        val offered = sessionState.credentialOffer?.credentialConfigurationIds.orEmpty()
        val selected =
            action.selection
                ?.selectedCredentialIdsByRequirement
                .orEmpty()
                .values
                .flatten()
        if (selected.isEmpty()) return sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_OFFER_SELECTION_EMPTY)
        if (selected.size != selected.distinct().size) return sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_OFFER_SELECTION_DUPLICATE)
        if (selected.any { it !in offered }) return sessionState.rejectAction(WalletInteractionFailureCodes.OID4VCI_OFFER_SELECTION_UNKNOWN)

        val accepted =
            sessionState.copy(
                selectedCredentialConfigurationIds = selected,
                revision = sessionState.revision + 1,
                error = null,
            )
        return if (sessionState.credentialOffer?.txCodeRequired == true) {
            accepted.copy(status = WalletInteractionStatus.TxCodeRequired)
        } else {
            authorizeHolderOperation(accepted, action)
        }
    }

    private fun WalletInteractionState.rejectAction(code: String): WalletInteractionState =
        copy(
            revision = revision + 1,
            error =
                classifiedWalletInteractionError(
                    code = code,
                    messageKey = "wallet.interaction.error.action_not_allowed",
                ),
        )

    private suspend fun WalletInteractionContext.authorizeHolderOperation(
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState {
        val operationId = "${sessionState.sessionId.value}-holder-proof"
        // Explicit launch attribute wins; otherwise bind to this protocol operation (same default
        // the security gate used to synthesize before operationBinding became a required field).
        val operationBinding =
            securityAttribute(WalletSecurityContextAttributes.OPERATION_BINDING)
                ?: "operation:$operationId"
        val result =
            authorizeProtocolOperation(
                WalletProtocolExecutionRequest(
                    operationId = operationId,
                    sessionId = sessionState.sessionId,
                    sessionWalletUnitId = sessionState.walletUnitId,
                    protocol = WalletProtocol.OID4VCI,
                    operation = WalletSecurityOperation.PRESENT_PROOF,
                    audience = sessionState.counterparty?.identifier,
                    keyRef = securityAttribute(WalletSecurityContextAttributes.KEY_REF),
                    walletUnitId = securityAttribute(WalletSecurityContextAttributes.WALLET_UNIT_ID)
                        ?: sessionState.walletUnitId,
                    walletAccountId = securityAttribute(WalletSecurityContextAttributes.WALLET_ACCOUNT_ID),
                    activationDecisionId = securityAttribute(WalletSecurityContextAttributes.ACTIVATION_DECISION_ID),
                    operationType = securityAttribute(WalletSecurityContextAttributes.OPERATION_TYPE),
                    operationBinding = operationBinding,
                    operationHash = securityAttribute(WalletSecurityContextAttributes.OPERATION_HASH),
                    nonce = securityAttribute(WalletSecurityContextAttributes.NONCE),
                ),
            )
        return when (result) {
            is WalletSecurityGateResult.Authorized -> {
                val operationBinding = result.grant.evidence["operation_binding"].orEmpty()
                storePrivate(
                    mapOf(
                        "security_grant_id" to result.grant.grantId,
                        SECURITY_OPERATION_BINDING_PRIVATE_KEY to operationBinding,
                    ),
                )
                attendedAuthorizationRegistry.authorize(sessionState.walletUnitId, operationBinding, result.grant)
                applyIssuanceResult(sessionState.copy(revision = sessionState.revision + 1, securityChallenge = null), action)
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
                        classifiedWalletInteractionError(
                            code = WalletInteractionFailureCodes.OID4VCI_SECURITY_DENIED,
                            messageKey = result.reasonKey,
                            arguments = result.arguments,
                        ),
                )
            }
        }
    }

    private suspend fun WalletInteractionContext.applyIssuanceResult(
        sessionState: WalletInteractionState,
        action: WalletInteractionAction? = null,
    ): WalletInteractionState =
        when (val result = issuanceExecutor.requestCredential(this, sessionState, action)) {
            is Oid4vciIssuanceExecutionResult.Received -> {
                val received =
                    sessionState.copy(
                        credentialPreview = result.credentialPreview.ifEmpty { sessionState.credentialPreview },
                        receivedCredentialPreview = result.credentialPreview,
                        message = WalletDisplayMessage(titleKey = result.titleKey),
                        error = null,
                    )
                when (val notification = issuanceExecutor.notifyCredentialAccepted(this, received)) {
                    is Oid4vciIssuerNotificationResult.Failed ->
                        received.next(
                            status = WalletInteractionStatus.Failed,
                            terminal = !notification.retryable,
                            error =
                                classifiedWalletInteractionError(
                                    code = notification.code,
                                    messageKey = notification.messageKey,
                                    arguments = notification.arguments,
                                ),
                        )
                    Oid4vciIssuerNotificationResult.NotSupported,
                    Oid4vciIssuerNotificationResult.Sent,
                    ->
                        received.next(
                            status = WalletInteractionStatus.Completed,
                            terminal = true,
                            message = WalletDisplayMessage(titleKey = result.titleKey),
                        )
                }
            }

            is Oid4vciIssuanceExecutionResult.Deferred -> {
                sessionState.copy(
                    status = WalletInteractionStatus.DeferredRetrievalPending,
                    deferred =
                        WalletDeferredRetrievalSummary(
                            intervalSeconds = result.intervalSeconds,
                            attempt = result.attempt,
                            resumable = result.resumable,
                        ),
                    error = null,
                )
            }

            is Oid4vciIssuanceExecutionResult.AuthorizationRequired -> {
                val handoffRef =
                    sensitiveInputAuthority.register(
                        sessionId = sessionState.sessionId,
                        purpose = WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_HANDOFF,
                        value = result.authorizationUrl,
                    )
                sessionState.copy(
                    status = WalletInteractionStatus.AuthorizationRequired,
                    authorizationHandoffRef = handoffRef,
                    error = null,
                )
            }

            is Oid4vciIssuanceExecutionResult.NestedPresentationRequired -> {
                sessionState.copy(
                    status =
                        if (result.credentialSelection
                                ?.requirements
                                .orEmpty()
                                .isEmpty()
                        ) {
                            WalletInteractionStatus.DisclosureConsent
                        } else {
                            WalletInteractionStatus.CredentialSelection
                        },
                    credentialSelection = result.credentialSelection,
                    disclosure = result.disclosure,
                    error = null,
                )
            }

            is Oid4vciIssuanceExecutionResult.Failed -> {
                sessionState.next(
                    status = WalletInteractionStatus.Failed,
                    terminal = !result.retryable,
                    error =
                        classifiedWalletInteractionError(
                            code = result.code,
                            messageKey = result.messageKey,
                            arguments = result.arguments,
                        ),
                )
            }
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

    private fun CredentialOffer.toSummary(metadata: CredentialIssuerMetadata? = null): WalletCredentialOfferSummary {
        val issuerDisplays = metadata?.display.orEmpty()
        val activeIssuerDisplay = issuerDisplays.firstOrNull()
        val issuer =
            WalletCounterpartySummary(
                role = WalletCounterpartyRole.ISSUER,
                identifier = credentialIssuer,
                displayName = activeIssuerDisplay?.name ?: credentialIssuer,
                displayNameSource = selfAssertedDisplayNameSource(activeIssuerDisplay?.name),
                logoUri = activeIssuerDisplay?.logo?.uri ?: issuerDisplays.firstNotNullOfOrNull { it.logo?.uri },
                localizedBranding =
                    issuerDisplays.map { display ->
                        com.sphereon.wallet.interaction.WalletCounterpartyLocalizedBranding(
                            locale = display.locale,
                            name = display.name,
                            logoUri = display.logo?.uri,
                            logoAltText = display.logo?.altText,
                            description = display.description,
                            backgroundImageUri = display.backgroundImage?.uri,
                            backgroundColor = display.backgroundColor,
                            textColor = display.textColor,
                        )
                    },
            )
        val preAuth = grants?.preAuthorizedCode
        val auth = grants?.authorizationCode
        return WalletCredentialOfferSummary(
            issuer = issuer,
            credentialConfigurationIds = credentialConfigurationIds,
            preAuthorizedCodeAvailable = preAuth != null,
            authorizationCodeAvailable = auth != null,
            txCodeRequired = preAuth?.txCode != null,
            branding =
                credentialConfigurationIds.map { id ->
                    val configuration = metadata?.credentialConfigurationsSupported?.get(id)
                    val display = configuration?.credentialMetadata?.display?.firstOrNull() ?: configuration?.display?.firstOrNull()
                    val info =
                        configuration?.credentialMetadata?.claims.orEmpty().mapNotNull { claim ->
                            val path = claim.path.mapNotNull { element -> element.toString().trim('"').takeIf(String::isNotBlank) }
                            val name = claim.display?.firstOrNull()?.name ?: path.lastOrNull()
                            name?.let { WalletCredentialOfferInfoDescriptor(path = path, displayName = it) }
                        }.ifEmpty {
                            configuration?.claims.orEmpty().mapNotNull { claim ->
                                val name = claim.display?.firstOrNull()?.name ?: claim.path.lastOrNull()
                                name?.let { WalletCredentialOfferInfoDescriptor(path = claim.path, displayName = it) }
                            }
                        }
                    WalletCredentialBranding(
                        credentialConfigurationId = id,
                        name = display?.name ?: id,
                        locale = display?.locale,
                        description = display?.description,
                        logoUri = display?.logo?.uri,
                        backgroundImageUri = display?.backgroundImage?.uri,
                        backgroundColor = display?.backgroundColor,
                        textColor = display?.textColor,
                        info = info,
                    )
                },
        )
    }

    private fun fallbackOffer(entryPoint: WalletEntryPoint): CredentialOffer =
        CredentialOffer(
            credentialIssuer = entryPoint.raw?.substringAfter("credential_issuer=", missingDelimiterValue = "unknown-issuer")?.substringBefore("&") ?: "unknown-issuer",
            credentialConfigurationIds = listOf("credential"),
            grants = null,
        )

    companion object {
        private const val CREDENTIAL_OFFER_SCHEME: String = "openid-credential-offer://"
        const val ADAPTER_ID: String = "oid4vci"
        const val SECURITY_OPERATION_BINDING_PRIVATE_KEY: String = "security_operation_binding"
        const val DIGITAL_CREDENTIAL_PROTOCOL: String = "openid4vci-v1"

        /**
         * `WalletEntryPointKind.PARSED_OBJECT` type for normal wallet-initiated issuance:
         * `parsed = {"credentialIssuer":"...","credentialConfigurationIds":["..."]}`.
         */
        const val ISSUE_PARSED_TYPE: String = "com.sphereon.wallet.credential.issue"

        /**
         * `WalletEntryPointKind.PARSED_OBJECT` type claimed for a wallet-initiated credential
         * refresh entry point: `parsed = {"credentialRecordId": "..."}`.
         */
        const val REFRESH_PARSED_TYPE: String = "com.sphereon.wallet.credential.refresh"
    }
}

private suspend fun WalletInteractionContext.resolveIssuerAuthentication(
    counterparty: WalletCounterpartySummary,
    trustAuthentication: WalletIssuerAuthenticationResult?,
): WalletIssuerAuthenticationResult? {
    val policyResult = issuerAuthenticationResolver.resolve(
        WalletIssuerAuthenticationRequest(
            counterparty = counterparty,
            protocol = WalletProtocol.OID4VCI,
        ),
    )
    val policyAuthentication = policyResult?.admitForIssuer(counterparty.identifier)
    val admittedTrust = trustAuthentication?.admitForIssuer(counterparty.identifier)

    return when {
        trustAuthentication != null && admittedTrust == null -> null
        trustAuthentication != null && policyResult != null && policyAuthentication == null -> null
        admittedTrust != null && policyAuthentication != null ->
            admittedTrust.takeIf { it.issuer == policyAuthentication.issuer && it.trustedJwks == policyAuthentication.trustedJwks }
        admittedTrust != null -> admittedTrust
        else -> policyAuthentication
    }
}

private fun JsonElement?.walletInitiatedCredentialOfferOrNull(): CredentialOffer? {
    val input = this as? JsonObject ?: return null
    val credentialIssuer =
        (input["credentialIssuer"] as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: return null
    val credentialConfigurationIds =
        (input["credentialConfigurationIds"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            ?.distinct()
            ?.takeIf(List<String>::isNotEmpty)
            ?: return null
    val authorizationServer =
        (input["authorizationServer"] as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
    return CredentialOffer(
        credentialIssuer = credentialIssuer,
        credentialConfigurationIds = credentialConfigurationIds,
        grants =
            CredentialOfferGrants(
                authorizationCode = AuthorizationCodeOfferGrant(authorizationServer = authorizationServer),
            ),
    )
}

private fun WalletInteractionContext.securityAttribute(key: String): String? = attributes[key]?.takeIf { it.isNotBlank() }

private fun JsonElement?.refreshCredentialRecordIdOrNull(): String? =
    (this as? JsonObject)
        ?.get("credentialRecordId")
        ?.let { (it as? JsonPrimitive)?.contentOrNull }
        ?.takeIf { it.isNotBlank() }

private fun JsonElement?.refreshCredentialInstanceIdOrNull(): String? =
    (this as? JsonObject)
        ?.get("credentialInstanceId")
        ?.let { (it as? JsonPrimitive)?.contentOrNull }
        ?.takeIf { it.isNotBlank() }

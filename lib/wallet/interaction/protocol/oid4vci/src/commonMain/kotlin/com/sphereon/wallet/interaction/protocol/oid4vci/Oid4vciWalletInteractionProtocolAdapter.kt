/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCounterpartyTrustRequest
import com.sphereon.wallet.interaction.WalletCredentialBranding
import com.sphereon.wallet.interaction.WalletCredentialOfferSummary
import com.sphereon.wallet.interaction.WalletCredentialPreview
import com.sphereon.wallet.interaction.WalletDeferredRetrievalSummary
import com.sphereon.wallet.interaction.WalletDisplayMessage
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletEntryPointKind
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
import com.sphereon.wallet.interaction.WalletSecurityContextAttributes
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.interaction.WalletTrustPolicyAction
import com.sphereon.wallet.interaction.WalletTxCodeSpec

class Oid4vciWalletInteractionProtocolAdapter(
    private val holder: Oid4vciHolderService? = null,
    private val issuanceExecutor: Oid4vciIssuanceExecutor = Oid4vciIssuanceExecutor.notConfigured,
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
        context.storePrivate(
            mapOf(
                "entry_point.raw" to entryPoint.raw.orEmpty(),
                "entry_point.fingerprint" to entryPoint.summary().fingerprint.orEmpty(),
            ),
        )
        val parsed = entryPoint.raw?.let { raw -> holder?.parseCredentialOffer(raw) }
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
                            WalletInteractionError(
                                code = "oid4vci.offer_parse_failed",
                                messageKey = "wallet.interaction.error.oid4vci_offer_parse_failed",
                                retryable = false,
                            ),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }

        val summary =
            (offer ?: fallbackOffer(entryPoint)).toSummary(
                resolved
                    ?.issuerMetadata
                    ?.display
                    ?.firstOrNull()
                    ?.name
            )
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
        if (trust.policyAction == WalletTrustPolicyAction.BLOCK) {
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
                        trust = trust,
                        credentialOffer = summary,
                        terminal = true,
                        error =
                            WalletInteractionError(
                                code = "oid4vci.issuer_blocked",
                                messageKey = "wallet.interaction.error.issuer_blocked",
                            ),
                    )
            return WalletInteractionSession(context.sessionId, state)
        }
        val status =
            when {
                trust.policyAction == WalletTrustPolicyAction.ASK_USER ||
                    trust.policyAction == WalletTrustPolicyAction.FIRST_CONTACT_PROMPT -> WalletInteractionStatus.TrustReview

                summary.txCodeRequired -> WalletInteractionStatus.TxCodeRequired

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
                    trust = trust,
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

    override suspend fun handle(
        context: WalletInteractionContext,
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState =
        when (action.type) {
            WalletInteractionActionType.DECLINE -> {
                sessionState.next(WalletInteractionStatus.Cancelled, terminal = true)
            }

            WalletInteractionActionType.SUBMIT_TX_CODE,
            WalletInteractionActionType.SELECT_CREDENTIALS,
            WalletInteractionActionType.CONTINUE,
            WalletInteractionActionType.AUTH_CALLBACK,
            -> {
                if (sessionState.status == WalletInteractionStatus.TrustReview) {
                    sessionState.copy(
                        status =
                            if (sessionState.txCode != null) {
                                WalletInteractionStatus.TxCodeRequired
                            } else {
                                WalletInteractionStatus.CredentialOfferReview
                            },
                        revision = sessionState.revision + 1,
                    )
                } else {
                    if (action.type == WalletInteractionActionType.SUBMIT_TX_CODE) {
                        context.storePrivate(mapOf("tx_code" to action.value.orEmpty()))
                    } else if (action.type == WalletInteractionActionType.AUTH_CALLBACK) {
                        context.storePrivate(mapOf("authorization_callback" to (action.authorizationCallback ?: action.value).orEmpty()))
                    }
                    val selectedCredentialIds =
                        action.selection
                            ?.selectedCredentialIdsByRequirement
                            .orEmpty()
                            .values
                            .flatten()
                    val stateForAction =
                        if (selectedCredentialIds.isNotEmpty()) {
                            sessionState.copy(
                                disclosure =
                                    sessionState.disclosure?.copy(
                                        selectedCredentialIds = selectedCredentialIds,
                                    ),
                            )
                        } else {
                            sessionState
                        }
                    context.authorizeHolderOperation(stateForAction, action)
                }
            }

            WalletInteractionActionType.APPROVE_SECURITY_CHALLENGE -> {
                action.securityGrant?.let { grant -> context.storePrivate(mapOf("security_grant_id" to grant.grantId)) }
                context.applyIssuanceResult(
                    sessionState.copy(
                        revision = sessionState.revision + 1,
                        securityChallenge = null,
                    ),
                    action,
                )
            }

            WalletInteractionActionType.ACCEPT_RECEIVED_CREDENTIAL -> {
                context.completeAcceptedCredential(sessionState)
            }

            WalletInteractionActionType.DECLINE_RECEIVED_CREDENTIAL -> {
                context.completeDeclinedCredential(sessionState)
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
                sessionState.next()
            }
        }

    private suspend fun WalletInteractionContext.authorizeHolderOperation(
        sessionState: WalletInteractionState,
        action: WalletInteractionAction,
    ): WalletInteractionState {
        val result =
            authorizeProtocolOperation(
                WalletProtocolExecutionRequest(
                    operationId = "${sessionState.sessionId.value}-holder-proof",
                    sessionId = sessionState.sessionId,
                    walletInstanceId = sessionState.walletInstanceId,
                    protocol = WalletProtocol.OID4VCI,
                    operation = WalletSecurityOperation.HOLDER_PROOF,
                    audience = sessionState.counterparty?.identifier,
                    keyRef = securityAttribute(WalletSecurityContextAttributes.KEY_REF),
                    walletUnitId = securityAttribute(WalletSecurityContextAttributes.WALLET_UNIT_ID),
                    walletAccountId = securityAttribute(WalletSecurityContextAttributes.WALLET_ACCOUNT_ID),
                    activationDecisionId = securityAttribute(WalletSecurityContextAttributes.ACTIVATION_DECISION_ID),
                    operationType = securityAttribute(WalletSecurityContextAttributes.OPERATION_TYPE),
                    operationHash = securityAttribute(WalletSecurityContextAttributes.OPERATION_HASH),
                    nonce = securityAttribute(WalletSecurityContextAttributes.NONCE),
                ),
            )
        return when (result) {
            is WalletSecurityGateResult.Authorized -> {
                storePrivate(mapOf("security_grant_id" to result.grant.grantId))
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
                        WalletInteractionError(
                            code = "oid4vci.security_denied",
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
                sessionState.copy(
                    status = WalletInteractionStatus.ReceivedCredentialReview,
                    credentialPreview = result.credentialPreview.ifEmpty { sessionState.credentialPreview },
                    message = WalletDisplayMessage(titleKey = result.titleKey),
                    error = null,
                )
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
                sessionState.copy(
                    status = WalletInteractionStatus.AuthorizationRequired,
                    authorizationUrl = result.authorizationUrl,
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
                        WalletInteractionError(
                            code = result.code,
                            messageKey = result.messageKey,
                            retryable = result.retryable,
                            arguments = result.arguments,
                        ),
                )
            }
        }

    private suspend fun WalletInteractionContext.completeAcceptedCredential(sessionState: WalletInteractionState): WalletInteractionState =
        when (val result = issuanceExecutor.notifyCredentialAccepted(this, sessionState)) {
            is Oid4vciIssuerNotificationResult.Failed -> {
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

            Oid4vciIssuerNotificationResult.NotSupported,
            Oid4vciIssuerNotificationResult.Sent,
            -> {
                sessionState.next(
                    status = WalletInteractionStatus.Completed,
                    terminal = true,
                    message = WalletDisplayMessage(titleKey = "wallet.interaction.status.credential_received"),
                )
            }
        }

    private suspend fun WalletInteractionContext.completeDeclinedCredential(sessionState: WalletInteractionState): WalletInteractionState =
        when (val result = issuanceExecutor.notifyCredentialDeclined(this, sessionState)) {
            is Oid4vciIssuerNotificationResult.Failed -> {
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

            Oid4vciIssuerNotificationResult.NotSupported,
            Oid4vciIssuerNotificationResult.Sent,
            -> {
                sessionState.next(WalletInteractionStatus.Cancelled, terminal = true)
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

    private fun CredentialOffer.toSummary(displayName: String? = null): WalletCredentialOfferSummary {
        val issuer =
            WalletCounterpartySummary(
                role = WalletCounterpartyRole.ISSUER,
                identifier = credentialIssuer,
                displayName = displayName ?: credentialIssuer,
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
                credentialConfigurationIds.map {
                    WalletCredentialBranding(credentialConfigurationId = it, name = it)
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
        const val ADAPTER_ID: String = "oid4vci"
    }
}

private fun WalletInteractionContext.securityAttribute(key: String): String? = attributes[key]?.takeIf { it.isNotBlank() }

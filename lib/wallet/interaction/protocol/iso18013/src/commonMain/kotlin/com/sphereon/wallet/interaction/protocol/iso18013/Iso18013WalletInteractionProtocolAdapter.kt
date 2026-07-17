/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.iso18013

import com.sphereon.mdoc.engagement.MdocEngagementManager
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletCounterpartySummary
import com.sphereon.wallet.interaction.WalletCounterpartyTrustRequest
import com.sphereon.wallet.interaction.WalletDisclosureSummary
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
import kotlin.time.Clock

class Iso18013WalletInteractionProtocolAdapter(
    private val engagementManager: MdocEngagementManager? = null,
    private val disclosureExecutor: Iso18013DisclosureExecutor = Iso18013DisclosureExecutor.notConfigured,
    priority: Int = 80,
) : WalletInteractionProtocolAdapter {
    override val capability: WalletProtocolCapability =
        WalletProtocolCapability(
            adapterId = ADAPTER_ID,
            protocol = WalletProtocol.ISO18013,
            flowKinds = listOf(WalletInteractionFlowKind.AttendedPresent, WalletInteractionFlowKind.CredentialPresent),
            priority = priority,
            labelKey = "wallet.interaction.adapter.iso18013",
        )

    override suspend fun canHandle(entryPoint: WalletEntryPoint): WalletProtocolMatch {
        val raw = entryPoint.raw.orEmpty().lowercase()
        return when {
            raw.startsWith("mdoc://") -> WalletProtocolMatch.strong(capability.priority, "iso18013.match.website_retrieval")
            raw.startsWith("mdoc:") -> WalletProtocolMatch.strong(capability.priority, "iso18013.match.reverse_engagement")
            entryPoint.kind == WalletEntryPointKind.NFC_HANDOVER -> WalletProtocolMatch.strong(capability.priority, "iso18013.match.nfc_handover")
            entryPoint.kind == WalletEntryPointKind.BLE_HANDOVER -> WalletProtocolMatch.strong(capability.priority, "iso18013.match.ble_handover")
            entryPoint.kind == WalletEntryPointKind.WIFI_AWARE_HANDOVER -> WalletProtocolMatch.weak(capability.priority, "iso18013.match.wifi_aware_handover_model_only")
            else -> WalletProtocolMatch.none
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
                "entry_point.kind" to entryPoint.kind.name,
            ),
        )
        if (entryPoint.kind == WalletEntryPointKind.WIFI_AWARE_HANDOVER) {
            val state =
                base(context, entryPoint).copy(
                    status = WalletInteractionStatus.UnsupportedEntryPoint,
                    terminal = true,
                    error =
                        WalletInteractionError(
                            code = "iso18013.transport_unavailable",
                            messageKey = "wallet.interaction.error.iso18013_transport_unavailable",
                        ),
                )
            return WalletInteractionSession(context.sessionId, state)
        }

        val toAppResult = entryPoint.raw?.let { uri -> engagementManager?.toApp(uri, autoStart = false) }
        if (toAppResult != null && toAppResult.isErr) {
            val state =
                base(context, entryPoint).copy(
                    status = WalletInteractionStatus.Failed,
                    terminal = true,
                    error =
                        WalletInteractionError(
                            code = "iso18013.engagement_failed",
                            messageKey = "wallet.interaction.error.iso18013_engagement_failed",
                        ),
                )
            return WalletInteractionSession(context.sessionId, state)
        }

        val reader =
            WalletCounterpartySummary(
                role = WalletCounterpartyRole.MDOC_READER,
                identifier = entryPoint.summary().scheme ?: "mdoc-reader",
            )
        val trust =
            context.trustResolver
                .resolve(
                    WalletCounterpartyTrustRequest(
                        counterparty = reader,
                        protocol = WalletProtocol.ISO18013,
                    ),
                ).let { trust -> trust.copy(policyAction = context.trustPolicy.evaluate(trust).action) }
        if (trust.policyAction == WalletTrustPolicyAction.BLOCK) {
            val state =
                base(context, entryPoint).copy(
                    status = WalletInteractionStatus.Failed,
                    disclosure = WalletDisclosureSummary(verifier = reader),
                    counterparty = reader,
                    trust = trust,
                    terminal = true,
                    error =
                        WalletInteractionError(
                            code = "iso18013.reader_blocked",
                            messageKey = "wallet.interaction.error.mdoc_reader_blocked",
                        ),
                )
            return WalletInteractionSession(context.sessionId, state)
        }
        val state =
            base(context, entryPoint).copy(
                status =
                    if (trust.policyAction == WalletTrustPolicyAction.ASK_USER ||
                        trust.policyAction == WalletTrustPolicyAction.FIRST_CONTACT_PROMPT
                    ) {
                        WalletInteractionStatus.TrustReview
                    } else {
                        WalletInteractionStatus.DisclosureConsent
                    },
                counterparty = reader,
                trust = trust,
                disclosure =
                    WalletDisclosureSummary(
                        verifier = reader,
                    ),
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

            WalletInteractionActionType.CONTINUE,
            WalletInteractionActionType.SELECT_CREDENTIALS,
            -> {
                if (sessionState.status == WalletInteractionStatus.TrustReview) {
                    sessionState.copy(
                        status = WalletInteractionStatus.DisclosureConsent,
                        revision = sessionState.revision + 1,
                    )
                } else {
                    context.authorizeMdocDisclosure(sessionState)
                }
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
                        error = WalletInteractionError("iso18013.security_grant_ref_invalid", "wallet.interaction.error.security_grant_ref_invalid", retryable = true),
                    )
                } else {
                    context.storePrivate(mapOf("security_grant_id" to grant.grantId))
                    context.applyDisclosureResult(
                        sessionState.copy(revision = sessionState.revision + 1, securityChallenge = null),
                    )
                }
            }

            WalletInteractionActionType.RESOLVE_COUNTERPARTY_CONTACT -> {
                sessionState.copy(
                    revision = sessionState.revision + 1,
                    error =
                        WalletInteractionError(
                            code = "iso18013.action_counterparty_resolution_not_allowed",
                            messageKey = "wallet.interaction.error.action_not_allowed",
                            retryable = true,
                        ),
                )
            }

            else -> {
                sessionState.next()
            }
        }

    private suspend fun WalletInteractionContext.authorizeMdocDisclosure(sessionState: WalletInteractionState): WalletInteractionState {
        val result =
            authorizeProtocolOperation(
                WalletProtocolExecutionRequest(
                    operationId = "${sessionState.sessionId.value}-mdoc-share",
                    sessionId = sessionState.sessionId,
                    sessionWalletUnitId = sessionState.walletUnitId,
                    protocol = WalletProtocol.ISO18013,
                    operation = WalletSecurityOperation.PRESENTATION_SHARING,
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
                applyDisclosureResult(sessionState.copy(revision = sessionState.revision + 1, securityChallenge = null))
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
                            code = "iso18013.security_denied",
                            messageKey = result.reasonKey,
                            arguments = result.arguments,
                        ),
                )
            }
        }
    }

    private suspend fun WalletInteractionContext.applyDisclosureResult(sessionState: WalletInteractionState): WalletInteractionState =
        when (val result = disclosureExecutor.sendDeviceResponse(this, sessionState)) {
            is Iso18013DisclosureExecutionResult.Sent -> {
                sessionState.next(
                    status = WalletInteractionStatus.Completed,
                    terminal = true,
                    message = WalletDisplayMessage(titleKey = result.titleKey),
                )
            }

            is Iso18013DisclosureExecutionResult.Sharing -> {
                sessionState.copy(
                    status = WalletInteractionStatus.Sharing,
                    message = WalletDisplayMessage(titleKey = result.titleKey),
                    error = null,
                )
            }

            is Iso18013DisclosureExecutionResult.RedirectRequired -> {
                val handoffRef =
                    sensitiveInputAuthority.register(
                        sessionId = sessionState.sessionId,
                        purpose = WalletInteractionSensitiveInputPurpose.PROTOCOL_REDIRECT_HANDOFF,
                        value = result.redirectUri,
                    )
                sessionState.copy(
                    status = WalletInteractionStatus.AuthorizationRequired,
                    authorizationHandoffRef = handoffRef,
                    error = null,
                )
            }

            is Iso18013DisclosureExecutionResult.Failed -> {
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

    private fun base(
        context: WalletInteractionContext,
        entryPoint: WalletEntryPoint,
    ): WalletInteractionState =
        context.baseState(
            status = WalletInteractionStatus.DisclosureConsent,
            flowKind = WalletInteractionFlowKind.AttendedPresent,
            protocol = WalletProtocol.ISO18013,
            adapterId = capability.adapterId,
            entryPoint = entryPoint,
        )

    companion object {
        const val ADAPTER_ID: String = "iso18013"
    }
}

private fun WalletInteractionContext.securityAttribute(key: String): String? = attributes[key]?.takeIf { it.isNotBlank() }

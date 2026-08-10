/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.BeginWalletAppOutcomeEvidenceArgs
import com.sphereon.wallet.interaction.BeginWalletAppOutcomeEvidenceCommand
import com.sphereon.wallet.interaction.BeginWalletAppOutcomeEvidenceResult
import com.sphereon.wallet.interaction.RecordWalletAppOutcomeEvidenceArgs
import com.sphereon.wallet.interaction.RecordWalletAppOutcomeEvidenceCommand
import com.sphereon.wallet.interaction.RecordWalletAppOutcomeEvidenceResult
import com.sphereon.wallet.interaction.WalletAppOutcomeEvidenceVerifier
import com.sphereon.wallet.interaction.WalletInteractionActivitySummary
import com.sphereon.wallet.interaction.WalletInteractionActivityType
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.WalletInteractionStatus
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.ProtocolExecutionOwner
import com.sphereon.wallet.interaction.VerifiedWalletAppOutcomeEvidence
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
class BeginWalletAppOutcomeEvidenceCommandImpl(
    execution: SessionExecution,
    private val verifier: WalletAppOutcomeEvidenceVerifier,
) : TypedServiceCommandAdapter<BeginWalletAppOutcomeEvidenceArgs, BeginWalletAppOutcomeEvidenceResult, IdkError>(
        BeginWalletAppOutcomeEvidenceCommand.COMMAND_ID,
        execution,
        typeToken<BeginWalletAppOutcomeEvidenceArgs>(),
        typeToken<BeginWalletAppOutcomeEvidenceResult>(),
    ), BeginWalletAppOutcomeEvidenceCommand {
    override suspend fun doExecute(
        args: BeginWalletAppOutcomeEvidenceArgs,
        applyDuring: (BeginWalletAppOutcomeEvidenceArgs) -> BeginWalletAppOutcomeEvidenceArgs,
    ): IdkResult<BeginWalletAppOutcomeEvidenceResult, IdkError> {
        val input = applyDuring(args)
        return verifier.begin(input.summary).map(::BeginWalletAppOutcomeEvidenceResult)
    }
}

@Inject
@SingleIn(SessionScope::class)
class RecordWalletAppOutcomeEvidenceCommandImpl(
    execution: SessionExecution,
    private val verifier: WalletAppOutcomeEvidenceVerifier,
    private val engine: DefaultWalletInteractionEngine,
) : TypedServiceCommandAdapter<RecordWalletAppOutcomeEvidenceArgs, RecordWalletAppOutcomeEvidenceResult, IdkError>(
        RecordWalletAppOutcomeEvidenceCommand.COMMAND_ID,
        execution,
        typeToken<RecordWalletAppOutcomeEvidenceArgs>(),
        typeToken<RecordWalletAppOutcomeEvidenceResult>(),
    ), RecordWalletAppOutcomeEvidenceCommand {
    override suspend fun doExecute(
        args: RecordWalletAppOutcomeEvidenceArgs,
        applyDuring: (RecordWalletAppOutcomeEvidenceArgs) -> RecordWalletAppOutcomeEvidenceArgs,
    ): IdkResult<RecordWalletAppOutcomeEvidenceResult, IdkError> {
        val input = applyDuring(args)
        engine.replayVerifiedWalletAppOutcome(input.summary)?.let { return com.sphereon.core.api.Ok(it) }
        val verified = verifier.verify(input).getOrElse { return Err(it) }
        val state = engine.recordVerifiedWalletAppOutcome(verified)
        return com.sphereon.core.api.Ok(RecordWalletAppOutcomeEvidenceResult(verified.evidenceRef, state))
    }
}

internal suspend fun DefaultWalletInteractionEngine.replayVerifiedWalletAppOutcome(
    summary: com.sphereon.wallet.interaction.WalletAppOutcomeEvidenceSummary,
): RecordWalletAppOutcomeEvidenceResult? {
    val stored = storedSession(summary.sessionId) ?: return null
    val metadata = stored.input.metadata
    if (stored.state.executionOwner != ProtocolExecutionOwner.WALLET_APP ||
        stored.state.walletUnitId != summary.walletUnitId || stored.state.flowKind != summary.flowKind ||
        stored.state.status != summary.status || !stored.state.terminal ||
        metadata["walletInstanceId"] != summary.walletInstanceId ||
        metadata["appRegistrationId"] != summary.appRegistrationId ||
        metadata["policyRevision"] != summary.policyRevision.toString() ||
        metadata["outcomeDigest"] != summary.outcomeDigest ||
        metadata["recordedAtEpochSeconds"] != summary.recordedAtEpochSeconds.toString() ||
        metadata["idempotencyKey"] != summary.idempotencyKey
    ) {
        return null
    }
    val evidenceRef = metadata["evidenceRef"] ?: return null
    return RecordWalletAppOutcomeEvidenceResult(evidenceRef, stored.state)
}

internal suspend fun DefaultWalletInteractionEngine.recordVerifiedWalletAppOutcome(
    verified: VerifiedWalletAppOutcomeEvidence,
): WalletInteractionState {
    val summary = verified.summary
    val existing = storedSession(summary.sessionId)
    if (existing != null) {
        require(existing.state.executionOwner == ProtocolExecutionOwner.WALLET_APP &&
            existing.state.walletUnitId == summary.walletUnitId && existing.state.flowKind == summary.flowKind &&
            existing.state.status == summary.status && existing.state.terminal
        ) { "wallet_app_outcome_evidence_idempotency_mismatch" }
        return existing.state
    }
    val input = WalletInteractionInput(
        walletUnitId = summary.walletUnitId,
        entryPoint = WalletEntryPoint.link("wallet-app-outcome:${summary.sessionId.value}", source = "WALLET_APP_SIGNED_OUTCOME"),
        executionOwner = ProtocolExecutionOwner.WALLET_APP,
        requestedFlowKinds = listOf(summary.flowKind),
        metadata = mapOf(
            "walletInstanceId" to summary.walletInstanceId,
            "appRegistrationId" to summary.appRegistrationId,
            "policyRevision" to summary.policyRevision.toString(),
            "evidenceRef" to verified.evidenceRef,
            "outcomeDigest" to summary.outcomeDigest,
            "recordedAtEpochSeconds" to summary.recordedAtEpochSeconds.toString(),
            "idempotencyKey" to summary.idempotencyKey,
        ),
    )
    val state = WalletInteractionState(
        sessionId = summary.sessionId,
        walletUnitId = summary.walletUnitId,
        status = summary.status,
        executionOwner = ProtocolExecutionOwner.WALLET_APP,
        revision = 1,
        flowKind = summary.flowKind,
        activity = WalletInteractionActivitySummary(
            type = summary.flowKind.toActivityType(),
            metadata = mapOf(
                "recordedAtEpochSeconds" to summary.recordedAtEpochSeconds.toString(),
                "evidenceRef" to verified.evidenceRef,
                "outcomeDigest" to summary.outcomeDigest,
            ),
        ),
        entryPoint = input.entryPoint.summary(),
        terminal = true,
    )
    saveVerifiedWalletAppOutcome(WalletInteractionStoredSession(summary.sessionId, input, "wallet-app-signed-outcome", state))
    return state
}

private fun com.sphereon.wallet.interaction.WalletInteractionFlowKind.toActivityType(): WalletInteractionActivityType =
    when (this) {
        com.sphereon.wallet.interaction.WalletInteractionFlowKind.CredentialReceive -> WalletInteractionActivityType.CREDENTIAL_RECEIVE
        com.sphereon.wallet.interaction.WalletInteractionFlowKind.CredentialPresent -> WalletInteractionActivityType.CREDENTIAL_PRESENTATION
        com.sphereon.wallet.interaction.WalletInteractionFlowKind.AttendedPresent -> WalletInteractionActivityType.ATTENDED_PRESENTATION
    }

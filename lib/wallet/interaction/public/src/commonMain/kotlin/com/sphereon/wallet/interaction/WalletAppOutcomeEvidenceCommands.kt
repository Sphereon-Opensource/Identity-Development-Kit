/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable

/**
 * Minimized app-owned terminal outcome. It deliberately excludes raw protocol messages,
 * credentials, tokens, disclosures, remote URLs, and credential-derived display metadata.
 */
@Serializable
data class WalletAppOutcomeEvidenceSummary(
    val walletInstanceId: String,
    val walletUnitId: String,
    val appRegistrationId: String,
    val sessionId: WalletInteractionSessionId,
    val flowKind: WalletInteractionFlowKind,
    val status: WalletInteractionStatus,
    val policyRevision: Long,
    val outcomeDigest: String,
    val recordedAtEpochSeconds: Long,
    val idempotencyKey: String,
) {
    init {
        require(listOf(walletInstanceId, walletUnitId, appRegistrationId, sessionId.value, outcomeDigest, idempotencyKey).none(String::isBlank)) {
            "wallet_app_outcome_evidence_binding_blank"
        }
        require(walletInstanceId != walletUnitId) { "wallet_app_outcome_evidence_identity_collapsed" }
        require(status in TERMINAL_STATUSES) { "wallet_app_outcome_evidence_status_not_terminal" }
        require(policyRevision > 0 && recordedAtEpochSeconds > 0) { "wallet_app_outcome_evidence_revision_invalid" }
        require(outcomeDigest.startsWith("sha256:") && outcomeDigest.length > "sha256:".length) {
            "wallet_app_outcome_evidence_digest_invalid"
        }
    }

    companion object {
        val TERMINAL_STATUSES = setOf(
            WalletInteractionStatus.Completed,
            WalletInteractionStatus.Cancelled,
            WalletInteractionStatus.Failed,
        )
    }
}

@Serializable
data class BeginWalletAppOutcomeEvidenceArgs(val summary: WalletAppOutcomeEvidenceSummary)

@Serializable
data class WalletAppOutcomeEvidenceChallenge(
    val challengeId: String,
    val challenge: String,
    val rpId: String,
    val allowedOrigins: Set<String>,
    val userVerification: String,
    val expiresAtEpochSeconds: Long,
    val credentialIds: Set<String>,
) {
    init {
        require(listOf(challengeId, challenge, rpId, userVerification).none(String::isBlank)) {
            "wallet_app_outcome_evidence_challenge_invalid"
        }
        require(allowedOrigins.isNotEmpty() && allowedOrigins.none(String::isBlank)) {
            "wallet_app_outcome_evidence_origins_missing"
        }
        require(credentialIds.size == 1 && credentialIds.none(String::isBlank)) {
            "wallet_app_outcome_evidence_credential_binding_invalid"
        }
        require(expiresAtEpochSeconds > 0) { "wallet_app_outcome_evidence_expiry_invalid" }
    }
}

@Serializable
data class BeginWalletAppOutcomeEvidenceResult(val challenge: WalletAppOutcomeEvidenceChallenge)

@Serializable
data class RecordWalletAppOutcomeEvidenceArgs(
    val summary: WalletAppOutcomeEvidenceSummary,
    val challengeId: String,
    val credentialId: String,
    val authenticatorData: String,
    val clientDataJson: String,
    val signature: String,
    val origin: String,
    val rpId: String,
    val userVerified: Boolean,
    val signCount: Long? = null,
    val backupEligible: Boolean? = null,
    val backupState: Boolean? = null,
    val transport: String? = null,
) {
    init {
        require(
            listOf(challengeId, credentialId, authenticatorData, clientDataJson, signature, origin, rpId).none(String::isBlank),
        ) { "wallet_app_outcome_evidence_assertion_invalid" }
        require(userVerified) { "wallet_app_outcome_evidence_user_verification_required" }
    }
}

@Serializable
data class VerifiedWalletAppOutcomeEvidence(
    val summary: WalletAppOutcomeEvidenceSummary,
    val evidenceRef: String,
    val proofProfile: String,
    val verifiedAtEpochSeconds: Long,
) {
    init {
        require(evidenceRef.isNotBlank() && proofProfile.isNotBlank() && verifiedAtEpochSeconds >= summary.recordedAtEpochSeconds) {
            "wallet_app_outcome_evidence_verification_invalid"
        }
    }
}

@Serializable
data class RecordWalletAppOutcomeEvidenceResult(
    val evidenceRef: String,
    val state: WalletInteractionState,
)

/** App-host client for signing and recording minimized WALLET_APP terminal outcomes. */
interface WalletAppOutcomeEvidenceClient {
    suspend fun begin(summary: WalletAppOutcomeEvidenceSummary): IdkResult<WalletAppOutcomeEvidenceChallenge, IdkError>
    suspend fun record(args: RecordWalletAppOutcomeEvidenceArgs): IdkResult<RecordWalletAppOutcomeEvidenceResult, IdkError>
}

/** Provider-neutral proof port. Product APIs invoke only the commands below. */
interface WalletAppOutcomeEvidenceVerifier {
    suspend fun begin(summary: WalletAppOutcomeEvidenceSummary): IdkResult<WalletAppOutcomeEvidenceChallenge, IdkError>
    suspend fun verify(args: RecordWalletAppOutcomeEvidenceArgs): IdkResult<VerifiedWalletAppOutcomeEvidence, IdkError>
}

interface BeginWalletAppOutcomeEvidenceCommand :
    ServiceCommand<BeginWalletAppOutcomeEvidenceArgs, BeginWalletAppOutcomeEvidenceResult, IdkError> {
    override val actionType: ActionType get() = ActionType.CREATE
    companion object { const val COMMAND_ID = "wallet.interaction.begin-app-outcome-evidence" }
}

interface RecordWalletAppOutcomeEvidenceCommand :
    ServiceCommand<RecordWalletAppOutcomeEvidenceArgs, RecordWalletAppOutcomeEvidenceResult, IdkError> {
    override val actionType: ActionType get() = ActionType.CREATE
    companion object { const val COMMAND_ID = "wallet.interaction.record-app-outcome-evidence" }
}

@ContributesTo(SessionScope::class)
interface WalletAppOutcomeEvidenceCommandBindings {
    @Provides
    fun beginWalletAppOutcomeEvidence(registry: SessionScopedCommandRegistry): BeginWalletAppOutcomeEvidenceCommand =
        registry.get(BeginWalletAppOutcomeEvidenceCommand.COMMAND_ID) as? BeginWalletAppOutcomeEvidenceCommand
            ?: error("No binding for ${BeginWalletAppOutcomeEvidenceCommand.COMMAND_ID}")

    @Provides
    fun recordWalletAppOutcomeEvidence(registry: SessionScopedCommandRegistry): RecordWalletAppOutcomeEvidenceCommand =
        registry.get(RecordWalletAppOutcomeEvidenceCommand.COMMAND_ID) as? RecordWalletAppOutcomeEvidenceCommand
            ?: error("No binding for ${RecordWalletAppOutcomeEvidenceCommand.COMMAND_ID}")
}

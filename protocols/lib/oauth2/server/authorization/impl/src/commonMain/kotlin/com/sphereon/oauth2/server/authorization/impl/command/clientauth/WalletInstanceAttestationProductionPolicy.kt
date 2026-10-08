/*
 * (C) 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.clientauth

import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEvidence
import kotlin.time.Clock

internal object WalletInstanceAttestationProductionPolicy {
    fun validate(
        evidence: WalletInstanceAttestationEvidence?,
        nowEpochSeconds: Long = Clock.System.now().epochSeconds,
    ): String? {
        if (evidence == null) {
            return "Wallet Instance Attestation evidence is required"
        }
        if (evidence.evidenceId.isBlank()) {
            return "Wallet Instance Attestation evidence is not persisted"
        }
        if (evidence.profile.normalized() != PROFILE_TS03_JWT) {
            return "Wallet Instance Attestation profile '${evidence.profile}' is not supported for production issuance"
        }
        if (evidence.format.normalized() != FORMAT_JWT) {
            return "Wallet Instance Attestation format '${evidence.format}' is not supported for production issuance"
        }
        if (evidence.attestationExpiresAtEpochSeconds <= nowEpochSeconds) {
            return "Wallet Instance Attestation evidence has expired"
        }
        evidence.localOrTestToken()?.let {
            return "Wallet Instance Attestation evidence uses local or LOCAL_EVALUATION trust material"
        }

        val status = evidence.clientStatus
        if (status.statusListUri.isBlank() || status.index.isBlank()) {
            return "Wallet Instance Attestation client_status evidence is incomplete"
        }
        if (status.revoked || status.status.normalized() != STATUS_VALID) {
            return "Wallet Instance Attestation client_status is revoked or not valid"
        }
        if (!status.failureReason.isNullOrBlank()) {
            return "Wallet Instance Attestation client_status validation failed: ${status.failureReason}"
        }
        val statusMaintenanceExpiresAt = status.maintenanceExpiresAtEpochSeconds
        if (statusMaintenanceExpiresAt != null && statusMaintenanceExpiresAt <= nowEpochSeconds) {
            return "Wallet Instance Attestation client_status maintenance window has expired"
        }

        val trust = evidence.trust
        if (!trust.trusted) {
            return "Wallet Instance Attestation trust evidence is untrusted"
        }
        val trustDecision = trust.decision.normalized()
        if (trustDecision in REJECTED_TRUST_DECISIONS) {
            return "Wallet Instance Attestation trust decision '$trustDecision' is not acceptable"
        }
        val trustExpiresAt = trust.expiresAtEpochSeconds
        if (trustExpiresAt != null && trustExpiresAt <= nowEpochSeconds) {
            return "Wallet Instance Attestation trust evidence has expired"
        }
        if (trust.revokedAtEpochSeconds != null) {
            return "Wallet Instance Attestation trust evidence is revoked"
        }
        return null
    }

    private fun WalletInstanceAttestationEvidence.localOrTestToken(): String? =
        listOfNotNull(
            profile,
            signerCertificateProfile,
            trust.signerCertificateProfile,
        ).firstOrNull { token ->
            val normalized = token.normalized()
            normalized in LOCAL_OR_TEST_TOKENS
        }

    private fun String.normalized(): String = trim().uppercase().replace('-', '_')

    private const val PROFILE_TS03_JWT = "TS03_JWT"
    private const val FORMAT_JWT = "JWT"
    private const val STATUS_VALID = "VALID"
    private val LOCAL_OR_TEST_TOKENS =
        setOf(
            "LOCAL",
            "LOCAL_EVALUATION",
            "LOCAL_EVALUATION_REFERENCE",
        )
    private val REJECTED_TRUST_DECISIONS =
        setOf(
            "MISSING",
            "UNTRUSTED",
            "EXPIRED",
            "REVOKED",
            "UNSUPPORTED",
            "UNSUPPORTED_PROFILE",
            "LOCAL",
            "LOCAL_EVALUATION",
            "LOCAL_EVALUATION_REFERENCE",
        )
}

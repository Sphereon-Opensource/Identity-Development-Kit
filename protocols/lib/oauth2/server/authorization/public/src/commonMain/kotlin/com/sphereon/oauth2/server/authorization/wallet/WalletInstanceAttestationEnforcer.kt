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

package com.sphereon.oauth2.server.authorization.wallet

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.command.ClientAuthenticationEndpoint
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Narrow extension point for deployments that bind persisted Wallet Unit evidence to OAuth2
 * attestation-based client authentication.
 *
 * The OAuth2 authorization-server module deliberately owns only the enforcement contract and the
 * production evidence checks. EDK/VDX service modules own persistence, TS 119 472-3 validation,
 * status-list resolution, and trust-list evaluation, then return this portable evidence shape.
 */
interface WalletInstanceAttestationEnforcer {
    suspend fun enforce(request: WalletInstanceAttestationEnforcementRequest): IdkResult<WalletInstanceAttestationEvidence, IdkError>
}

data class WalletInstanceAttestationEnforcementRequest(
    val clientId: String,
    val endpoint: ClientAuthenticationEndpoint,
    val endpointUrl: String,
    val acceptedAudiences: Set<String>,
    val attestationJwt: String,
    val attestationPopJwt: String,
)

data class WalletInstanceAttestationEvidence(
    /** Non-blank reference proving this result came from persisted Wallet Unit evidence. */
    val evidenceId: String,
    val profile: String,
    val format: String,
    val attestationExpiresAtEpochSeconds: Long,
    val clientStatus: WalletInstanceClientStatusEvidence,
    val trust: WalletInstanceTrustEvidence,
    val walletInstanceId: String? = null,
    val walletProvider: String? = null,
    val walletSolution: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val signerCertificateProfile: String? = null,
    val claims: Map<String, JsonElement> = emptyMap(),
)

data class WalletInstanceClientStatusEvidence(
    val statusListUri: String,
    val index: String,
    val status: String = STATUS_VALID,
    val revoked: Boolean = false,
    val maintenanceExpiresAtEpochSeconds: Long? = null,
    val failureReason: String? = null,
) {
    companion object {
        const val STATUS_VALID = "VALID"
    }
}

data class WalletInstanceTrustEvidence(
    val trusted: Boolean,
    val decision: String = DECISION_TRUSTED,
    val expiresAtEpochSeconds: Long? = null,
    val revokedAtEpochSeconds: Long? = null,
    val signerCertificateProfile: String? = null,
) {
    companion object {
        const val DECISION_TRUSTED = "TRUSTED"
    }
}

object WalletInstanceAttestationTokenClaims {
    const val CLIENT_STATUS = "client_status"
    const val WALLET_INSTANCE_ATTESTATION = "wallet_instance_attestation"
}

fun WalletInstanceAttestationEvidence.accessTokenClaims(): Map<String, Any> =
    mapOf(
        WalletInstanceAttestationTokenClaims.CLIENT_STATUS to
            buildJsonObject {
                put("status_list_uri", clientStatus.statusListUri)
                put("index", clientStatus.index)
                put("status", clientStatus.status)
                put("revoked", clientStatus.revoked)
                clientStatus.maintenanceExpiresAtEpochSeconds?.let { put("maintenance_expires_at", it) }
            },
        WalletInstanceAttestationTokenClaims.WALLET_INSTANCE_ATTESTATION to
            buildJsonObject {
                put("evidence_id", evidenceId)
                put("profile", profile)
                put("format", format)
                put("expires_at", attestationExpiresAtEpochSeconds)
                walletInstanceId?.let { put("wallet_instance_id", it) }
                walletProvider?.let { put("wallet_provider", it) }
                walletSolution?.let { put("wallet_solution", it) }
                walletUnitId?.let { put("wallet_unit_id", it) }
                walletAccountId?.let { put("wallet_account_id", it) }
                signerCertificateProfile?.let { put("signer_certificate_profile", it) }
                put(
                    "trust",
                    buildJsonObject {
                        put("trusted", trust.trusted)
                        put("decision", trust.decision)
                        trust.expiresAtEpochSeconds?.let { put("expires_at", it) }
                        trust.signerCertificateProfile?.let { put("signer_certificate_profile", it) }
                    },
                )
                if (claims.isNotEmpty()) {
                    put(
                        "claims",
                        buildJsonObject {
                            claims.forEach { (key, value) -> put(key, value) }
                        },
                    )
                }
            },
    )

/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.unit

import kotlinx.serialization.Serializable

@Serializable
enum class EudiWalletTrustEvidenceKind {
    WALLET_PROVIDER,
    WALLET_SOLUTION,
    ATTESTATION_SIGNER,
    STATUS_REFERENCE,
}

@Serializable
enum class EudiWalletTrustDecision {
    TRUSTED,
    UNTRUSTED,
    MISSING,
    EXPIRED,
    REVOKED,
    UNSUPPORTED_PROFILE,
}

@Serializable
data class EudiWalletTrustEvidence(
    val trusted: Boolean,
    val kind: EudiWalletTrustEvidenceKind? = null,
    val decision: EudiWalletTrustDecision? = null,
    val trustListUri: String? = null,
    val trustAnchor: String? = null,
    val loteRole: String? = null,
    val serviceType: String? = null,
    val serviceStatus: String? = null,
    val serviceDigitalIdentity: String? = null,
    val matchedCertificateFingerprint: String? = null,
    val signingCertificateProfile: String? = null,
    val validationTimeEpochSeconds: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
    val revokedAtEpochSeconds: Long? = null,
    val walletSolutionName: String? = null,
    val walletSolutionVersion: String? = null,
    val certificationEvidenceRef: String? = null,
    val failureReason: String? = null,
    val provenance: Map<String, String> = emptyMap(),
)

@Serializable
data class ResolveWalletProviderTrustRequest(
    val providerId: String,
    val signerCertificateChain: List<String> = emptyList(),
    val requiredLoteRole: String? = null,
    val requiredServiceType: String? = null,
    val requiredCertificateProfile: String? = null,
    val validationTimeEpochSeconds: Long? = null,
)

@Serializable
data class ResolveWalletSolutionTrustRequest(
    val walletSolution: WalletSolutionRef,
    val requiredLoteRole: String? = null,
    val requiredServiceType: String? = null,
    val validationTimeEpochSeconds: Long? = null,
)

@Serializable
data class WalletAttestationStatusEvidence(
    val statusListUri: String,
    val index: String,
    val revoked: Boolean = false,
    val resolvedAtEpochSeconds: Long? = null,
    val maintenanceExpiresAtEpochSeconds: Long? = null,
    val failureReason: String? = null,
    val provenance: Map<String, String> = emptyMap(),
)

interface EudiWalletTrustService {
    suspend fun resolveWalletProviderTrust(request: ResolveWalletProviderTrustRequest): EudiWalletTrustEvidence
    suspend fun resolveWalletSolutionTrust(request: ResolveWalletSolutionTrustRequest): EudiWalletTrustEvidence
}

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
data class WalletInstanceRef(
    val id: String,
    val holderBindingRef: String? = null,
    val integrityEvidenceRef: String? = null,
    val evidence: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletSolutionRef(
    val name: String,
    val version: String,
    val informationLink: String? = null,
    val vendor: String? = null,
    val certification: WalletSolutionCertificationEvidence? = null,
)

@Serializable
data class WalletSolutionCertificationEvidence(
    val certificationId: String,
    val scheme: String,
    val assuranceLevel: String,
    val issuer: String,
    val evidenceRef: String? = null,
    val validFrom: String? = null,
    val validUntil: String? = null,
)

@Serializable
data class WalletProviderAttestationSignerRef(
    val signerId: String,
    val issuer: String,
    val keyId: String? = null,
    val signingAlgorithm: String = "ES256",
    val signerProfile: String = "REMOTE_WSCD",
    val certificateChain: List<String> = emptyList(),
    val trustEvidenceRef: String? = null,
)

@Serializable
data class WalletStatusSubjectRef(
    val statusListUri: String,
    val index: String,
    val purpose: String,
    val issuer: String? = null,
)

@Serializable
data class WalletStatusMaintenancePeriod(
    val statusSubject: WalletStatusSubjectRef,
    val maintenanceExpiresAtEpochSeconds: Long,
)

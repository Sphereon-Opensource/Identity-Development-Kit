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
// Do not reorder: declaration order encodes strength (strongest first). LocalWsca's
// key-attestation ceiling check compares ordinals against WscdProfile capabilities.
enum class WalletKeystoreSecurityLevel {
    ISO_18045_HIGH,
    ISO_18045_MODERATE,
    ISO_18045_ENHANCED_BASIC,
    ISO_18045_BASIC,
    NONE,
}

@Serializable
enum class WalletSecureComponentType {
    LOCAL_WSCA,
    LOCAL_WSCD,
    MOBILE_PLATFORM_WSCD,
    WEBCRYPTO_WSCD,
    REMOTE_WSCA,
    REMOTE_WSCD,
    REMOTE_HSM,
    REMOTE_QSCD,
    EXTERNAL_CERTIFIED_PROVIDER,
}

@Serializable
enum class SecureComponentUsage {
    WALLET_ATTESTATION,
    OAUTH_CLIENT_AUTHENTICATION,
    WALLET_CREDENTIAL_PROOF,
    EIDAS_SIGNATURE,
    EIDAS_SEAL,
}

@Serializable
data class WalletSecureComponentWalletBinding(
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val walletInstanceId: String? = null,
    val evidence: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletSecureCryptographicApplicationRef(
    val id: String,
    val version: String? = null,
    val componentType: WalletSecureComponentType,
    val usages: Set<SecureComponentUsage> = emptySet(),
    val walletBinding: WalletSecureComponentWalletBinding? = null,
    val evidence: WalletSecureComponentEvidence = WalletSecureComponentEvidence(),
)

@Serializable
data class WalletSecureCryptographicDeviceRef(
    val id: String,
    val providerId: String? = null,
    val componentType: WalletSecureComponentType,
    val securityLevel: WalletKeystoreSecurityLevel,
    val usages: Set<SecureComponentUsage> = emptySet(),
    val walletBinding: WalletSecureComponentWalletBinding? = null,
    val certification: WalletSolutionCertificationEvidence? = null,
    val evidence: WalletSecureComponentEvidence = WalletSecureComponentEvidence(),
)

@Serializable
data class WalletKeystoreRef(
    val id: String,
    val namespace: String,
    val providerId: String? = null,
    val securityLevel: WalletKeystoreSecurityLevel,
    val componentType: WalletSecureComponentType,
    val usages: Set<SecureComponentUsage> = emptySet(),
    val walletBinding: WalletSecureComponentWalletBinding? = null,
    val evidence: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletSecureComponentEvidence(
    val certificationEvidence: List<WalletSolutionCertificationEvidence> = emptyList(),
    val privateKeyProtection: WalletPrivateKeyProtectionEvidence? = null,
    val userAuthentication: WalletUserAuthenticationEvidence? = null,
    val provenance: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletUserAuthenticationEvidence(
    val assuranceLevel: String,
    val methods: List<String> = emptyList(),
    val policyRef: String? = null,
    val evidence: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletPrivateKeyProtectionEvidence(
    val nonExportable: Boolean,
    val protectedByUserAuthentication: Boolean,
    val isolationBoundary: String? = null,
    val evidence: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletAttestedKeyRef(
    val keyId: String,
    val algorithm: String,
    val publicKeyJwk: String? = null,
    val keyRef: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val keystore: WalletKeystoreRef? = null,
    val proofOfPossession: String? = null,
    val evidence: Map<String, String> = emptyMap(),
)

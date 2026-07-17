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
enum class WalletRuntime {
    MOBILE,
    WEB,
}

@Serializable
enum class WalletBackendMode {
    NONE,
    REMOTE,
    HYBRID,
}

@Serializable
enum class WalletStorageProfile {
    LOCAL,
    REMOTE,
    HYBRID,
}

@Serializable
enum class WalletKeystoreProfile {
    MOBILE_SECURE_AREA,
    LOCAL_DEVICE,
    WEBCRYPTO,
    REMOTE_WSCD,
    REMOTE_HSM,
    EXTERNAL_PROVIDER,
}

@Serializable
enum class WalletSecureComponentProfile {
    LOCAL_DEVICE_WSCA_WSCD,
    MOBILE_PLATFORM_WSCD,
    WEBCRYPTO_WSCD,
    REMOTE_WSCA,
    REMOTE_WSCD,
    REMOTE_HSM,
    REMOTE_QSCD,
    EXTERNAL_CERTIFIED_PROVIDER,
}

@Serializable
enum class WalletAttestationProfile {
    NONE,
    LOCAL_EVALUATION_REFERENCE,
    TS03_JWT,
}

@Serializable
enum class WalletTrustPolicy {
    NONE,
    LOCAL_EVALUATION,
    EUDI_WALLET_PROVIDER_TRUST_LIST,
}

@Serializable
data class WalletRemoteBackendRef(
    val baseUrl: String,
    val clientId: String? = null,
    val audience: String? = null,
)

@Serializable
data class BrowserSessionBindingPolicy(
    val policyId: String,
    val sameSiteRequired: Boolean = true,
    val originBound: Boolean = true,
)

@Serializable
data class WalletUserAuthenticationPolicyRef(
    val policyId: String,
    val assuranceLevel: String,
)

@Serializable
data class WalletHybridPlacement(
    val localKeyRefs: List<String> = emptyList(),
    val remoteKeyRefs: List<String> = emptyList(),
    val localArtifactKinds: List<String> = emptyList(),
    val remoteArtifactKinds: List<String> = emptyList(),
)

@Serializable
data class WalletSetupOptions(
    val runtime: WalletRuntime,
    val backendMode: WalletBackendMode,
    val storageProfile: WalletStorageProfile,
    val keystoreProfile: WalletKeystoreProfile,
    val secureComponentProfile: WalletSecureComponentProfile,
    val attestationProfile: WalletAttestationProfile,
    val trustPolicy: WalletTrustPolicy,
    val remoteBackend: WalletRemoteBackendRef? = null,
    val browserSessionBindingPolicy: BrowserSessionBindingPolicy? = null,
    val userAuthenticationPolicy: WalletUserAuthenticationPolicyRef? = null,
    val hybridPlacement: WalletHybridPlacement? = null,
    val evaluationProfilesEnabled: Boolean = false,
)

@Serializable
data class WalletSetupValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
) {
    fun requireValid() {
        require(valid) { errors.joinToString("; ") }
    }
}

object WalletSetupValidator {
    fun validate(options: WalletSetupOptions): WalletSetupValidationResult {
        val errors = mutableListOf<String>()

        when (options.backendMode) {
            WalletBackendMode.NONE -> {
                if (options.remoteBackend != null) errors += "backendMode=NONE must not configure a remote backend"
                if (options.storageProfile == WalletStorageProfile.REMOTE) errors += "backendMode=NONE cannot use REMOTE storage"
                if (options.keystoreProfile in backendOnlyKeystores) errors += "backendMode=NONE requires a local/mobile keystore profile"
                if (options.secureComponentProfile in backendOnlySecureComponents) errors += "backendMode=NONE requires a local/mobile secure component profile"
                if (options.attestationProfile == WalletAttestationProfile.TS03_JWT) {
                    errors += "backendMode=NONE cannot request production TS03 wallet-provider attestations"
                }
            }

            WalletBackendMode.REMOTE -> {
                if (options.remoteBackend == null) errors += "backendMode=REMOTE requires remoteBackend"
                if (options.keystoreProfile !in backendOnlyKeystores) errors += "backendMode=REMOTE requires a remote keystore/WSCD profile"
                if (options.secureComponentProfile !in backendOnlySecureComponents) errors += "backendMode=REMOTE requires a remote WSCA/WSCD profile"
            }

            WalletBackendMode.HYBRID -> {
                if (options.remoteBackend == null) errors += "backendMode=HYBRID requires remoteBackend"
                val placement = options.hybridPlacement
                if (placement == null) {
                    errors += "backendMode=HYBRID requires explicit hybridPlacement"
                } else if (placement.localKeyRefs.isEmpty() && placement.remoteKeyRefs.isEmpty() && placement.localArtifactKinds.isEmpty() && placement.remoteArtifactKinds.isEmpty()) {
                    errors += "backendMode=HYBRID requires at least one explicit local or remote placement"
                }
            }
        }

        if (options.runtime == WalletRuntime.WEB) {
            if (isLocalOssWebProfile(options)) {
                if (options.browserSessionBindingPolicy == null) errors += "runtime=WEB local OSS profile requires browserSessionBindingPolicy"
                if (options.userAuthenticationPolicy == null) errors += "runtime=WEB local OSS profile requires userAuthenticationPolicy"
            } else {
                if (options.backendMode != WalletBackendMode.REMOTE) errors += "runtime=WEB requires either backendMode=REMOTE or an explicit local OSS WSCD profile"
                if (options.remoteBackend == null) errors += "runtime=WEB remote profile requires remoteBackend"
                if (options.browserSessionBindingPolicy == null) errors += "runtime=WEB remote profile requires browserSessionBindingPolicy"
                if (options.userAuthenticationPolicy == null) errors += "runtime=WEB remote profile requires userAuthenticationPolicy"
                if (options.secureComponentProfile !in backendOnlySecureComponents) errors += "runtime=WEB remote profile requires a remote WSCA/WSCD secure component profile"
            }
        }

        if (!options.evaluationProfilesEnabled) {
            if (options.attestationProfile == WalletAttestationProfile.LOCAL_EVALUATION_REFERENCE) errors += "LOCAL_EVALUATION_REFERENCE attestation requires evaluationProfilesEnabled=true"
            if (options.trustPolicy == WalletTrustPolicy.LOCAL_EVALUATION) errors += "LOCAL_EVALUATION trust policy requires evaluationProfilesEnabled=true"
        }

        return WalletSetupValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    private val backendOnlyKeystores =
        setOf(
            WalletKeystoreProfile.REMOTE_WSCD,
            WalletKeystoreProfile.REMOTE_HSM,
            WalletKeystoreProfile.EXTERNAL_PROVIDER,
        )

    private val backendOnlySecureComponents =
        setOf(
            WalletSecureComponentProfile.REMOTE_WSCA,
            WalletSecureComponentProfile.REMOTE_WSCD,
            WalletSecureComponentProfile.REMOTE_HSM,
            WalletSecureComponentProfile.REMOTE_QSCD,
            WalletSecureComponentProfile.EXTERNAL_CERTIFIED_PROVIDER,
        )

    private val localOssWebKeystores =
        setOf(
            WalletKeystoreProfile.WEBCRYPTO,
            WalletKeystoreProfile.LOCAL_DEVICE,
        )

    private val localOssWebSecureComponents =
        setOf(
            WalletSecureComponentProfile.WEBCRYPTO_WSCD,
            WalletSecureComponentProfile.LOCAL_DEVICE_WSCA_WSCD,
        )

    private fun isLocalOssWebProfile(options: WalletSetupOptions): Boolean =
        options.runtime == WalletRuntime.WEB &&
            options.backendMode == WalletBackendMode.NONE &&
            options.storageProfile == WalletStorageProfile.LOCAL &&
            options.keystoreProfile in localOssWebKeystores &&
            options.secureComponentProfile in localOssWebSecureComponents &&
            options.remoteBackend == null
}

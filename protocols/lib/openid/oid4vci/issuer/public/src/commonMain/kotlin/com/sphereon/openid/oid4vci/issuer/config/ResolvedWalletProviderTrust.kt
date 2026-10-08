/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Admission decision resolved from persisted Trust Domain attachments. */
@Serializable
enum class WalletProviderAdmission { RESTRICTED, UNRESTRICTED }

/** Closed registry key for every supported wallet-provider verifier mechanism. */
@Serializable
enum class WalletProviderTrustMechanism { JWK, ISSUER, X509, ETSI_TS_119_602 }

/**
 * Public, reusable verifier material. It is deliberately detached from configuration/YAML and
 * cannot carry filesystem locations, private key references, or secret material.
 */
@Serializable
sealed interface WalletProviderVerifierMaterial {
    val mechanism: WalletProviderTrustMechanism

    @Serializable
    @SerialName("jwk")
    data class Jwk(
        val publicJwk: com.sphereon.crypto.core.jose.Jwk,
        val fingerprint: String,
    ) : WalletProviderVerifierMaterial {
        override val mechanism: WalletProviderTrustMechanism = WalletProviderTrustMechanism.JWK

        init {
            require(publicJwk.kty.value.lowercase() != "oct") {
                "resolved wallet-provider JWK material must not be symmetric"
            }
            require(
                publicJwk.d == null &&
                    publicJwk.p == null &&
                    publicJwk.q == null &&
                    publicJwk.dP == null &&
                    publicJwk.dQ == null &&
                    publicJwk.qInv == null &&
                    publicJwk.k == null,
            ) { "resolved wallet-provider JWK material must be public" }
            requireSha256(fingerprint, "JWK fingerprint")
        }
    }

    @Serializable
    @SerialName("issuer")
    data class Issuer(val issuer: String) : WalletProviderVerifierMaterial {
        override val mechanism: WalletProviderTrustMechanism = WalletProviderTrustMechanism.ISSUER

        init {
            requireCanonicalHttpsUri(issuer, "wallet-provider issuer")
        }
    }

    @Serializable
    @SerialName("x509")
    data class X509(
        /** Standard-base64 DER certificate, never a path or a private-key reference. */
        val certificateDerBase64: String,
        val fingerprint: String,
    ) : WalletProviderVerifierMaterial {
        override val mechanism: WalletProviderTrustMechanism = WalletProviderTrustMechanism.X509

        init {
            requireBase64(certificateDerBase64, "X.509 certificate")
            requireSha256(fingerprint, "X.509 certificate fingerprint")
        }
    }

    @Serializable
    @SerialName("etsi_ts_119_602")
    data class Ts119602(
        val providerId: String,
        val serviceTypeIdentifier: String,
        val snapshotId: String,
        val artifactDigest: String,
        val certificateDerBase64: String,
        val certificateFingerprint: String,
    ) : WalletProviderVerifierMaterial {
        override val mechanism: WalletProviderTrustMechanism = WalletProviderTrustMechanism.ETSI_TS_119_602

        init {
            require(providerId.isNotBlank() && providerId == providerId.trim()) { "TS 119 602 provider ID must be canonical" }
            require(serviceTypeIdentifier == ETSI_TS_119_602_WALLET_PROVIDER_SERVICE_TYPE) {
                "TS 119 602 material must be a wallet-provider service"
            }
            require(snapshotId.isNotBlank() && snapshotId == snapshotId.trim()) { "TS 119 602 snapshot ID must be canonical" }
            requireSha256(artifactDigest, "TS 119 602 artifact digest")
            requireBase64(certificateDerBase64, "TS 119 602 service certificate")
            requireSha256(certificateFingerprint, "TS 119 602 certificate fingerprint")
        }
    }
}

/** Exact policy and public material selected for one persisted credential-configuration request. */
@Serializable
data class ResolvedWalletProviderTrust(
    val policyDigest: String,
    val admission: WalletProviderAdmission,
    val verifierMaterials: List<WalletProviderVerifierMaterial>,
    val requireWalletUnitEvidence: Boolean,
) {
    init {
        requireSha256(policyDigest, "wallet-provider policy digest")
        require(verifierMaterials.distinct() == verifierMaterials) { "wallet-provider verifier material must be unique" }
        require(admission != WalletProviderAdmission.RESTRICTED || verifierMaterials.isNotEmpty()) {
            "restricted wallet-provider trust requires verifier material"
        }
    }
}

/** Persisted OID4VCI resource identities used to resolve attachment precedence. */
@Serializable
data class ResolveWalletProviderTrustArgs(
    val tenantId: String,
    val issuerInstanceId: String,
    val issuanceTemplateResourceId: String,
    val credentialConfigurationId: String,
) {
    init {
        requireCanonicalId(tenantId, "tenant ID")
        requireCanonicalId(issuerInstanceId, "issuer instance ID")
        requireCanonicalId(issuanceTemplateResourceId, "issuance-template resource ID")
        requireCanonicalId(credentialConfigurationId, "credential-configuration ID")
    }
}

/** Production deployments bind this SPI to their persisted Trust Domain service. */
fun interface WalletProviderTrustResolver {
    suspend fun resolve(args: ResolveWalletProviderTrustArgs): IdkResult<ResolvedWalletProviderTrust, IdkError>
}

const val ETSI_TS_119_602_WALLET_PROVIDER_SERVICE_TYPE: String =
    "http://uri.etsi.org/TrstSvc/Svctype/WalletProvider"

private val SHA256 = Regex("^sha256:[0-9a-f]{64}$")
private val BASE64 = Regex("^[A-Za-z0-9+/]+={0,2}$")

private fun requireSha256(value: String, role: String) {
    require(SHA256.matches(value)) { "$role must be a SHA-256 digest" }
}

private fun requireCanonicalId(value: String, role: String) {
    require(value.isNotBlank() && value == value.trim()) { "$role must be nonblank and canonical" }
}

private fun requireCanonicalHttpsUri(value: String, role: String) {
    require(value.startsWith("https://") && value == value.trim() && value.none(Char::isWhitespace)) {
        "$role must be a canonical HTTPS URI"
    }
}

private fun requireBase64(value: String, role: String) {
    require(value.isNotBlank() && value.length % 4 == 0 && BASE64.matches(value)) {
        "$role must be standard-base64 DER"
    }
}

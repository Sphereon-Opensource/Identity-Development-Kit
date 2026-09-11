/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vci.common.impl.attestation

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.openid.oid4vci.issuer.config.ResolvedWalletProviderTrust
import com.sphereon.openid.oid4vci.issuer.config.WalletProviderAdmission
import com.sphereon.openid.oid4vci.issuer.config.WalletProviderVerifierMaterial
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Closed, reusable view of the verifier mechanisms admitted by one persisted attachment.
 * Presented material is accepted only for the explicit unrestricted mode; restricted mode
 * always returns the exact public material selected by Trust Domains.
 */
class WalletProviderTrustMechanismRegistry private constructor(
    private val admission: WalletProviderAdmission,
    private val admittedJwks: List<Jwk>,
    val issuers: Set<String>,
    private val admittedX509AnchorDerCertificates: List<String>,
    val requireWalletUnitEvidence: Boolean,
) {
    fun jwkCandidates(presentedPublicJwk: Jwk?, presentedJwkJson: JsonObject): List<Jwk> =
        when (admission) {
            WalletProviderAdmission.RESTRICTED -> admittedJwks
            WalletProviderAdmission.UNRESTRICTED -> presentedPublicJwk
                ?.takeIf(Jwk::isPublicVerifierMaterial)
                ?.takeIf { !presentedJwkJson.hasPrivateOrSymmetricJwkMaterial() }
                ?.let(::listOf)
                .orEmpty()
        }

    fun x509AnchorDerCertificates(presentedChainRootDerBase64: String?): List<String> =
        when (admission) {
            WalletProviderAdmission.RESTRICTED -> admittedX509AnchorDerCertificates
            WalletProviderAdmission.UNRESTRICTED -> presentedChainRootDerBase64?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
        }

    fun x509AnchorPemCertificates(presentedChainRootDerBase64: String?): List<String> =
        x509AnchorDerCertificates(presentedChainRootDerBase64).map(::derBase64ToPem)

    companion object {
        fun from(trust: ResolvedWalletProviderTrust): WalletProviderTrustMechanismRegistry {
            val jwks = mutableListOf<Jwk>()
            val issuers = linkedSetOf<String>()
            val x509Anchors = mutableListOf<String>()
            trust.verifierMaterials.forEach { material ->
                when (material) {
                    is WalletProviderVerifierMaterial.Jwk -> jwks += material.publicJwk
                    is WalletProviderVerifierMaterial.Issuer -> issuers += material.issuer
                    is WalletProviderVerifierMaterial.X509 -> x509Anchors += material.certificateDerBase64
                    is WalletProviderVerifierMaterial.Ts119602 -> x509Anchors += material.certificateDerBase64
                }
            }
            return WalletProviderTrustMechanismRegistry(
                trust.admission,
                jwks.distinct(),
                issuers,
                x509Anchors.distinct(),
                trust.requireWalletUnitEvidence,
            )
        }
    }
}

private fun derBase64ToPem(value: String): String =
    "-----BEGIN CERTIFICATE-----\n" + value.chunked(64).joinToString("\n") + "\n-----END CERTIFICATE-----"

private fun Jwk.isPublicVerifierMaterial(): Boolean =
    kty.value.lowercase() != "oct" &&
        d == null &&
        p == null &&
        q == null &&
        dP == null &&
        dQ == null &&
        qInv == null &&
        k == null

private fun JsonObject.hasPrivateOrSymmetricJwkMaterial(): Boolean {
    val privateMembers = setOf("d", "k", "p", "q", "dp", "dq", "qi", "qinv", "oth")
    if (keys.any { it.lowercase() in privateMembers }) return true
    val keyType = entries.firstOrNull { (name, _) -> name.equals("kty", ignoreCase = true) }?.value
    return (keyType as? JsonPrimitive)?.contentOrNull?.equals("oct", ignoreCase = true) == true
}

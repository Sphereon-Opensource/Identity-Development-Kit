/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationPolicyProvider
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationProvenance
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationRequest
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResolutionPlan
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResolver
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResult
import com.sphereon.wallet.interaction.admitForIssuer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Resolves issuer verification keys only from a deployment-admitted plan, using the shared
 * external-identifier services for parsing, transport, and X.509 validation.
 */
class ExternalIdentifierWalletIssuerAuthenticationResolver(
    private val policyProvider: WalletIssuerAuthenticationPolicyProvider,
    private val externalIdentifierService: ExternalIdentifierService,
) : WalletIssuerAuthenticationResolver {
    override suspend fun resolve(input: WalletIssuerAuthenticationRequest): WalletIssuerAuthenticationResult? {
        val requestedIssuer = input.counterparty.identifier
        val policy = policyProvider.policyFor(requestedIssuer, input.protocol) ?: return null
        if (policy.issuer != requestedIssuer || policy.protocol != input.protocol) return null

        val normalized = mutableListOf<JsonObject>()
        val provenance = mutableListOf<WalletIssuerAuthenticationProvenance>()
        for (plan in policy.plans) {
            if (plan.issuer != requestedIssuer) return null
            val resolved = resolvePlan(plan) ?: return null
            normalized += resolved.first
            provenance += resolved.second
        }
        if (normalized.isEmpty()) return null
        val kids = normalized.mapNotNull { it["kid"]?.toString() }
        if (kids.size != normalized.size || kids.distinct().size != kids.size) return null

        return WalletIssuerAuthenticationResult(
            issuer = requestedIssuer,
            trustedJwks = kotlinx.serialization.json.buildJsonObject { put("keys", kotlinx.serialization.json.JsonArray(normalized)) },
            provenance = provenance,
        ).admitForIssuer(requestedIssuer)
    }

    private suspend fun resolvePlan(
        plan: WalletIssuerAuthenticationResolutionPlan,
    ): Pair<List<JsonObject>, WalletIssuerAuthenticationProvenance>? {
        return when (plan) {
            is WalletIssuerAuthenticationResolutionPlan.PinnedJwk -> {
                if (plan.jwk.hasCertificateMaterial() || plan.jwk.hasPrivateMaterial()) return null
                normalizeExternalResult(
                    externalIdentifierService.resolve(ExternalIdentifierJwkOpts(plan.jwk)).getOrNull()
                        ?: return null,
                    plan.reference,
                    requireJwkResult = true,
                    expectedJwk = plan.jwk,
                )
            }
            is WalletIssuerAuthenticationResolutionPlan.PinnedJwks -> {
                val keys = mutableListOf<JsonObject>()
                for (jwk in plan.jwks) {
                    if (jwk.hasCertificateMaterial() || jwk.hasPrivateMaterial()) return null
                    val result = externalIdentifierService.resolve(ExternalIdentifierJwkOpts(jwk)).getOrNull() ?: return null
                    val normalized = normalizeExternalResult(result, plan.reference, requireJwkResult = true, expectedJwk = jwk) ?: return null
                    keys += normalized.first
                }
                keys to WalletIssuerAuthenticationProvenance("pinned-jwks", plan.reference)
            }
            is WalletIssuerAuthenticationResolutionPlan.HttpsJwks -> {
                if (!plan.url.startsWith("https://", ignoreCase = true)) return null
                val result = externalIdentifierService.resolve(
                    ExternalIdentifierJwksUrlOpts(plan.url, lookup = AdditionalIdentifierLookup(kid = plan.requestedKid)),
                ).getOrNull() ?: return null
                normalizeExternalResult(result, plan.reference, requireJwksResult = true, expectedUrl = plan.url, expectedKid = plan.requestedKid)
            }
            is WalletIssuerAuthenticationResolutionPlan.X5c -> {
                if (!plan.verify || plan.trustAnchors.isEmpty() || plan.issuerBinding != plan.issuer) return null
                val result = externalIdentifierService.resolve(
                    ExternalIdentifierX5cOpts(
                        identifier = plan.chain,
                        verify = true,
                        trustAnchors = plan.trustAnchors,
                        verificationTime = plan.verificationTime,
                    ),
                ).getOrNull() ?: return null
                val x5c = result as? ExternalIdentifierResult.X5c ?: return null
                if (x5c.verificationResult.error ||
                    x5c.verificationResult.critical ||
                    x5c.verificationResult.certificateChain.isEmpty() ||
                    x5c.verificationResult.publicKey == null
                ) return null
                normalizeExternalResult(
                    x5c,
                    plan.reference,
                    requireX5cResult = true,
                    expectedChain = plan.chain,
                    expectedTrustAnchors = plan.trustAnchors,
                    expectedVerificationTime = plan.verificationTime,
                    expectedKid = plan.keyId,
                )
            }
        }
    }

    private fun normalizeExternalResult(
        result: ExternalIdentifierResult,
        reference: String,
        requireJwkResult: Boolean = false,
        requireJwksResult: Boolean = false,
        requireX5cResult: Boolean = false,
        expectedJwk: JwkType? = null,
        expectedUrl: String? = null,
        expectedKid: String? = null,
        expectedChain: List<String>? = null,
        expectedTrustAnchors: List<String>? = null,
        expectedVerificationTime: String? = null,
    ): Pair<List<JsonObject>, WalletIssuerAuthenticationProvenance>? {
        if (requireJwkResult && result !is ExternalIdentifierResult.Jwk) return null
        if (requireJwksResult && result !is ExternalIdentifierResult.JwksUrl) return null
        if (requireX5cResult && result !is ExternalIdentifierResult.X5c) return null
        if (expectedJwk != null) {
            val actual = result as? ExternalIdentifierResult.Jwk ?: return null
            val expectedOpts = ExternalIdentifierJwkOpts(expectedJwk)
            if (!sameJwkOpts(actual.identifierOpts, expectedOpts) || actual.x5c != null || actual.jwks.size != 1 ||
                !sameJwk(actual.jwks.single().key, expectedJwk) ||
                !sameJwk(actual.identifierOpts.identifier, expectedJwk) || !sameJwk(actual.keyInfo.key, expectedJwk) ||
                actual.jwks.single().kid != expectedJwk.kid || actual.keyInfo.kid != expectedJwk.kid
            ) return null
        }
        if (expectedUrl != null) {
            val actual = result as? ExternalIdentifierResult.JwksUrl ?: return null
            val expectedOpts = ExternalIdentifierJwksUrlOpts(
                expectedUrl,
                lookup = AdditionalIdentifierLookup(kid = expectedKid),
            )
            if (!sameJwksUrlOpts(actual.identifierOpts, expectedOpts) || actual.jwksUrl != expectedUrl ||
                actual.identifierOpts.identifier != expectedUrl || actual.selectedKid != expectedKid
            ) return null
            val selectedMatches = actual.jwks.count {
                it.kid == actual.keyInfo.kid && sameJwk(it.key, actual.keyInfo.key)
            }
            if (selectedMatches != 1) return null
        }
        if (expectedChain != null) {
            val actual = result as? ExternalIdentifierResult.X5c ?: return null
            val expectedOpts = ExternalIdentifierX5cOpts(
                identifier = expectedChain,
                verify = true,
                trustAnchors = expectedTrustAnchors,
                verificationTime = expectedVerificationTime,
            )
            if (!sameX5cOpts(actual.identifierOpts, expectedOpts) || actual.x5c != expectedChain ||
                actual.identifierOpts.identifier != expectedChain || actual.identifierOpts.verify != true ||
                actual.identifierOpts.trustAnchors != expectedTrustAnchors ||
                actual.identifierOpts.verificationTime != expectedVerificationTime
            ) return null
            if (expectedKid != null && actual.keyInfo.kid != expectedKid) return null
            val verifiedPublicKey = actual.verificationResult.publicKey ?: return null
            if (!samePublicKeyMaterial(actual.keyInfo.key, verifiedPublicKey)) return null
            if (actual.jwks.count { samePublicKeyMaterial(it.key, actual.keyInfo.key) } != 1) return null
        }
        val keyInfos = when (result) {
            is ExternalIdentifierResult.X5c -> listOf(result.keyInfo)
            is ExternalIdentifierResult.JwksUrl -> {
                if (expectedKid != null) {
                    result.jwks.filter { it.kid == expectedKid }.takeIf { it.size == 1 } ?: return null
                } else {
                    result.jwks.toList()
                }
            }
            else -> result.jwks.toList()
        }
        val keys = keyInfos.map { rawExternalKey(it.key, it.kid ?: expectedKid) ?: return null }
        if (keys.isEmpty()) return null
        val source = when (result) {
            is ExternalIdentifierResult.Jwk -> "pinned-jwk"
            is ExternalIdentifierResult.JwksUrl -> "https-jwks"
            is ExternalIdentifierResult.X5c -> "validated-x5c"
            else -> return null
        }
        return keys to WalletIssuerAuthenticationProvenance(source, reference)
    }

    private fun sameJwk(first: JwkType, second: JwkType): Boolean =
        Json.encodeToJsonElement(com.sphereon.crypto.core.jose.Jwk.serializer(), com.sphereon.crypto.core.jose.Jwk.from(first)) ==
            Json.encodeToJsonElement(com.sphereon.crypto.core.jose.Jwk.serializer(), com.sphereon.crypto.core.jose.Jwk.from(second))

    private fun samePublicKeyMaterial(first: JwkType, second: JwkType): Boolean =
        runCatching {
            generateJwkThumbprint(first.toPublicKey()) == generateJwkThumbprint(second.toPublicKey())
        }.getOrNull() == true

    private fun sameJwkOpts(
        actual: ExternalIdentifierJwkOpts,
        expected: ExternalIdentifierJwkOpts,
    ): Boolean =
        actual.method == expected.method && actual.context == expected.context && actual.lookup == expected.lookup &&
            sameJwk(actual.identifier, expected.identifier) && actual.x5c == expected.x5c

    private fun sameJwksUrlOpts(
        actual: ExternalIdentifierJwksUrlOpts,
        expected: ExternalIdentifierJwksUrlOpts,
    ): Boolean =
        actual.method == expected.method && actual.identifier == expected.identifier &&
            actual.context == expected.context && actual.lookup == expected.lookup

    private fun sameX5cOpts(
        actual: ExternalIdentifierX5cOpts,
        expected: ExternalIdentifierX5cOpts,
    ): Boolean =
        actual.method == expected.method && actual.identifier == expected.identifier &&
            actual.context == expected.context && actual.lookup == expected.lookup &&
            actual.verify == expected.verify && actual.verificationTime == expected.verificationTime &&
            actual.trustAnchors == expected.trustAnchors

    private fun rawExternalKey(key: JwkType, resolvedKid: String?): JsonObject? {
        val kid = resolvedKid ?: key.kid ?: return null
        if (kid.isBlank()) return null
        val encoded = Json.encodeToJsonElement(com.sphereon.crypto.core.jose.Jwk.serializer(), com.sphereon.crypto.core.jose.Jwk.from(key)).jsonObject
        return buildJsonObject {
            encoded.forEach { (name, value) -> put(name, value) }
            put("kid", kid)
        }
    }

    private fun JwkType.hasCertificateMaterial(): Boolean =
        x5c != null || x5t != null || x5u != null || x5t_S256 != null

    private fun JwkType.hasPrivateMaterial(): Boolean =
        d != null || p != null || q != null || dP != null || dQ != null || qInv != null || k != null
}

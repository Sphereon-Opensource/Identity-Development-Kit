/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.utils.ParsedDid
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.wallet.interaction.WalletCounterpartyRole
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationProvenance
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationRequest
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResolver
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Resolves issuer signing authority from the issuer DID document.
 *
 * Only verification methods explicitly present in the DID document's
 * `assertionMethod` relationship are returned.  In particular, authentication,
 * key-agreement, credential-subject, JWT-header, and transport-supplied key
 * material are deliberately outside this resolver's trust boundary.
 */
class DidWalletIssuerAuthenticationResolver(
    private val didResolverRegistry: DidResolverRegistry,
    private val now: () -> Instant = { Clock.System.now() },
) : WalletIssuerAuthenticationResolver {
    override suspend fun resolve(input: WalletIssuerAuthenticationRequest): WalletIssuerAuthenticationResult? {
        if (input.protocol != com.sphereon.wallet.interaction.WalletProtocol.OID4VCI ||
            input.counterparty.role != WalletCounterpartyRole.ISSUER
        ) {
            return null
        }

        val parsed = ParsedDid.tryParse(input.counterparty.identifier.trim()) ?: return null
        val did = "did:${parsed.method}:${parsed.methodSpecificId}"
        val resolution = didResolverRegistry.resolve(did, DidResolutionOptions()).getOrElse { return null }
        if (!resolution.isSuccess()) return null
        val document = resolution.didDocument ?: return null
        if (document.id != did || resolution.didDocumentMetadata.deactivated == true) return null
        val assertionMethods = document.assertionMethod.orEmpty()
        if (assertionMethods.isEmpty()) return null

        val methods = mutableListOf<VerificationMethod>()
        for (relationship in assertionMethods) {
            methods += relationship.resolveVerificationMethod(document, didResolverRegistry) ?: return null
        }
        if (methods.map { it.id }.distinct().size != methods.size) return null
        val currentTime = now()
        val keys = methods.map { it.toIssuerJwk(did, currentTime) ?: return null }

        return WalletIssuerAuthenticationResult(
            issuer = did,
            trustedJwks = JsonObject(mapOf("keys" to JsonArray(keys))),
            provenance =
                listOf(
                    WalletIssuerAuthenticationProvenance(
                        source = "did.assertionMethod",
                        reference = did,
                    ),
                ),
        )
    }

    private suspend fun VerificationMethodOrReference.resolveVerificationMethod(
        document: com.sphereon.did.models.DidDocument,
        registry: DidResolverRegistry,
    ): VerificationMethod? {
        embedded?.let { return it }
        val reference = reference ?: return null
        val absoluteReference =
            if (reference.startsWith("did:")) {
                reference
            } else {
                "${document.id}#${reference.removePrefix("#")}"
            }
        return registry
            .dereference(absoluteReference)
            .getOrElse { return null }
            .takeIf { it.isSuccess() }
            ?.verificationMethod
            ?.takeIf { it.id == absoluteReference }
    }

    private fun VerificationMethod.toIssuerJwk(did: String, currentTime: Instant): JsonObject? {
        if (!id.startsWith("$did#")) return null
        if (controller != did) return null
        if (revokedAt != null || expiresAt?.let { it <= currentTime } == true) return null
        val key = publicKeyJwk ?: return null
        if (!key.isUsableAsPublicAsymmetricKey()) return null
        if (key.hasPrivateMaterial()) return null
        if (!key.hasSigningPurpose()) return null
        val publicKey = key.toPublicKey()
        val encoded = Json.encodeToJsonElement(Jwk.serializer(), publicKey).jsonObject
        // The DID verification-method identifier is the authoritative kid. Never
        // preserve a key alias or certificate material supplied by another layer
        // of the wallet. DID assertion authority is represented by public JWK
        // parameters and the DID VM identifier only.
        return buildJsonObject {
            encoded.forEach { (name, value) ->
                if (name !in setOf("kid", "x5c", "x5t", "x5u", "x5t#S256")) put(name, value)
            }
            put("kid", JsonPrimitive(id))
        }
    }

    private fun Jwk.isUsableAsPublicAsymmetricKey(): Boolean =
        when (kty) {
            JwaKeyType.EC -> crv in setOf(JwaCurve.P_256, JwaCurve.P_384, JwaCurve.P_521, JwaCurve.Secp256k1) && x != null && y != null
            JwaKeyType.RSA -> n != null && e != null
            JwaKeyType.OKP -> crv in setOf(JwaCurve.Ed25519, JwaCurve.Ed448) && x != null
            else -> false
        }

    private fun Jwk.hasPrivateMaterial(): Boolean =
        d != null || p != null || q != null || dP != null || dQ != null || qInv != null || k != null

    private fun Jwk.hasSigningPurpose(): Boolean {
        if (use != null && use != "sig") return false
        val operations = key_ops ?: return true
        return operations.isNotEmpty() && operations.all { it == JoseKeyOperations.VERIFY }
    }
}

/**
 * Ordered extension seam for issuer-authentication sources.
 *
 * DID resolution is the built-in source. Deployments may add a validated
 * federation/JWKS or certificate resolver without changing wallet interaction
 * code. A delegate that cannot establish authority returns null; no URL guessing
 * or implicit well-known fetching is performed here.
 */
class CompositeWalletIssuerAuthenticationResolver(
    private val delegates: List<WalletIssuerAuthenticationResolver>,
) : WalletIssuerAuthenticationResolver {
    override suspend fun resolve(input: WalletIssuerAuthenticationRequest): WalletIssuerAuthenticationResult? {
        delegates.forEach { delegate ->
            delegate.resolve(input)?.let { return it }
        }
        return null
    }
}

/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction

import com.sphereon.crypto.core.jose.AlgorithmType
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JoseKeyOperations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Canonical admission boundary for every issuer-authentication source, including trust plugins.
 * It binds the key set to the expected issuer and emits only public asymmetric signing keys.
 */
fun WalletIssuerAuthenticationResult.admitForIssuer(expectedIssuer: String): WalletIssuerAuthenticationResult? {
    if (issuer != expectedIssuer || provenance.isEmpty()) return null
    if (provenance.any { it.source.isBlank() || it.reference.isBlank() || it.source.lowercase() in FORBIDDEN_SOURCES }) return null
    val values = trustedJwks["keys"] as? JsonArray ?: return null
    val keys = values.map { value ->
        val json = value as? JsonObject ?: return null
        val kid = json["kid"]?.jsonPrimitive?.content?.takeUnless { it.isBlank() } ?: return null
        if (CERTIFICATE_FIELDS.any(json::containsKey)) return null
        val parsed = runCatching { Jwk.fromJsonObject(json) }.getOrNull() ?: return null
        if (parsed.kid != kid) return null
        normalizeAdmissionKey(parsed, kid) ?: return null
    }
    if (keys.isEmpty() || keys.map { it["kid"]!!.jsonPrimitive.content }.distinct().size != keys.size) return null
    return copy(trustedJwks = JsonObject(mapOf("keys" to JsonArray(keys))))
}

private fun normalizeAdmissionKey(key: Jwk, kid: String): JsonObject? {
    if (key.d != null || key.p != null || key.q != null || key.dP != null || key.dQ != null || key.qInv != null || key.k != null) return null
    if (key.use != null && key.use != "sig") return null
    key.alg?.let { algorithm ->
        if (algorithm.type != AlgorithmType.SIGNATURE || algorithm.keyType != key.kty) return null
    }
    if (key.key_ops?.let { it.isEmpty() || it.any { operation -> operation != JoseKeyOperations.VERIFY } } == true) return null
    val usable = when (key.kty) {
        JwaKeyType.EC -> key.crv in setOf(JwaCurve.P_256, JwaCurve.P_384, JwaCurve.P_521, JwaCurve.Secp256k1) && key.x != null && key.y != null
        JwaKeyType.RSA -> key.n != null && key.e != null
        JwaKeyType.OKP -> key.crv in setOf(JwaCurve.Ed25519, JwaCurve.Ed448) && key.x != null
        else -> false
    }
    if (!usable) return null
    val encoded = Json.encodeToJsonElement(Jwk.serializer(), key.toPublicKey()).jsonObject
    return buildJsonObject {
        encoded.forEach { (name, value) -> if (name !in CERTIFICATE_FIELDS) put(name, value) }
        put("kid", kid)
    }
}

private val CERTIFICATE_FIELDS = setOf("x5c", "x5t", "x5u", "x5t#S256")
private val FORBIDDEN_SOURCES = setOf("token", "jwt", "jwt-header", "jwk", "jku", "embedded")

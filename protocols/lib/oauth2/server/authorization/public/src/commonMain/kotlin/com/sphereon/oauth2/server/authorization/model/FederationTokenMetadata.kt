/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.oauth2.server.authorization.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Private AS token metadata, disclosed only through authenticated internal introspection.
 * The oidc namespace is deliberately excluded from public access-token JWT payloads.
 * Never interpret this contract as locally authenticated provenance on an external JWT.
 */
object FederationTokenMetadata {
    const val KEY = "oidc.internal.federation_claims"

    data class Context(
        val upstreamIssuer: String,
        val upstreamSubject: String?,
        val userinfo: Map<String, JsonElement>,
    )

    /** Construct the fixed bag from claims belonging to the verified authorization code. */
    fun fromUserClaims(claims: Map<String, JsonElement>): JsonObject? {
        val issuer = string(claims["upstream_iss"]) ?: return null
        return buildJsonObject {
            put("upstream_iss", JsonPrimitive(issuer))
            string(claims["upstream_sub"])?.let { put("upstream_sub", JsonPrimitive(it)) }
            put("userinfo", JsonObject(filterUserinfo(claims)))
        }
    }

    /** Presence is separate from validity so malformed metadata cannot enable local fallback. */
    fun isFederated(claims: Map<String, JsonElement>): Boolean =
        KEY in claims || "upstream_iss" in claims || "upstream_sub" in claims

    fun read(claims: Map<String, JsonElement>): Context? {
        if (KEY !in claims) {
            // Compatibility with tokens minted before the private nested contract existed.
            val issuer = string(claims["upstream_iss"]) ?: return null
            return Context(issuer, string(claims["upstream_sub"]), filterUserinfo(claims))
        }
        val bag = claims[KEY] as? JsonObject ?: return null
        val issuer = string(bag["upstream_iss"]) ?: return null
        return Context(issuer, string(bag["upstream_sub"]), filterUserinfo((bag["userinfo"] as? JsonObject).orEmpty()))
    }

    /** Refresh rows use a typed JSON string, never a JsonObject hidden in Contextual Any. */
    fun decode(serialized: String): JsonObject? {
        val bag = runCatching { Json.parseToJsonElement(serialized) as? JsonObject }.getOrNull() ?: return null
        val context = read(mapOf(KEY to bag)) ?: return null
        return buildJsonObject {
            put("upstream_iss", JsonPrimitive(context.upstreamIssuer))
            context.upstreamSubject?.let { put("upstream_sub", JsonPrimitive(it)) }
            put("userinfo", JsonObject(context.userinfo))
        }
    }

    fun filterUserinfo(claims: Map<String, JsonElement>): Map<String, JsonElement> =
        claims.filterKeys { it !in PROTOCOL_KEYS && !it.startsWith("oidc.") && !it.startsWith("oid4vci.internal.") }

    private fun string(value: JsonElement?): String? =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private val PROTOCOL_KEYS = setOf(
        "iss", "sub", "aud", "exp", "iat", "nbf", "jti", "auth_time", "nonce", "at_hash", "c_hash",
        "azp", "typ", "sid", "acr", "amr", "scope", "token_type", "client_id", "cnf", "active",
        "authorization_details", "credential_configuration_ids", "credential_identifiers", "issuer_state",
        "upstream_iss", "upstream_sub", "upstream_acr", "upstream_amr", "userinfo", "roles",
        "client_status", "wallet_instance_attestation",
    )
}

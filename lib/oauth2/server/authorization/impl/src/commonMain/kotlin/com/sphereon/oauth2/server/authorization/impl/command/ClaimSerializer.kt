package com.sphereon.oauth2.server.authorization.impl.command

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * Claims that the STS sets itself and must not be overridden by external/additional claims.
 */
val JWT_RESERVED_CLAIMS = setOf(
    "iss", "sub", "aud", "exp", "iat", "nbf", "jti",
    "auth_time", "nonce", "at_hash", "c_hash", "azp", "typ", "sid",
    "acr", "amr", "scope", "token_type", "client_id", "cnf"
)

/**
 * Puts additional claims into a JsonObjectBuilder, skipping reserved JWT claims
 * that the STS controls itself.
 */
fun JsonObjectBuilder.putClaims(claims: Map<String, Any>, reservedClaims: Set<String> = JWT_RESERVED_CLAIMS) {
    claims.forEach { (key, value) ->
        if (key !in reservedClaims) {
            putClaimValue(key, value)
        }
    }
}

/**
 * Serializes a single claim value into the JsonObjectBuilder, handling
 * strings, numbers, booleans, JSON objects, JSON arrays, and lists.
 */
fun JsonObjectBuilder.putClaimValue(key: String, value: Any) {
    when (value) {
        is String -> put(key, value)
        is Number -> put(key, JsonPrimitive(value))
        is Boolean -> put(key, value)
        is JsonObject -> put(key, value)
        is JsonArray -> put(key, value)
        is List<*> -> put(key, buildJsonArray {
            value.forEach { item ->
                when (item) {
                    is String -> add(JsonPrimitive(item))
                    is Number -> add(JsonPrimitive(item))
                    is Boolean -> add(JsonPrimitive(item))
                    is JsonObject -> add(item)
                    is JsonArray -> add(item)
                    else -> item?.toString()?.let { add(JsonPrimitive(it)) }
                }
            }
        })
    }
}

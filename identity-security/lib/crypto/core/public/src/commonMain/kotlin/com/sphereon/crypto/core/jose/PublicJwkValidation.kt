/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.crypto.core.jose

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Checks public asymmetric JWK material without imposing a signing algorithm or key use. */
fun hasWellFormedPublicJwkMaterial(jwk: JsonObject): Boolean {
    if (PRIVATE_FIELDS.any { it in jwk } || "k" in jwk) return false
    if (!KNOWN_STRING_FIELDS.all { field ->
            field !in jwk || (jwk[field] as? JsonPrimitive)?.isString == true
        }) return false

    val operations = jwk["key_ops"]
    if (operations != null) {
        val values = (operations as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }
            ?: return false
        if (values.any { it == null } || values.toSet().size != values.size) return false
    }

    return when (jwk.stringField("kty")) {
        "RSA" -> jwk.noFields("crv", "x", "y") &&
            canonicalBase64Url(jwk.stringField("n")) != null && canonicalBase64Url(jwk.stringField("e")) != null
        "EC" -> {
            val coordinateSize = when (jwk.stringField("crv")) {
                "P-256", "secp256k1" -> 32
                "P-384" -> 48
                "P-521" -> 66
                else -> return false
            }
            jwk.noFields("n", "e") &&
                canonicalBase64Url(jwk.stringField("x"))?.size == coordinateSize &&
                canonicalBase64Url(jwk.stringField("y"))?.size == coordinateSize
        }
        "OKP" -> {
            val coordinateSize = when (jwk.stringField("crv")) {
                "Ed25519", "X25519" -> 32
                "Ed448" -> 57
                "X448" -> 56
                else -> return false
            }
            jwk.noFields("n", "e", "y") && canonicalBase64Url(jwk.stringField("x"))?.size == coordinateSize
        }
        else -> false
    }
}

private val PRIVATE_FIELDS = setOf("d", "p", "q", "dp", "dq", "qi", "oth")
private val KNOWN_STRING_FIELDS = setOf("kid", "kty", "alg", "use", "crv", "n", "e", "x", "y")
private val BASE64URL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-".toSet()

private fun JsonObject.stringField(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.noFields(vararg names: String): Boolean = names.none { it in this }

private fun canonicalBase64Url(value: String?): ByteArray? {
    if (value.isNullOrEmpty() || value.any { it !in BASE64URL_CHARS } || value.length % 4 == 1) return null
    return runCatching { value.decodeFrom(Encoding.BASE64URL) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() && it.encodeToBase64Url() == value }
}

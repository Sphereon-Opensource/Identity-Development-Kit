/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.crypto.core.jose

import com.sphereon.core.api.encodeToBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicJwkValidationTest {
    // RFC 7517 section 3: public P-256 JWK used in the JWS example.
    private val rfcP256 = Json.parseToJsonElement(
        """{"kty":"EC","crv":"P-256","x":"f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU","y":"x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0","kid":"Public key used in JWS spec Appendix A.3 example"}"""
    ).jsonObject

    @Test
    fun rfcP256PublicKeyIsWellFormed() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
    }

    @Test
    fun supportedEcCurvesRequireTheirExactCoordinateLengths() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
        for ((curve, size) in listOf("P-256" to 32, "P-384" to 48, "P-521" to 66, "secp256k1" to 32)) {
            val key = ec(curve, size)
            assertTrue(hasWellFormedPublicJwkMaterial(key), curve)
            assertFalse(hasWellFormedPublicJwkMaterial(key.withField("x", b64(size - 1))), "$curve short x")
            assertFalse(hasWellFormedPublicJwkMaterial(key.withField("y", b64(size + 1))), "$curve long y")
        }
    }

    @Test
    fun supportedOkpCurvesIncludeEncryptionCurvesWithExactLengths() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
        for ((curve, size) in listOf("Ed25519" to 32, "Ed448" to 57, "X25519" to 32, "X448" to 56)) {
            val key = okp(curve, size)
            assertTrue(hasWellFormedPublicJwkMaterial(key), curve)
            assertFalse(hasWellFormedPublicJwkMaterial(key.withField("x", b64(size - 1))), "$curve short x")
            assertFalse(hasWellFormedPublicJwkMaterial(key.withField("x", b64(size + 1))), "$curve long x")
        }
    }

    @Test
    fun missingWrongTypedAndNullKnownFieldsAreRejected() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
        for (field in listOf("kty", "crv", "x", "y")) {
            assertFalse(hasWellFormedPublicJwkMaterial(JsonObject(rfcP256 - field)), "missing $field")
            assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField(field, JsonPrimitive(12))), "numeric $field")
            assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField(field, JsonNull)), "null $field")
        }
        for (field in listOf("kid", "alg", "use")) {
            assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField(field, JsonNull)), "null $field")
            assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField(field, JsonPrimitive(false))), "boolean $field")
        }
    }

    @Test
    fun rsaAndOkpAlsoRequireTheirOwnPublicFieldsWithoutCrossFamilyMaterial() {
        val rsa = JsonObject(mapOf(
            "kty" to JsonPrimitive("RSA"),
            "n" to JsonPrimitive("AQID"),
            "e" to JsonPrimitive("AQAB"),
        ))
        val okp = okp("X25519", 32)
        assertTrue(hasWellFormedPublicJwkMaterial(rsa))
        assertTrue(hasWellFormedPublicJwkMaterial(okp))
        for (field in listOf("n", "e")) {
            assertFalse(hasWellFormedPublicJwkMaterial(JsonObject(rsa - field)), "missing $field")
            assertFalse(hasWellFormedPublicJwkMaterial(rsa.withField(field, JsonNull)), "null $field")
            assertFalse(hasWellFormedPublicJwkMaterial(rsa.withField(field, JsonPrimitive(3))), "numeric $field")
        }
        assertFalse(hasWellFormedPublicJwkMaterial(JsonObject(okp - "x")))
        assertFalse(hasWellFormedPublicJwkMaterial(okp.withField("x", JsonNull)))
        assertFalse(hasWellFormedPublicJwkMaterial(okp.withField("y", b64(32))))
        assertFalse(hasWellFormedPublicJwkMaterial(rsa.withField("crv", JsonPrimitive("P-256"))))
        assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField("kty", JsonPrimitive("unknown"))))
    }

    @Test
    fun canonicalUnpaddedBase64UrlIsRequiredForPublicMaterial() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
        val x = (rfcP256.getValue("x") as JsonPrimitive).content
        for (invalid in listOf(x + "=", x.dropLast(1) + "V", x.replaceFirst('f', '+'), "")) {
            assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField("x", JsonPrimitive(invalid))), invalid)
        }
    }

    @Test
    fun anyPrivateParameterPresenceIncludingJsonNullIsRejected() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
        for (field in listOf("d", "p", "q", "dp", "dq", "qi", "oth")) {
            assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField(field, JsonNull)), field)
        }
    }

    @Test
    fun symmetricMaterialAndCrossFamilyParametersAreNotPublicAsymmetricKeys() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
        assertFalse(hasWellFormedPublicJwkMaterial(JsonObject(mapOf("kty" to JsonPrimitive("oct"), "k" to JsonPrimitive("AQ")))))
        assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField("k", JsonPrimitive("AQ"))))
        assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField("n", JsonPrimitive("AQID"))))
    }

    @Test
    fun keyOperationsRequireUniqueStringArrayWithoutImposingVerifyOperation() {
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256))
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256.withField("key_ops", JsonArray(listOf(JsonPrimitive("deriveKey"))))))
        for (invalid in listOf(
            JsonNull,
            JsonPrimitive("verify"),
            JsonArray(listOf(JsonPrimitive("verify"), JsonPrimitive("verify"))),
            JsonArray(listOf(JsonPrimitive("verify"), JsonNull)),
            JsonArray(listOf(JsonPrimitive(1))),
        )) {
            assertFalse(hasWellFormedPublicJwkMaterial(rfcP256.withField("key_ops", invalid)), invalid.toString())
        }
    }

    @Test
    fun encryptionUseAndUnknownExtensionsDoNotInvalidatePublicMaterial() {
        val withExtensions = JsonObject(rfcP256 + mapOf(
            "use" to JsonPrimitive("enc"),
            "alg" to JsonPrimitive("ECDH-ES"),
            "extension" to Json.parseToJsonElement("""{"nested":[true,null,{"label":"unchanged"}]}"""),
        ))
        assertTrue(hasWellFormedPublicJwkMaterial(withExtensions))
    }

    @Test
    fun genericMaterialCheckDoesNotRequireRsaSignatureStrengthOrUniqueKidAcrossKeys() {
        val smallRsa = JsonObject(mapOf(
            "kty" to JsonPrimitive("RSA"),
            "n" to JsonPrimitive("AQID"),
            "e" to JsonPrimitive("AQAB"),
            "kid" to JsonPrimitive("shared"),
        ))
        assertTrue(hasWellFormedPublicJwkMaterial(smallRsa))
        assertTrue(hasWellFormedPublicJwkMaterial(rfcP256.withField("kid", JsonPrimitive("shared"))))
    }

    private fun ec(curve: String, size: Int): JsonObject = JsonObject(mapOf(
        "kty" to JsonPrimitive("EC"),
        "crv" to JsonPrimitive(curve),
        "x" to b64(size),
        "y" to b64(size),
    ))

    private fun okp(curve: String, size: Int): JsonObject = JsonObject(mapOf(
        "kty" to JsonPrimitive("OKP"),
        "crv" to JsonPrimitive(curve),
        "x" to b64(size),
    ))

    private fun b64(size: Int): JsonPrimitive = JsonPrimitive(ByteArray(size) { 1 }.encodeToBase64Url())

    private fun JsonObject.withField(name: String, value: JsonElement): JsonObject = JsonObject(this + (name to value))
}

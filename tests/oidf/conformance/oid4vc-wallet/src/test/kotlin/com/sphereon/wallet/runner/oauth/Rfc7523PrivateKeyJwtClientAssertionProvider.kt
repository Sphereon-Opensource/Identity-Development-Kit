/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.wallet.runner.oauth

import com.sphereon.wallet.runner.di.RunnerTokenClientAssertion
import com.sphereon.wallet.runner.di.RunnerTokenClientAssertionProvider
import com.sphereon.wallet.runner.di.RunnerTokenClientAssertionRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.time.Clock
import java.util.Base64
import java.util.UUID

private const val JWT_BEARER_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
private const val ES256_JOSE_SIGNATURE_LENGTH = 64

internal class Rfc7523PrivateKeyJwtClientAssertionProvider(
    private val kid: String?,
    private val privateKey: ECPrivateKey,
    private val clock: Clock = Clock.systemUTC(),
) : RunnerTokenClientAssertionProvider {
    override suspend fun assertion(request: RunnerTokenClientAssertionRequest): RunnerTokenClientAssertion =
        RunnerTokenClientAssertion(
            assertionType = JWT_BEARER_ASSERTION_TYPE,
            assertion = signAssertion(request),
        )

    internal fun signAssertion(request: RunnerTokenClientAssertionRequest): String {
        val now = clock.instant().epochSecond
        val header =
            buildJsonObject {
                put("alg", "ES256")
                kid?.let { put("kid", it) }
                put("typ", "JWT")
            }
        val claims =
            buildJsonObject {
                put("iss", request.clientId)
                put("sub", request.clientId)
                put("aud", request.audience)
                put("iat", now)
                put("exp", now + 300)
                put("jti", UUID.randomUUID().toString())
            }
        val signingInput = "${base64Url(header.toString().encodeToByteArray())}.${base64Url(claims.toString().encodeToByteArray())}"
        val derSignature =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(signingInput.encodeToByteArray())
                sign()
            }
        return "$signingInput.${base64Url(derEcdsaToJose(derSignature, ES256_JOSE_SIGNATURE_LENGTH))}"
    }

    companion object {
        fun fromP256Jwk(
            jwk: JsonObject,
            clock: Clock = Clock.systemUTC(),
        ): Rfc7523PrivateKeyJwtClientAssertionProvider {
            require(jwk.getValue("kty").jsonPrimitive.content == "EC") { "private_key_jwt test key must be an EC JWK" }
            require(jwk.getValue("crv").jsonPrimitive.content == "P-256") { "private_key_jwt test key must be P-256 for ES256" }
            val d = BigInteger(1, base64UrlDecode(jwk.getValue("d").jsonPrimitive.content))
            return Rfc7523PrivateKeyJwtClientAssertionProvider(
                kid = jwk["kid"]?.jsonPrimitive?.content,
                privateKey = ecPrivateKey(d, "secp256r1"),
                clock = clock,
            )
        }
    }
}

private fun ecPrivateKey(
    privateScalar: BigInteger,
    curveName: String,
): ECPrivateKey {
    val parameters =
        AlgorithmParameters
            .getInstance("EC")
            .apply { init(ECGenParameterSpec(curveName)) }
    val parameterSpec = parameters.getParameterSpec(ECParameterSpec::class.java)
    val keySpec = ECPrivateKeySpec(privateScalar, parameterSpec)
    return KeyFactory.getInstance("EC").generatePrivate(keySpec) as ECPrivateKey
}

private fun derEcdsaToJose(
    der: ByteArray,
    outputLength: Int,
): ByteArray {
    var offset = 0
    require(der[offset++].toInt() == 0x30) { "ECDSA signature must be a DER sequence" }
    readDerLength(der, offset).also { offset = it.nextOffset }
    require(der[offset++].toInt() == 0x02) { "ECDSA signature r value must be an integer" }
    val rLength = readDerLength(der, offset).also { offset = it.nextOffset }.length
    val r = der.copyOfRange(offset, offset + rLength)
    offset += rLength
    require(der[offset++].toInt() == 0x02) { "ECDSA signature s value must be an integer" }
    val sLength = readDerLength(der, offset).also { offset = it.nextOffset }.length
    val s = der.copyOfRange(offset, offset + sLength)
    val partLength = outputLength / 2
    return unsignedLeftPadded(r, partLength) + unsignedLeftPadded(s, partLength)
}

private data class DerLength(
    val length: Int,
    val nextOffset: Int,
)

private fun readDerLength(
    der: ByteArray,
    offset: Int,
): DerLength {
    val first = der[offset].toInt() and 0xff
    if (first < 0x80) {
        return DerLength(first, offset + 1)
    }
    val byteCount = first and 0x7f
    require(byteCount in 1..4) { "Unsupported DER length encoding" }
    var length = 0
    repeat(byteCount) { index ->
        length = (length shl 8) or (der[offset + 1 + index].toInt() and 0xff)
    }
    return DerLength(length, offset + 1 + byteCount)
}

private fun unsignedLeftPadded(
    source: ByteArray,
    length: Int,
): ByteArray {
    val unsigned = source.dropWhile { it == 0.toByte() }.toByteArray()
    require(unsigned.size <= length) { "ECDSA integer is too large for JOSE signature" }
    return ByteArray(length).also { target ->
        unsigned.copyInto(target, destinationOffset = length - unsigned.size)
    }
}

private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun base64UrlDecode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

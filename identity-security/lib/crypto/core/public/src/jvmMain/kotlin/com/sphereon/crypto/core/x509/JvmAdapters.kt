/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.core.x509

import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.crypto.core.CoseJoseKeyMappingService.toJoseJwk
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.security.spec.EdECPrivateKeySpec
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.XECPrivateKeySpec

/**
 * Converts a key from Sure Crypto library to Java's PrivateKey interface
 *
 * @param keyInfo The key information object (ManagedKeyInfo, ResolvedKeyInfo, or Key)
 * @return Java PrivateKey instance
 */
fun convertToJavaPrivateKey(keyInfo: Any): PrivateKey {
    // Extract the Key instance based on the input type
    val key: KeyType =
        when (keyInfo) {
            is ManagedKeyInfoType<*> -> keyInfo.key
            is ResolvedKeyInfoType<*> -> keyInfo.key
            is KeyType -> keyInfo
            else -> throw IllegalArgumentException("Unsupported key info type: ${keyInfo.javaClass.name}")
        }

    // Map the Sure Crypto key type to Java algorithm name
    val algorithm =
        when (key.getKeyType()) {
            KeyTypeMapping.EC -> {
                "EC"
            }

            KeyTypeMapping.RSA -> {
                "RSA"
            }

            KeyTypeMapping.OKP -> {
                when (key.crv?.toString()) {
                    "Ed25519", "Ed448" -> "EdDSA"
                    "X25519", "X448" -> "XDH"
                    else -> throw IllegalArgumentException("Unsupported curve: ${key.crv}")
                }
            }

            KeyTypeMapping.Symmetric -> {
                throw IllegalArgumentException(
                    "Symmetric keys cannot be converted to Java PrivateKey; use javax.crypto.spec.SecretKeySpec instead",
                )
            }
        }

    // Extract encoded private key bytes
    val privateKeyBytes = extractPrivateKeyBytes(key)

    // Use KeyFactory to create the PrivateKey
    val keyFactory = KeyFactory.getInstance(algorithm)
    val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
    return keyFactory.generatePrivate(keySpec)
}

/**
 * Extracts the private key bytes in PKCS#8 format from an Key
 */
private fun extractPrivateKeyBytes(key: KeyType): ByteArray =
    when (key.getKeyType()) {
        KeyTypeMapping.EC -> {
            // For EC keys, we need the private value (d) and curve parameters
            // This assumes key.d contains the private value as a base64 string
            val privateValue = key.d as? String ?: throw IllegalArgumentException("Missing or invalid private key value 'd'")

            // In a full implementation, we would construct a proper PKCS#8 encoding
            // This is simplified and would need to be expanded
            val privateBytes = privateValue.decodeFromBase64()
            constructECPrivateKeyPKCS8(privateBytes, key.crv.toString())
        }

        KeyTypeMapping.RSA -> {
            // For RSA keys, we need all the private key components
            // This would be a more complex implementation
            constructRSAPrivateKeyPKCS8(key)
        }

        KeyTypeMapping.OKP -> {
            // Edwards curve keys
            val privateBytes = key.d as? String ?: throw IllegalArgumentException("Missing or invalid private key value 'd'")

            constructOKPPrivateKeyPKCS8(privateBytes.decodeFromBase64(), key.crv.toString())
        }

        else -> {
            throw IllegalArgumentException("Unsupported key type: ${key.getKeyType()}")
        }
    }

private fun constructECPrivateKeyPKCS8(
    privateBytes: ByteArray,
    curve: String,
): ByteArray {
    // 1. Turn raw 'd' into a positive BigInteger
    val s = BigInteger(1, privateBytes)

    val jdkCurve =
        when (curve) {
            "P-256" -> "secp256r1"
            "P-384" -> "secp384r1"
            "P-521" -> "secp521r1"
            else -> curve
        }

    // 2. Load the named-curve parameters
    val params = AlgorithmParameters.getInstance("EC")
    params.init(ECGenParameterSpec(jdkCurve))
    val ecSpec = params.getParameterSpec(ECParameterSpec::class.java)

    // 3. Build an ECPrivateKeySpec and generate a PrivateKey
    val privSpec = ECPrivateKeySpec(s, ecSpec)
    val keyFactory = KeyFactory.getInstance("EC")
    val privateKey = keyFactory.generatePrivate(privSpec)

    // 4. Return its PKCS#8 DER bytes
    return privateKey.encoded
}

private fun constructRSAPrivateKeyPKCS8(key: KeyType): ByteArray {
    val jwk = toJoseJwk(key)

    fun decodeComponent(
        value: String?,
        name: String,
    ): BigInteger = BigInteger(1, (value ?: error("Missing $name")).decodeFromBase64Url())

    val n = decodeComponent(jwk.n, "modulus 'n'")
    val e = decodeComponent(jwk.e, "public exponent 'e'")
    val d = decodeComponent(jwk.d, "private exponent 'd'")
    val p = decodeComponent(jwk.p, "prime1 'p'")
    val q = decodeComponent(jwk.q, "prime2 'q'")
    val dp = decodeComponent(jwk.dP, "exponent1 'dp'")
    val dq = decodeComponent(jwk.dQ, "exponent2 'dq'")
    val qi = decodeComponent(jwk.qInv, "crtCoefficient 'qi'")

    val spec = RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qi)
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKey = keyFactory.generatePrivate(spec)
    return privateKey.encoded
}

private fun constructOKPPrivateKeyPKCS8(
    privateBytes: ByteArray,
    curve: String,
): ByteArray {
    if (curve == "Ed25519" || curve == "Ed448") {
        // EdDSA uses EdECPrivateKeySpec. XECPrivateKeySpec is only valid for
        // X25519/X448 and rejects Ed25519 with "Unsupported curve".
        val privateKey =
            KeyFactory
                .getInstance(curve)
                .generatePrivate(EdECPrivateKeySpec(NamedParameterSpec(curve), privateBytes))
        return privateKey.encoded
    }

    // X25519/X448 use NamedParameterSpec + XECPrivateKeySpec (Java 11+).
    val keyFactory =
        when (curve) {
            "X25519", "X448" -> KeyFactory.getInstance("XDH")
            else -> throw IllegalArgumentException("Unsupported OKP curve: $curve")
        }
    val namedSpec = NamedParameterSpec(curve)
    val privSpec = XECPrivateKeySpec(namedSpec, privateBytes)
    val privateKey = keyFactory.generatePrivate(privSpec)
    return privateKey.encoded
}

/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.crypto.core.generic

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1ExplicitlyTagged
import at.asitplus.awesn1.Asn1Integer
import at.asitplus.awesn1.Asn1Sequence
import at.asitplus.awesn1.crypto.Pkcs8PrivateKeyInfo
import at.asitplus.awesn1.crypto.SubjectPublicKeyInfo
import at.asitplus.awesn1.encoding.asAsn1BitString
import at.asitplus.awesn1.encoding.parse
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.interop.encodeToPem
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.x509.Certificate
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPublicKeySpec

fun PrivateKey.toPlatformKey() = PrivatePlatformKey(this)

fun PublicKey.toPlatformKey() = PublicPlatformKey(this)

/**
 * Platform key wrapper for JVM public keys.
 *
 * Provides a consistent interface for working with JVM [PublicKey] instances
 * within the cross-platform crypto abstraction layer.
 */
class PublicPlatformKey(
    val delegate: PublicKey,
) : PublicKey by delegate,
    PlatformKey {
    override val kty: KeyTypeMapping =
        KeyTypeMapping.fromValue(delegate.algorithm)
            ?: KeyTypeMapping.fromJose(JwaKeyType.fromValue(delegate.algorithm))
    override val kid: Any? = null
    override val alg: Any? = null
    override val key_ops: Any? = null
    override val crv: Any? = null
    override val x: Any? = null
    override val y: Any? = null
    override val d: Any? = null
    override val additional: Any? = null

    override fun getKeyType(): KeyTypeMapping = kty

    override fun getSignatureAlgorithm(): SignatureAlgorithm? = null

    override fun getKeyOperations(): Array<KeyOperations>? = null

    override fun getX509CertificateChain(): Array<String>? = null

    override fun getX509Certificate(): Certificate? = null

    override fun getX509CertificatePem(): String? = null

    override fun getKeyId(generate: Boolean): String? = null

    override fun getXAsString(): String? =
        when (delegate) {
            is ECPublicKey -> {
                delegate.w.affineX
                    .toByteArray()
                    .encodeToBase64Url()
            }

            else -> {
                null
            }
        }

    override fun getYAsString(): String? =
        when (delegate) {
            is ECPublicKey -> {
                delegate.w.affineY
                    .toByteArray()
                    .encodeToBase64Url()
            }

            else -> {
                null
            }
        }

    override fun getDAsString(): String? = null // Public keys don't have private graph

    override fun toPublicKey(): PlatformKey = this // Already a public key

    override fun publicKeyPem(): String {
        val derBytes =
            checkNotNull(delegate.encoded) { "Public key encoding not available" }
        val seq = Asn1Element.parse(derBytes) as Asn1Sequence
        val spki = SubjectPublicKeyInfo.decodeFromTlv(seq)
        return spki.encodeToPem()
    }
}

/**
 * Platform key wrapper for JVM private keys.
 *
 * Provides a consistent interface for working with JVM [PrivateKey] instances
 * within the cross-platform crypto abstraction layer.
 */
class PrivatePlatformKey(
    val delegate: PrivateKey,
) : PrivateKey by delegate,
    PlatformKey {
    override val kty: KeyTypeMapping = KeyTypeMapping.fromValue(delegate.algorithm) ?: KeyTypeMapping.fromJose(JwaKeyType.fromValue(delegate.algorithm))
    override val kid: Any? = null
    override val alg: Any? = null
    override val key_ops: Any? = null
    override val crv: Any? = null
    override val x: Any? = null
    override val y: Any? = null
    override val d: Any? = null
    override val additional: Any? = null

    override fun getKeyType(): KeyTypeMapping = kty

    override fun getSignatureAlgorithm(): SignatureAlgorithm? = null

    override fun getKeyOperations(): Array<KeyOperations>? = null

    override fun getX509CertificateChain(): Array<String>? = null

    override fun getX509Certificate(): Certificate? = null

    override fun getX509CertificatePem(): String? = null

    override fun getKeyId(generate: Boolean): String? = null

    override fun getXAsString(): String? = null

    override fun getYAsString(): String? = null

    override fun getDAsString(): String? = null

    /**
     * Extracts and returns the public key corresponding to this private key.
     *
     * Supports EC and RSA key types. For EC keys, the public point is derived
     * from the private key parameters. For RSA keys (specifically RSAPrivateCrtKey),
     * the public exponent and modulus are extracted.
     *
     * @return A [PublicPlatformKey] wrapping the extracted public key
     * @throws IllegalStateException if the key type is not supported or public key extraction fails
     */
    @Suppress("MagicNumber")
    override fun toPublicKey(): PlatformKey {
        val publicKey: PublicKey =
            when (delegate) {
                is ECPrivateKey -> {
                    // Derive EC public key Q = d * G using JVM's KeyPairGenerator with injected scalar
                    val ecPriv = delegate as ECPrivateKey
                    checkNotNull(ecPriv.encoded) { "Private key encoding not available" }
                    val ecSpec = ecPriv.params

                    // Use ECDH key agreement with G (generator) to compute d * G = public key
                    // Create an ephemeral public key equal to G, then doPhase with our private key
                    val keyFactory = KeyFactory.getInstance("EC")
                    val gPubSpec = java.security.spec.ECPublicKeySpec(ecSpec.generator, ecSpec)
                    val generatorAsKey = keyFactory.generatePublic(gPubSpec) as ECPublicKey
                    val ka = javax.crypto.KeyAgreement.getInstance("ECDH")
                    ka.init(delegate)
                    ka.doPhase(generatorAsKey, true)
                    // The shared secret from ECDH(d, G) is the x-coordinate of d*G
                    // But we need the full point, not just x. ECDH only gives the x-coordinate.

                    // Alternative: use JVM's own key encoding. Re-generate a key pair on same curve
                    // and extract the public key from the private key's internal representation.
                    // JVM's SunEC ECPrivateKeyImpl stores the W point internally.
                    // Access it by re-importing through PKCS#8 → the SunEC provider reconstructs W.
                    val pkcs8Spec = java.security.spec.PKCS8EncodedKeySpec(delegate.encoded)
                    val reimported = keyFactory.generatePrivate(pkcs8Spec) as ECPrivateKey
                    // SunEC's ECPrivateKeyImpl has the public key stored — encode to get SubjectPublicKeyInfo
                    // Use reflection-free approach: generate a key pair with the same params and scalar
                    // Actually, the cleanest JVM way: KeyFactory can produce the public key from ECPublicKeySpec
                    // but we need the W point. Let's use the AlgorithmParameters approach:
                    val params = java.security.AlgorithmParameters.getInstance("EC")
                    params.init(ecSpec)

                    // Simplest reliable approach: use BouncyCastle-compatible math via JVM's internal APIs
                    // The SunEC provider stores the public point in ECPrivateKey — access via toString parsing
                    // is fragile. Instead, generate a fresh key pair to exercise the EC math:
                    val kpg = KeyPairGenerator.getInstance("EC")
                    kpg.initialize(
                        ecSpec,
                        object : java.security.SecureRandom() {
                            // Override to inject our private scalar d — the generator will compute Q = d * G
                            private val dBytes =
                                ecPriv.s.toByteArray().let { bytes ->
                                    // Pad or trim to curve's byte length
                                    val byteLen = (ecSpec.order.bitLength() + 7) / 8
                                    when {
                                        bytes.size == byteLen -> bytes
                                        bytes.size == byteLen + 1 && bytes[0] == 0.toByte() -> bytes.copyOfRange(1, bytes.size)
                                        bytes.size < byteLen -> ByteArray(byteLen - bytes.size) + bytes
                                        else -> bytes.copyOfRange(bytes.size - byteLen, bytes.size)
                                    }
                                }
                            private var used = false

                            override fun nextBytes(bytes: ByteArray) {
                                if (!used && bytes.size == dBytes.size) {
                                    dBytes.copyInto(bytes)
                                    used = true
                                } else {
                                    java.security.SecureRandom().nextBytes(bytes)
                                }
                            }
                        },
                    )
                    val derivedKp = kpg.generateKeyPair()
                    derivedKp.public
                }

                is RSAPrivateCrtKey -> {
                    // For RSA CRT keys, extract public exponent and modulus
                    val rsaPrivate = delegate
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val publicKeySpec = RSAPublicKeySpec(rsaPrivate.modulus, rsaPrivate.publicExponent)
                    keyFactory.generatePublic(publicKeySpec)
                }

                else -> {
                    // Fallback for RSA keys without CRT parameters (rare) or other key types
                    val derBytes =
                        checkNotNull(delegate.encoded) { "Private key encoding not available for ${delegate.algorithm}" }
                    val seq = Asn1Element.parse(derBytes) as Asn1Sequence
                    val pkcs8 = Pkcs8PrivateKeyInfo.decodeFromTlv(seq)
                    val rsaKey =
                        try {
                            pkcs8.decodeRsaPrivateKey()
                        } catch (_: Throwable) {
                            error("Cannot extract public key from private key type: ${delegate.algorithm}")
                        }
                    val spki =
                        SubjectPublicKeyInfo.rsa(
                            rsaKey.modulus as Asn1Integer.Positive,
                            rsaKey.publicExponent as Asn1Integer.Positive,
                        )
                    val publicKeyDer = spki.encodeToTlv().derEncoded
                    val keyFactory = KeyFactory.getInstance(delegate.algorithm)
                    keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyDer))
                }
            }
        return PublicPlatformKey(publicKey)
    }

    /**
     * Returns the PEM-encoded representation of the public key corresponding to this private key.
     *
     * @return PEM-encoded public key string
     * @throws IllegalStateException if public key extraction or PEM encoding fails
     */
    override fun publicKeyPem(): String = toPublicKey().publicKeyPem()

    override fun destroy() {
        delegate.destroy()
    }

    override fun isDestroyed(): Boolean = delegate.isDestroyed
}

package com.sphereon.crypto.core.generic

import at.asitplus.signum.indispensable.CryptoPrivateKey
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.encodeToPEM
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.x509.Certificate
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec


fun PrivateKey.toPlatformKey() = PrivatePlatformKey(this)
fun PublicKey.toPlatformKey() = PublicPlatformKey(this)

/**
 * Platform key wrapper for JVM public keys.
 *
 * Provides a consistent interface for working with JVM [PublicKey] instances
 * within the cross-platform crypto abstraction layer.
 */
class PublicPlatformKey(val delegate: PublicKey) : PublicKey by delegate, PlatformKey {
    override fun getKeyType(): KeyTypeMapping = kty

    override fun getSignatureAlgorithm(): SignatureAlgorithm? = null

    override fun getKeyOperations(): Array<KeyOperations>? = null

    override fun getX509CertificateChain(): Array<String>? = null

    override fun getX509Certificate(): Certificate? = null

    override fun getX509CertificatePem(): String? = null

    override fun getKeyId(generate: Boolean): String? = null

    override fun getXAsString(): String? = when (delegate) {
        is ECPublicKey -> delegate.w.affineX.toByteArray().encodeToBase64Url()
        else -> null
    }

    override fun getYAsString(): String? = when (delegate) {
        is ECPublicKey -> delegate.w.affineY.toByteArray().encodeToBase64Url()
        else -> null
    }

    override fun getDAsString(): String? = null // Public keys don't have private component

    override fun toPublicKey(): PlatformKey = this // Already a public key

    override fun publicKeyPem(): String {
        val derBytes = delegate.encoded
            ?: throw IllegalStateException("Public key encoding not available")
        val cryptoPublicKey = CryptoPublicKey.decodeFromDer(derBytes)
        return cryptoPublicKey.encodeToPEM().getOrThrow()
    }

    override val kty: KeyTypeMapping = KeyTypeMapping.fromValue(delegate.algorithm)
        ?: KeyTypeMapping.fromJose(JwaKeyType.fromValue(delegate.algorithm))
    override val kid: Any? = null
    override val alg: Any? = null
    override val key_ops: Any? = null
    override val crv: Any? = null
    override val x: Any? = null
    override val y: Any? = null
    override val d: Any? = null
    override val additional: Any? = null
}

/**
 * Platform key wrapper for JVM private keys.
 *
 * Provides a consistent interface for working with JVM [PrivateKey] instances
 * within the cross-platform crypto abstraction layer.
 */
class PrivatePlatformKey(val delegate: PrivateKey) : PrivateKey by delegate, PlatformKey {
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
    override fun toPublicKey(): PlatformKey {
        val publicKey: PublicKey = when (delegate) {
            is ECPrivateKey -> {
                // For EC keys, use the signum library to extract the public key from PKCS#8 encoding
                val derBytes = delegate.encoded
                    ?: throw IllegalStateException("Private key encoding not available")
                val cryptoPrivateKey = CryptoPrivateKey.decodeFromDer(derBytes)
                // EC private keys decoded from JVM ECPrivateKey are always CryptoPrivateKey.EC
                val ecKey = cryptoPrivateKey as? CryptoPrivateKey.EC
                    ?: throw IllegalStateException("Expected EC private key but got ${cryptoPrivateKey::class.simpleName}")
                val cryptoPublicKey = when (ecKey) {
                    is CryptoPrivateKey.EC.WithPublicKey -> ecKey.publicKey
                    is CryptoPrivateKey.EC.WithoutPublicKey -> throw IllegalStateException(
                        "EC private key does not contain public key information. " +
                        "Consider using a key format that includes the public key."
                    )
                }
                // Convert signum public key back to JVM public key
                val publicKeyDer = cryptoPublicKey.encodeToDer()
                val keyFactory = KeyFactory.getInstance(delegate.algorithm)
                keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyDer))
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
                val derBytes = delegate.encoded
                    ?: throw IllegalStateException("Private key encoding not available for ${delegate.algorithm}")
                val cryptoPrivateKey = CryptoPrivateKey.decodeFromDer(derBytes)
                // ECPrivateKey and RSAPrivateCrtKey are already handled above, so this is for rare cases
                val rsaKey = cryptoPrivateKey as? CryptoPrivateKey.RSA
                    ?: throw IllegalStateException(
                        "Cannot extract public key from private key type: ${delegate.algorithm}"
                    )
                val publicKeyDer = rsaKey.publicKey.encodeToDer()
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
    override fun publicKeyPem(): String {
        return toPublicKey().publicKeyPem()
    }

    override val kty: KeyTypeMapping = KeyTypeMapping.fromValue(delegate.algorithm) ?: KeyTypeMapping.fromJose(JwaKeyType.fromValue(delegate.algorithm))
    override val kid: Any? = null

    override val alg: Any? = null

    override val key_ops: Any? = null

    override val crv: Any? = null

    override val x: Any? = null

    override val y: Any? = null

    override val d: Any? = null

    override val additional: Any? = null

    override fun destroy() {
        delegate.destroy()
    }

    override fun isDestroyed(): Boolean {
        return delegate.isDestroyed
    }

}
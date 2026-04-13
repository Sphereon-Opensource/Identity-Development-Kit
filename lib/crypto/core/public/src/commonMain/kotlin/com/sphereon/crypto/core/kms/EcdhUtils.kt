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

package com.sphereon.crypto.core.kms

import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.interop.convertDerECKeyBytesToJwk
import com.sphereon.crypto.core.interop.resolveEcdsaKmpCurve
import com.sphereon.crypto.core.interop.toDerEcdsaPrivateKeyBytes
import com.sphereon.crypto.core.interop.toDerEcdsaPublicKeyBytes
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.jose.JwkUse
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH

/**
 * Utility object for ECDH key agreement operations.
 *
 * Provides functions for:
 * - Generating ephemeral EC key pairs for ECDH
 * - Performing ECDH key agreement
 * - Converting between DER and JWK key formats
 *
 * These utilities are used by the JWE layer for ECDH-ES encryption algorithms.
 */
object EcdhUtils {
    /**
     * Result of ephemeral key pair generation.
     *
     * @property publicKeyJwk The ephemeral public key as JWK (for epk header)
     * @property privateKeyDer The ephemeral private key in DER format (for key agreement)
     * @property curve The elliptic curve used
     */
    data class EphemeralKeyPair(
        val publicKeyJwk: Jwk,
        val privateKeyDer: ByteArray,
        val curve: Curve,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }
            other as EphemeralKeyPair
            if (publicKeyJwk != other.publicKeyJwk) {
                return false
            }
            if (!privateKeyDer.contentEquals(other.privateKeyDer)) {
                return false
            }
            if (curve != other.curve) {
                return false
            }
            return true
        }

        override fun hashCode(): Int {
            var result = publicKeyJwk.hashCode()
            result = 31 * result + privateKeyDer.contentHashCode()
            result = 31 * result + curve.hashCode()
            return result
        }
    }

    /**
     * Generates an ephemeral EC key pair for ECDH key agreement.
     *
     * The key pair is generated using the same curve as the recipient's key,
     * as required by RFC 7518 Section 4.6.1.
     *
     * @param curve The elliptic curve to use (must match recipient's key curve)
     * @return EphemeralKeyPair containing the public key as JWK and private key in DER format
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun generateEphemeralKeyPair(curve: Curve): EphemeralKeyPair {
        val kmpCurve = resolveEcdsaKmpCurve(curve)
        val provider = CryptographyProvider.Default
        val ecdh = provider.get(ECDH)

        // Generate key pair
        val keyPair = ecdh.keyPairGenerator(kmpCurve).generateKey()

        // Encode to DER format
        val publicKeyDer = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
        val privateKeyDer = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)

        // Convert public key to JWK for epk header
        // Note: We only include the public key in the JWK (no 'd' parameter)
        val publicKeyJwk =
            convertDerECKeyBytesToJwk(
                publicKeyBytes = publicKeyDer,
                privateKeyBytes = null,
                use = JwkUse.enc,
                keyOperations = arrayOf(KeyOperations.DERIVE_KEY),
                curve = curve,
            )

        // Remove unnecessary fields from epk (per RFC 7518 Section 4.6.1.1)
        // epk should only contain kty, crv, x, y (and optionally kid)
        val epkJwk =
            publicKeyJwk.copy(
                use = null,
                alg = null,
                key_ops = null,
            )

        return EphemeralKeyPair(
            publicKeyJwk = epkJwk,
            privateKeyDer = privateKeyDer,
            curve = curve,
        )
    }

    /**
     * Performs ECDH key agreement to derive a shared secret.
     *
     * @param ephemeralPrivateKeyDer The ephemeral private key in DER format
     * @param recipientPublicKeyJwk The recipient's public key as JWK
     * @param curve The elliptic curve (must match both keys)
     * @return The derived shared secret as a byte array
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun performKeyAgreement(
        ephemeralPrivateKeyDer: ByteArray,
        recipientPublicKeyJwk: JwkType,
        curve: Curve,
    ): ByteArray {
        require(recipientPublicKeyJwk.kty == JwaKeyType.EC) {
            "Recipient key must be an EC key, got: ${recipientPublicKeyJwk.kty}"
        }
        require(recipientPublicKeyJwk.x != null && recipientPublicKeyJwk.y != null) {
            "Recipient key must have x and y coordinates"
        }

        val kmpCurve = resolveEcdsaKmpCurve(curve)
        val provider = CryptographyProvider.Default
        val ecdh = provider.get(ECDH)

        // Decode the ephemeral private key
        val ecdhPrivateKey =
            ecdh
                .privateKeyDecoder(kmpCurve)
                .decodeFromByteArray(EC.PrivateKey.Format.DER, ephemeralPrivateKeyDer)

        // Convert recipient's public key to DER and decode
        val recipientPublicKeyDer = toDerEcdsaPublicKeyBytes(recipientPublicKeyJwk)
        val ecdhPublicKey =
            ecdh
                .publicKeyDecoder(kmpCurve)
                .decodeFromByteArray(EC.PublicKey.Format.DER, recipientPublicKeyDer)

        // Perform key agreement
        val sharedSecretGenerator = ecdhPrivateKey.sharedSecretGenerator()
        return sharedSecretGenerator.generateSharedSecretToByteArray(ecdhPublicKey)
    }

    /**
     * Performs ECDH key agreement for decryption (using our private key and sender's ephemeral public key).
     *
     * @param ourPrivateKeyJwk Our private key as JWK
     * @param senderEphemeralPublicKeyJwk The sender's ephemeral public key (from epk header) as JWK
     * @param curve The elliptic curve (must match both keys)
     * @return The derived shared secret as a byte array
     */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun performKeyAgreementForDecryption(
        ourPrivateKeyJwk: JwkType,
        senderEphemeralPublicKeyJwk: JwkType,
        curve: Curve,
    ): ByteArray {
        require(ourPrivateKeyJwk.kty == JwaKeyType.EC) {
            "Our key must be an EC key, got: ${ourPrivateKeyJwk.kty}"
        }
        require(ourPrivateKeyJwk.d != null) {
            "Our key must be a private key (must have 'd' parameter)"
        }
        require(senderEphemeralPublicKeyJwk.kty == JwaKeyType.EC) {
            "Sender's ephemeral key must be an EC key, got: ${senderEphemeralPublicKeyJwk.kty}"
        }
        require(senderEphemeralPublicKeyJwk.x != null && senderEphemeralPublicKeyJwk.y != null) {
            "Sender's ephemeral key must have x and y coordinates"
        }

        val kmpCurve = resolveEcdsaKmpCurve(curve)
        val provider = CryptographyProvider.Default
        val ecdh = provider.get(ECDH)

        // Convert our private key to DER and decode
        val ourPrivateKeyDer = toDerEcdsaPrivateKeyBytes(ourPrivateKeyJwk)
        val ecdhPrivateKey =
            ecdh
                .privateKeyDecoder(kmpCurve)
                .decodeFromByteArray(EC.PrivateKey.Format.DER, ourPrivateKeyDer)

        // Convert sender's ephemeral public key to DER and decode
        val senderPublicKeyDer = toDerEcdsaPublicKeyBytes(senderEphemeralPublicKeyJwk)
        val ecdhPublicKey =
            ecdh
                .publicKeyDecoder(kmpCurve)
                .decodeFromByteArray(EC.PublicKey.Format.DER, senderPublicKeyDer)

        // Perform key agreement
        val sharedSecretGenerator = ecdhPrivateKey.sharedSecretGenerator()
        return sharedSecretGenerator.generateSharedSecretToByteArray(ecdhPublicKey)
    }

    /**
     * Gets the curve from a JWK.
     *
     * @param jwk The JWK to extract the curve from
     * @return The curve, defaulting to P-256 if not specified
     */
    fun getCurveFromJwk(jwk: JwkType): Curve = jwk.crv?.let { Curve.fromJose(it) } ?: Curve.P_256

    /**
     * Checks if an algorithm is an ECDH-ES algorithm.
     *
     * @param algorithm The algorithm identifier string
     * @return true if the algorithm is ECDH-ES based
     */
    fun isEcdhEsAlgorithm(algorithm: String): Boolean = KeyAgreementAlgorithm.fromIdentifier(algorithm) != null
}

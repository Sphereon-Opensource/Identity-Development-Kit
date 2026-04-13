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

package com.sphereon.did.methods.key

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.AddKeyOptions
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidCreateResult
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidDeactivateResult
import com.sphereon.did.manager.DidProvider
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.DidUpdateResult
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Provider for creating did:key DIDs.
 *
 * did:key is an immutable DID method - once created, the DID cannot be
 * updated, deactivated, or have keys added/removed. The DID is derived
 * directly from the public key material.
 *
 * This provider creates did:key DIDs from existing JWK key material.
 * It does not manage the keys themselves - that's the responsibility
 * of the KMS layer.
 *
 * @see <a href="https://w3c-ccg.github.io/did-method-key/">did:key Method Specification</a>
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidProvider>())
class KeyDidProviderImpl(
    private val resolverRegistry: DidResolverRegistry,
) : DidProvider {
    override val method: String = KeyDidCapabilities.METHOD

    override val capabilities: DidMethodCapabilities = KeyDidCapabilities.CAPABILITIES

    override suspend fun create(options: DidCreateOptions): IdkResult<DidCreateResult, IdkError> {
        // Validate that we have key material to work with
        val jwk =
            options.publicKeyJwk
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "did:key creation requires publicKeyJwk in options",
                    ),
                )

        // Convert JWK to multicodec-prefixed bytes
        val multicodecBytes =
            jwkToMulticodecBytes(jwk).getOrElse {
                return Err(it)
            }

        // Encode as multibase (base58btc)
        val multibaseKey = MultibaseCodec.encodeBase58Btc(multicodecBytes)

        // Construct the DID
        val did = "did:key:$multibaseKey"

        // Build DID document directly from the JWK we already have,
        // avoiding the resolve() roundtrip that would re-decompress the key.
        val primaryVmId = "$did#$multibaseKey"
        val primaryVm =
            VerificationMethod(
                id = primaryVmId,
                type = VerificationMethodType.JSON_WEB_KEY_2020.value,
                controller = did,
                publicKeyJwk = jwk,
                publicKeyMultibase = multibaseKey,
            )

        val vmRef = VerificationMethodOrReference.fromReference(primaryVmId)
        val document =
            DidDocument(
                id = did,
                verificationMethod = listOf(primaryVm),
                authentication = listOf(vmRef),
                assertionMethod = listOf(vmRef),
                capabilityInvocation = listOf(vmRef),
                capabilityDelegation = listOf(vmRef),
            )

        val vmByPurpose = document.getVerificationMethodsByPurpose()

        return Ok(
            DidCreateResult(
                did = did,
                didDocument = document,
                verificationMethodsByPurpose = vmByPurpose,
            ),
        )
    }

    override suspend fun update(
        did: String,
        options: DidUpdateOptions,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:key is immutable - updates are not supported
        return Err(
            IdkError.fromString(
                message = "did:key does not support updates. DIDs are immutable and derived from the key material.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions,
    ): IdkResult<DidDeactivateResult, IdkError> {
        // did:key is immutable - deactivation is not supported
        return Err(
            IdkError.fromString(
                message = "did:key does not support deactivation. DIDs are immutable and always valid.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun addKey(
        did: String,
        options: AddKeyOptions,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:key supports only a single key per DID
        return Err(
            IdkError.fromString(
                message = "did:key does not support adding keys. Each DID corresponds to exactly one key.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun removeKey(
        did: String,
        keyId: String,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:key supports only a single key per DID
        return Err(
            IdkError.fromString(
                message = "did:key does not support removing keys. Each DID corresponds to exactly one key.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun addService(
        did: String,
        service: DidService,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:key does not support services
        return Err(
            IdkError.fromString(
                message = "did:key does not support services. Use did:web for DIDs with services.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    override suspend fun removeService(
        did: String,
        serviceId: String,
    ): IdkResult<DidUpdateResult, IdkError> {
        // did:key does not support services
        return Err(
            IdkError.fromString(
                message = "did:key does not support services.",
                code = "UNSUPPORTED_OPERATION",
            ),
        )
    }

    /**
     * Converts a JWK to multicodec-prefixed bytes.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun jwkToMulticodecBytes(jwk: Jwk): IdkResult<ByteArray, IdkError> {
        return when (jwk.kty) {
            JwaKeyType.OKP -> {
                val curve = jwk.crv?.value
                val xBase64 =
                    jwk.x
                        ?: return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "OKP JWK missing x coordinate",
                            ),
                        )

                // Decode x coordinate (add padding if needed)
                val xBytes =
                    try {
                        Base64.UrlSafe.decode(addBase64Padding(xBase64))
                    } catch (expected: Exception) {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Failed to decode x coordinate: ${expected.message}",
                            ),
                        )
                    }

                val prefix =
                    when (curve) {
                        "Ed25519" -> MulticodecPrefix.ED25519_PUB

                        "X25519" -> MulticodecPrefix.X25519_PUB

                        else -> return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Unsupported OKP curve: $curve",
                            ),
                        )
                    }

                Ok(prefix.toVarintBytes() + xBytes)
            }

            JwaKeyType.EC -> {
                val curve = jwk.crv?.value
                val xBase64 =
                    jwk.x
                        ?: return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "EC JWK missing x coordinate",
                            ),
                        )
                val yBase64 =
                    jwk.y
                        ?: return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "EC JWK missing y coordinate",
                            ),
                        )

                // Decode coordinates
                val xBytes =
                    try {
                        Base64.UrlSafe.decode(addBase64Padding(xBase64))
                    } catch (expected: Exception) {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Failed to decode x coordinate: ${expected.message}",
                            ),
                        )
                    }

                val yBytes =
                    try {
                        Base64.UrlSafe.decode(addBase64Padding(yBase64))
                    } catch (expected: Exception) {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Failed to decode y coordinate: ${expected.message}",
                            ),
                        )
                    }

                val prefix =
                    when (curve) {
                        "secp256k1" -> MulticodecPrefix.SECP256K1_PUB

                        "P-256" -> MulticodecPrefix.P256_PUB

                        "P-384" -> MulticodecPrefix.P384_PUB

                        else -> return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Unsupported EC curve: $curve",
                            ),
                        )
                    }

                // Compress the point (prefix 02 for even y, 03 for odd y)
                val compressedPoint = compressECPoint(xBytes, yBytes)

                Ok(prefix.toVarintBytes() + compressedPoint)
            }

            else -> {
                Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Unsupported JWK type for did:key: ${jwk.kty}",
                    ),
                )
            }
        }
    }

    /**
     * Compresses an EC point to compressed format (02/03 prefix + x).
     */
    private fun compressECPoint(
        x: ByteArray,
        y: ByteArray,
    ): ByteArray {
        // The compression prefix is 02 if y is even, 03 if y is odd
        // y is even if the last byte's LSB is 0
        val prefix =
            if (y.last().toInt() and 1 == 0) {
                0x02.toByte()
            } else {
                0x03.toByte()
            }
        return byteArrayOf(prefix) + x
    }

    /**
     * Adds padding to a base64url string if needed.
     */
    private fun addBase64Padding(base64: String): String =
        when (base64.length % BASE64_GROUP_SIZE) {
            BASE64_PAD_TWO -> "$base64=="
            BASE64_PAD_ONE -> "$base64="
            else -> base64
        }

    companion object {
        private const val BASE64_GROUP_SIZE = 4
        private const val BASE64_PAD_TWO = 2
        private const val BASE64_PAD_ONE = 3
        private const val ED25519_KEY_SIZE = 32
        private const val X25519_KEY_SIZE = 32
        private const val SECP256K1_COMPRESSED_KEY_SIZE = 33
        private const val P256_COMPRESSED_KEY_SIZE = 33
        private const val P384_COMPRESSED_KEY_SIZE = 49

        /**
         * Creates a did:key from an Ed25519 public key.
         */
        fun didFromEd25519PublicKey(publicKey: ByteArray): String {
            require(publicKey.size == ED25519_KEY_SIZE) { "Ed25519 public key must be $ED25519_KEY_SIZE bytes" }
            val multicodecBytes = MulticodecPrefix.ED25519_PUB.toVarintBytes() + publicKey
            val multibaseKey = MultibaseCodec.encodeBase58Btc(multicodecBytes)
            return "did:key:$multibaseKey"
        }

        /**
         * Creates a did:key from an X25519 public key.
         */
        fun didFromX25519PublicKey(publicKey: ByteArray): String {
            require(publicKey.size == X25519_KEY_SIZE) { "X25519 public key must be $X25519_KEY_SIZE bytes" }
            val multicodecBytes = MulticodecPrefix.X25519_PUB.toVarintBytes() + publicKey
            val multibaseKey = MultibaseCodec.encodeBase58Btc(multicodecBytes)
            return "did:key:$multibaseKey"
        }

        /**
         * Creates a did:key from a secp256k1 compressed public key.
         */
        fun didFromSecp256k1PublicKey(compressedPublicKey: ByteArray): String {
            require(compressedPublicKey.size == SECP256K1_COMPRESSED_KEY_SIZE) { "Secp256k1 compressed public key must be $SECP256K1_COMPRESSED_KEY_SIZE bytes" }
            val multicodecBytes = MulticodecPrefix.SECP256K1_PUB.toVarintBytes() + compressedPublicKey
            val multibaseKey = MultibaseCodec.encodeBase58Btc(multicodecBytes)
            return "did:key:$multibaseKey"
        }

        /**
         * Creates a did:key from a P-256 compressed public key.
         */
        fun didFromP256PublicKey(compressedPublicKey: ByteArray): String {
            require(compressedPublicKey.size == P256_COMPRESSED_KEY_SIZE) { "P-256 compressed public key must be $P256_COMPRESSED_KEY_SIZE bytes" }
            val multicodecBytes = MulticodecPrefix.P256_PUB.toVarintBytes() + compressedPublicKey
            val multibaseKey = MultibaseCodec.encodeBase58Btc(multicodecBytes)
            return "did:key:$multibaseKey"
        }

        /**
         * Creates a did:key from a P-384 compressed public key.
         */
        fun didFromP384PublicKey(compressedPublicKey: ByteArray): String {
            require(compressedPublicKey.size == P384_COMPRESSED_KEY_SIZE) { "P-384 compressed public key must be $P384_COMPRESSED_KEY_SIZE bytes" }
            val multicodecBytes = MulticodecPrefix.P384_PUB.toVarintBytes() + compressedPublicKey
            val multibaseKey = MultibaseCodec.encodeBase58Btc(multicodecBytes)
            return "did:key:$multibaseKey"
        }
    }
}

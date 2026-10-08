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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.resolver.DidDereferenceOptions
import com.sphereon.did.resolver.DidDereferenceResult
import com.sphereon.did.resolver.DidDereferencingMetadata
import com.sphereon.did.resolver.DidDocumentMetadata
import com.sphereon.did.resolver.DidResolutionMetadata
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolver
import com.sphereon.did.utils.ParsedDid
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Resolver for the did:key DID method.
 *
 * did:key is a static DID method where the DID is derived directly from
 * a cryptographic public key. The DID document is computed deterministically
 * from the key material without requiring any network or storage lookup.
 *
 * Format: did:key:<multibase-encoded-multicodec-public-key>
 *
 * Example: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
 *
 * @see <a href="https://w3c-ccg.github.io/did-method-key/">did:key Method Specification</a>
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidResolver>())
@ContributesBinding(SessionScope::class, binding = binding<KeyDidResolver>())
class KeyDidResolverImpl : KeyDidResolver {
    override val supportedMethods: List<String> = listOf(KeyDidCapabilities.METHOD)

    override val capabilities: DidMethodCapabilities = KeyDidCapabilities.CAPABILITIES

    override suspend fun resolve(
        did: String,
        options: DidResolutionOptions,
    ): IdkResult<DidResolutionResult, IdkError> {
        // Parse the DID
        val parsed =
            ParsedDid.tryParse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        if (parsed.method != KeyDidCapabilities.METHOD) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported DID method: ${parsed.method}. Expected: ${KeyDidCapabilities.METHOD}",
                ),
            )
        }

        // The method-specific identifier is the multibase-encoded key
        val multibaseKey = parsed.methodSpecificId

        // Decode multibase to get raw bytes
        val keyBytes =
            try {
                MultibaseCodec.decode(multibaseKey)
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to decode multibase key: ${expected.message}",
                    ),
                )
            }

        // Decode varint prefix to get key type
        val (codecCode, bytesRead) = MulticodecPrefix.decodeVarint(keyBytes)
        val codecPrefix =
            MulticodecPrefix.fromCode(codecCode)
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Unsupported multicodec prefix: 0x${codecCode.toString(HEX_RADIX)}",
                    ),
                )

        // Extract the raw public key bytes
        val rawKeyBytes = keyBytes.sliceArray(bytesRead until keyBytes.size)

        // Validate key length
        if (rawKeyBytes.size != codecPrefix.keyLength) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Invalid key length for ${codecPrefix.name}: expected ${codecPrefix.keyLength}, got ${rawKeyBytes.size}",
                ),
            )
        }

        // Build the DID document (suspend: EC decompression uses platform crypto)
        val didDocument = buildDidDocument(did, multibaseKey, codecPrefix, rawKeyBytes)

        // Build verification methods by purpose map
        val vmByPurpose = buildVerificationMethodsByPurpose(didDocument)

        return Ok(
            DidResolutionResult(
                didDocument = didDocument,
                didResolutionMetadata =
                    DidResolutionMetadata(
                        contentType = "application/did+ld+json",
                    ),
                didDocumentMetadata = DidDocumentMetadata(),
                verificationMethodsByPurpose = vmByPurpose,
            ),
        )
    }

    override suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions,
    ): IdkResult<DidDereferenceResult, IdkError> {
        // Parse DID URL (may include fragment like #key-1)
        val parsed =
            ParsedDid.tryParse(didUrl)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID URL: $didUrl"))

        // First resolve the full document
        val resolutionResult =
            resolve(parsed.did, DidResolutionOptions()).getOrElse {
                return Err(it)
            }

        val document =
            resolutionResult.didDocument
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID document not found for: ${parsed.did}"))

        // If there's a fragment, find the specific verification method
        val fragment = parsed.fragment
        if (fragment != null) {
            val vm = document.getVerificationMethodById(fragment)
            if (vm != null) {
                return Ok(
                    DidDereferenceResult(
                        contentType = "application/did+ld+json",
                        verificationMethod = vm,
                        dereferencingMetadata = DidDereferencingMetadata(),
                    ),
                )
            }

            // Check if it's a service reference
            val service = document.service?.find { it.id == fragment || it.id.endsWith("#$fragment") }
            if (service != null) {
                return Ok(
                    DidDereferenceResult(
                        contentType = "application/did+ld+json",
                        service = service,
                        dereferencingMetadata = DidDereferencingMetadata(),
                    ),
                )
            }

            return Err(
                IdkError.NOT_FOUND_ERROR(
                    message = "Fragment not found in DID document: #$fragment",
                ),
            )
        }

        // No fragment - return the whole document
        return Ok(
            DidDereferenceResult(
                contentType = "application/did+ld+json",
                didDocument = document,
                dereferencingMetadata = DidDereferencingMetadata(),
            ),
        )
    }

    /**
     * Builds a DID document from the decoded key material.
     */
    private suspend fun buildDidDocument(
        did: String,
        multibaseKey: String,
        codecPrefix: MulticodecPrefix,
        rawKeyBytes: ByteArray,
    ): DidDocument {
        // The primary verification method ID uses the multibase key as fragment
        val primaryVmId = "$did#$multibaseKey"

        // Build the JWK from raw key bytes
        val jwk = buildJwk(codecPrefix, rawKeyBytes)

        // Create primary verification method
        val primaryVm =
            VerificationMethod(
                id = primaryVmId,
                type = VerificationMethodType.JSON_WEB_KEY_2020.value,
                controller = did,
                publicKeyJwk = jwk,
                publicKeyMultibase = multibaseKey,
            )

        val verificationMethods = mutableListOf(primaryVm)
        val authenticationRefs = mutableListOf(VerificationMethodOrReference.fromReference(primaryVmId))
        val assertionMethodRefs = mutableListOf(VerificationMethodOrReference.fromReference(primaryVmId))
        val keyAgreementRefs = mutableListOf<VerificationMethodOrReference>()
        val capabilityInvocationRefs = mutableListOf(VerificationMethodOrReference.fromReference(primaryVmId))
        val capabilityDelegationRefs = mutableListOf(VerificationMethodOrReference.fromReference(primaryVmId))

        // For Ed25519, derive X25519 key for key agreement
        if (codecPrefix == MulticodecPrefix.ED25519_PUB) {
            val x25519KeyBytes = ed25519ToX25519(rawKeyBytes)
            if (x25519KeyBytes != null) {
                // Encode as multibase with X25519 prefix
                val x25519Multibase =
                    MultibaseCodec.encodeBase58Btc(
                        MulticodecPrefix.X25519_PUB.toVarintBytes() + x25519KeyBytes,
                    )
                val x25519VmId = "$did#$x25519Multibase"

                val x25519Jwk =
                    Jwk(
                        kty = JwaKeyType.OKP,
                        crv = JwaCurve.X25519,
                        x = x25519KeyBytes.encodeToBase64Url(),
                    )

                val x25519Vm =
                    VerificationMethod(
                        id = x25519VmId,
                        type = VerificationMethodType.JSON_WEB_KEY_2020.value,
                        controller = did,
                        publicKeyJwk = x25519Jwk,
                        publicKeyMultibase = x25519Multibase,
                    )

                verificationMethods.add(x25519Vm)
                keyAgreementRefs.add(VerificationMethodOrReference.fromReference(x25519VmId))
            }
        }

        // For X25519, it's only for key agreement
        if (codecPrefix == MulticodecPrefix.X25519_PUB) {
            keyAgreementRefs.add(VerificationMethodOrReference.fromReference(primaryVmId))
            // X25519 is not suitable for authentication/assertion, so clear those
            authenticationRefs.clear()
            assertionMethodRefs.clear()
            capabilityInvocationRefs.clear()
            capabilityDelegationRefs.clear()
        }

        return DidDocument(
            id = did,
            verificationMethod = verificationMethods,
            authentication = authenticationRefs.takeIf { it.isNotEmpty() },
            assertionMethod = assertionMethodRefs.takeIf { it.isNotEmpty() },
            keyAgreement = keyAgreementRefs.takeIf { it.isNotEmpty() },
            capabilityInvocation = capabilityInvocationRefs.takeIf { it.isNotEmpty() },
            capabilityDelegation = capabilityDelegationRefs.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Builds a JWK from raw key bytes.
     *
     * For EC keys, uses the platform crypto provider (via dev.whyoleg.cryptography) to
     * decompress compressed EC points, avoiding custom big-integer arithmetic.
     */
    private suspend fun buildJwk(
        codecPrefix: MulticodecPrefix,
        rawKeyBytes: ByteArray,
    ): Jwk =
        when (codecPrefix.keyType) {
            KeyTypeMapping.OKP -> {
                val crv =
                    when (codecPrefix.curve) {
                        Curve.Ed25519 -> JwaCurve.Ed25519
                        Curve.X25519 -> JwaCurve.X25519
                        else -> throw IllegalArgumentException("Unsupported OKP curve: ${codecPrefix.curve}")
                    }
                Jwk(
                    kty = JwaKeyType.OKP,
                    crv = crv,
                    x = rawKeyBytes.encodeToBase64Url(),
                )
            }

            KeyTypeMapping.EC -> {
                // Use the platform crypto provider to decode the compressed EC point.
                // The RAW format accepts both compressed (02/03 || x) and uncompressed (04 || x || y).
                val ecCurve =
                    when (codecPrefix.curve) {
                        Curve.P_256 -> EC.Curve.P256

                        Curve.P_384 -> EC.Curve.P384

                        Curve.Secp256k1 -> EC.Curve.P521

                        // Secp256k1 not in whyoleg; fall back to manual
                        else -> null
                    }

                if (ecCurve != null && codecPrefix.curve != Curve.Secp256k1) {
                    // Decode compressed point via platform crypto, re-encode as JWK
                    val jwkBytes =
                        CryptographyProvider.Default
                            .get(ECDSA)
                            .publicKeyDecoder(ecCurve)
                            .decodeFromByteArray(EC.PublicKey.Format.RAW, rawKeyBytes)
                            .encodeToByteArray(EC.PublicKey.Format.JWK)
                    val jwkJson =
                        kotlinx.serialization.json
                            .Json { ignoreUnknownKeys = true }
                            .decodeFromString<Jwk>(jwkBytes.decodeToString())
                    jwkJson
                } else {
                    // Fallback for Secp256k1 (not supported by most platform crypto providers)
                    val (x, y) = EcPointDecompression.decompress(codecPrefix.curve!!, rawKeyBytes)
                    val crv =
                        when (codecPrefix.curve) {
                            Curve.Secp256k1 -> JwaCurve.Secp256k1
                            else -> throw IllegalArgumentException("Unsupported EC curve: ${codecPrefix.curve}")
                        }
                    Jwk(
                        kty = JwaKeyType.EC,
                        crv = crv,
                        x = x.encodeToBase64Url(),
                        y = y.encodeToBase64Url(),
                    )
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported key type: ${codecPrefix.keyType}")
            }
        }

    /**
     * Derives an X25519 public key from an Ed25519 public key using the
     * birational map from twisted Edwards (Ed25519) to Montgomery (Curve25519) form.
     *
     * Ed25519 encodes 32 bytes as the y-coordinate in little-endian with the sign
     * bit of x stored in bit 255 (MSB of byte 31). The Montgomery u-coordinate is:
     *   u = (1 + y) / (1 - y) mod p
     * where p = 2^255 - 19.
     */
    private fun ed25519ToX25519(ed25519PublicKey: ByteArray): ByteArray? {
        if (ed25519PublicKey.size != ED25519_KEY_SIZE) {
            return null
        }

        // Clear the sign bit (bit 255) to get the pure y-coordinate
        val yBytes = ed25519PublicKey.copyOf()
        yBytes[SIGN_BIT_BYTE_INDEX] = (yBytes[SIGN_BIT_BYTE_INDEX].toInt() and SIGN_BIT_CLEAR_MASK).toByte()

        val y = Curve25519Field.decode(yBytes)
        val one = Curve25519Field.ONE

        // u = (1 + y) / (1 - y) mod p
        val numerator = Curve25519Field.add(one, y)
        val denominator = Curve25519Field.sub(one, y)
        val invDenominator = Curve25519Field.invert(denominator)
        val u = Curve25519Field.mul(numerator, invDenominator)

        return Curve25519Field.encode(u)
    }

    /**
     * Builds the verification methods by purpose map from a DID document.
     */
    private fun buildVerificationMethodsByPurpose(document: DidDocument): Map<VerificationPurpose, List<VerificationMethod>> = document.getVerificationMethodsByPurpose()

    @ContributesTo(SessionScope::class)
    interface Graph : KeyDidResolver.Graph {
        override val keyDidResolver: KeyDidResolver
    }

    companion object {
        private const val HEX_RADIX = 16
        private const val ED25519_KEY_SIZE = 32
        private const val SIGN_BIT_BYTE_INDEX = 31
        private const val SIGN_BIT_CLEAR_MASK = 0x7F
    }
}

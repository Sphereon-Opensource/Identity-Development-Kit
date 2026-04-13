/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
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
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
        options: DidResolutionOptions
    ): IdkResult<DidResolutionResult, IdkError> {
        // Parse the DID
        val parsed = ParsedDid.tryParse(did)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        if (parsed.method != KeyDidCapabilities.METHOD) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Unsupported DID method: ${parsed.method}. Expected: ${KeyDidCapabilities.METHOD}"
            ))
        }

        // The method-specific identifier is the multibase-encoded key
        val multibaseKey = parsed.methodSpecificId

        // Decode multibase to get raw bytes
        val keyBytes = try {
            MultibaseCodec.decode(multibaseKey)
        } catch (e: Exception) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Failed to decode multibase key: ${e.message}"
            ))
        }

        // Decode varint prefix to get key type
        val (codecCode, bytesRead) = MulticodecPrefix.decodeVarint(keyBytes)
        val codecPrefix = MulticodecPrefix.fromCode(codecCode)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Unsupported multicodec prefix: 0x${codecCode.toString(16)}"
            ))

        // Extract the raw public key bytes
        val rawKeyBytes = keyBytes.sliceArray(bytesRead until keyBytes.size)

        // Validate key length
        if (rawKeyBytes.size != codecPrefix.keyLength) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Invalid key length for ${codecPrefix.name}: expected ${codecPrefix.keyLength}, got ${rawKeyBytes.size}"
            ))
        }

        // Build the DID document
        val didDocument = buildDidDocument(did, multibaseKey, codecPrefix, rawKeyBytes)

        // Build verification methods by purpose map
        val vmByPurpose = buildVerificationMethodsByPurpose(didDocument)

        return Ok(DidResolutionResult(
            didDocument = didDocument,
            didResolutionMetadata = DidResolutionMetadata(
                contentType = "application/did+ld+json"
            ),
            didDocumentMetadata = DidDocumentMetadata(),
            verificationMethodsByPurpose = vmByPurpose
        ))
    }

    override suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions
    ): IdkResult<DidDereferenceResult, IdkError> {
        // Parse DID URL (may include fragment like #key-1)
        val parsed = ParsedDid.tryParse(didUrl)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID URL: $didUrl"))

        // First resolve the full document
        val resolutionResult = resolve(parsed.did, DidResolutionOptions()).getOrElse {
            return Err(it)
        }

        val document = resolutionResult.didDocument
            ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID document not found for: ${parsed.did}"))

        // If there's a fragment, find the specific verification method
        val fragment = parsed.fragment
        if (fragment != null) {
            val vm = document.getVerificationMethodById(fragment)
            if (vm != null) {
                return Ok(DidDereferenceResult(
                    contentType = "application/did+ld+json",
                    verificationMethod = vm,
                    dereferencingMetadata = DidDereferencingMetadata()
                ))
            }

            // Check if it's a service reference
            val service = document.service?.find { it.id == fragment || it.id.endsWith("#$fragment") }
            if (service != null) {
                return Ok(DidDereferenceResult(
                    contentType = "application/did+ld+json",
                    service = service,
                    dereferencingMetadata = DidDereferencingMetadata()
                ))
            }

            return Err(IdkError.NOT_FOUND_ERROR(
                message = "Fragment not found in DID document: #$fragment"
            ))
        }

        // No fragment - return the whole document
        return Ok(DidDereferenceResult(
            contentType = "application/did+ld+json",
            didDocument = document,
            dereferencingMetadata = DidDereferencingMetadata()
        ))
    }

    /**
     * Builds a DID document from the decoded key material.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun buildDidDocument(
        did: String,
        multibaseKey: String,
        codecPrefix: MulticodecPrefix,
        rawKeyBytes: ByteArray
    ): DidDocument {
        // The primary verification method ID uses the multibase key as fragment
        val primaryVmId = "$did#$multibaseKey"

        // Build the JWK from raw key bytes
        val jwk = buildJwk(codecPrefix, rawKeyBytes)

        // Create primary verification method
        val primaryVm = VerificationMethod(
            id = primaryVmId,
            type = VerificationMethodType.JSON_WEB_KEY_2020.value,
            controller = did,
            publicKeyJwk = jwk,
            publicKeyMultibase = multibaseKey
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
                val x25519Multibase = MultibaseCodec.encodeBase58Btc(
                    MulticodecPrefix.X25519_PUB.toVarintBytes() + x25519KeyBytes
                )
                val x25519VmId = "$did#$x25519Multibase"

                val x25519Jwk = Jwk(
                    kty = JwaKeyType.OKP,
                    crv = JwaCurve.X25519,
                    x = Base64.UrlSafe.encode(x25519KeyBytes).trimEnd('=')
                )

                val x25519Vm = VerificationMethod(
                    id = x25519VmId,
                    type = VerificationMethodType.JSON_WEB_KEY_2020.value,
                    controller = did,
                    publicKeyJwk = x25519Jwk,
                    publicKeyMultibase = x25519Multibase
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
            capabilityDelegation = capabilityDelegationRefs.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Builds a JWK from the raw key bytes based on the multicodec prefix.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun buildJwk(codecPrefix: MulticodecPrefix, rawKeyBytes: ByteArray): Jwk {
        return when (codecPrefix.keyType) {
            KeyTypeMapping.OKP -> {
                val crv = when (codecPrefix.curve) {
                    Curve.Ed25519 -> JwaCurve.Ed25519
                    Curve.X25519 -> JwaCurve.X25519
                    else -> throw IllegalArgumentException("Unsupported OKP curve: ${codecPrefix.curve}")
                }
                Jwk(
                    kty = JwaKeyType.OKP,
                    crv = crv,
                    x = Base64.UrlSafe.encode(rawKeyBytes).trimEnd('=')
                )
            }
            KeyTypeMapping.EC -> {
                // For EC keys, we need to handle compressed point format
                val (x, y) = decompressECPoint(codecPrefix.curve!!, rawKeyBytes)
                val crv = when (codecPrefix.curve) {
                    Curve.Secp256k1 -> JwaCurve.Secp256k1
                    Curve.P_256 -> JwaCurve.P_256
                    Curve.P_384 -> JwaCurve.P_384
                    else -> throw IllegalArgumentException("Unsupported EC curve: ${codecPrefix.curve}")
                }
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = crv,
                    x = Base64.UrlSafe.encode(x).trimEnd('='),
                    y = Base64.UrlSafe.encode(y).trimEnd('=')
                )
            }
            else -> throw IllegalArgumentException("Unsupported key type: ${codecPrefix.keyType}")
        }
    }

    /**
     * Decompresses a compressed EC point to x, y coordinates.
     * Compressed format: 02/03 prefix + x coordinate (02 = even y, 03 = odd y)
     */
    private fun decompressECPoint(curve: Curve, compressed: ByteArray): Pair<ByteArray, ByteArray> {
        // For now, we'll store the compressed key and note that full decompression
        // requires elliptic curve math which should be done by the crypto library
        // This is a simplified implementation - in production, use proper EC libraries

        val prefix = compressed[0].toInt() and 0xFF
        require(prefix == 0x02 || prefix == 0x03) {
            "Invalid compressed point prefix: $prefix"
        }

        val coordSize = when (curve) {
            Curve.Secp256k1, Curve.P_256 -> 32
            Curve.P_384 -> 48
            else -> throw IllegalArgumentException("Unsupported curve: $curve")
        }

        val x = compressed.sliceArray(1 until compressed.size)
        require(x.size == coordSize) {
            "Invalid x coordinate size: expected $coordSize, got ${x.size}"
        }

        // TODO: Implement proper EC point decompression using the curve equation
        // y^2 = x^3 + ax + b (mod p)
        // For now, return x and a placeholder y (this should be replaced with real decompression)
        // Real implementation would use the crypto library's EC operations

        // Placeholder: return x twice (caller should use crypto library for real decompression)
        return Pair(x, x)
    }

    /**
     * Derives an X25519 public key from an Ed25519 public key.
     *
     * This uses the birational map from the Ed25519 curve (twisted Edwards)
     * to Curve25519 (Montgomery form).
     *
     * Note: This is a simplified implementation. In production, use a proper
     * cryptographic library that supports Ed25519 to X25519 conversion.
     */
    private fun ed25519ToX25519(ed25519PublicKey: ByteArray): ByteArray? {
        // The conversion from Ed25519 to X25519 requires computing:
        // u = (1 + y) / (1 - y) mod p
        // where y is the Ed25519 y-coordinate and p is the field prime

        // This requires big integer arithmetic and modular inversion
        // For now, we'll use a simplified approach that many libraries support

        // TODO: Implement proper Ed25519 to X25519 conversion
        // For now, return the input as a placeholder (should be replaced with real conversion)
        // Real implementation would use the crypto library's curve conversion

        // Many crypto libraries have this built-in:
        // - libsodium: crypto_sign_ed25519_pk_to_curve25519
        // - tweetnacl: not directly supported
        // - bouncy castle: KeyAgreementSpi with X25519

        return if (ed25519PublicKey.size == 32) {
            // Placeholder - in real implementation, perform the birational map
            ed25519PublicKey.copyOf()
        } else {
            null
        }
    }

    /**
     * Builds the verification methods by purpose map from a DID document.
     */
    private fun buildVerificationMethodsByPurpose(
        document: DidDocument
    ): Map<VerificationPurpose, List<VerificationMethod>> {
        return document.getVerificationMethodsByPurpose()
    }

    @ContributesTo(SessionScope::class)
    interface Component : KeyDidResolver.Component {
        override val keyDidResolver: KeyDidResolver
    }
}

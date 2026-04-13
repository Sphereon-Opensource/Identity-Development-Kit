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

package com.sphereon.crypto.kms.keystore.software

import at.asitplus.signum.indispensable.ECCurve
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.CoseKeyPair
import com.sphereon.crypto.core.generic.JoseKeyPair
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.fromJwkName
import com.sphereon.crypto.core.interop.publicKeyECFrom
import com.sphereon.crypto.core.interop.toJwk
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkUse
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*
import platform.darwin.OSStatus

/**
 * Handles native key generation in iOS Keychain and conversion to JWK format.
 */
@OptIn(ExperimentalForeignApi::class)
object AppleKeychainKeyGeneration {

    /**
     * Generate a key pair natively in iOS keychain and return as JWK.
     */
    fun generateNativeKeyPair(
        alias: String,
        keyType: KeyTypeMapping,
        algorithm: SignatureAlgorithm,
        keyUse: JwkUse,
        keyOperations: Array<out KeyOperations>,
        overwriteAlias: Boolean = true
    ): ManagedKeyPair {
        val keySizeBits = when (keyType) {
            KeyTypeMapping.EC -> 256 // P-256
            KeyTypeMapping.RSA -> when (algorithm) {
                SignatureAlgorithm.RSA_SHA384, SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> 3072
                SignatureAlgorithm.RSA_SHA512, SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> 4096
                else -> 2048
            }
            else -> throw PKIException("Unsupported key type: $keyType")
        }

        // Generate the key pair using SecKeyGeneratePair (Signum approach)
        // This is the proper iOS API for key generation
        val privateTag = "com.sphereon.privatekey.$alias"
        val publicTag = "com.sphereon.publickey.$alias"

        // Check if key already exists
        val existingKey = getNativeKeychainKey(alias)
        if (existingKey != null) {
            if (!overwriteAlias) {
                throw PKIException("Cannot overwrite key alias $alias, as alias already exists in keychain and overwriting is not enabled")
            }
            // Delete existing keys to ensure we generate a fresh key
            deleteNativeKeysForAlias(alias, privateTag, publicTag)
        }

        val (privateKey, publicKey) = memScoped {
            val privateTagData = privateTag.encodeToByteArray().usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), privateTag.length.toLong())
            } ?: throw PKIException("Failed to create private tag data")

            val publicTagData = publicTag.encodeToByteArray().usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), publicTag.length.toLong())
            } ?: throw PKIException("Failed to create public tag data")

            // Create attributes dictionary for SecKeyGeneratePair
            val attributes = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            ) ?: throw PKIException("Failed to create attributes dictionary")

            // Set key type and size
            CFDictionarySetValue(attributes, kSecAttrKeyType, when (keyType) {
                KeyTypeMapping.EC -> kSecAttrKeyTypeECSECPrimeRandom
                KeyTypeMapping.RSA -> kSecAttrKeyTypeRSA
                else -> throw PKIException("Unsupported key type: $keyType")
            })

            val sizeNumber = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, cValuesOf(keySizeBits).ptr)
            CFDictionarySetValue(attributes, kSecAttrKeySizeInBits, sizeNumber)

            // Private key attributes
            val privateKeyAttrs = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            ) ?: throw PKIException("Failed to create private key attributes")

            // Do NOT set kSecAttrApplicationLabel - let iOS set it automatically to the SHA-1 of public key
            // This allows automatic certificate linking
            CFDictionarySetValue(privateKeyAttrs, kSecAttrIsPermanent, kCFBooleanTrue)
            CFDictionarySetValue(privateKeyAttrs, kSecAttrApplicationTag, privateTagData)
            CFDictionarySetValue(privateKeyAttrs, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            CFDictionarySetValue(attributes, kSecPrivateKeyAttrs, privateKeyAttrs)

            // Public key attributes
            val publicKeyAttrs = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            ) ?: throw PKIException("Failed to create public key attributes")

            // Do NOT set kSecAttrApplicationLabel - let iOS set it automatically to the SHA-1 of public key
            // IMPORTANT: Public key should NOT be stored permanently in keychain
            // Storing it creates ambiguity when iOS forms identities by kSecAttrApplicationLabel
            // (both public and private keys have same application label - SHA-1 of public key)
            // Public key can always be derived from private key via SecKeyCopyPublicKey
            CFDictionarySetValue(publicKeyAttrs, kSecAttrIsPermanent, kCFBooleanFalse)
            CFDictionarySetValue(publicKeyAttrs, kSecAttrApplicationTag, publicTagData)
            CFDictionarySetValue(attributes, kSecPublicKeyAttrs, publicKeyAttrs)

            // Generate the key pair
            val pubKeyRef = alloc<SecKeyRefVar>()
            val privKeyRef = alloc<SecKeyRefVar>()

            val status = SecKeyGeneratePair(attributes, pubKeyRef.ptr, privKeyRef.ptr)

            if (status != 0) {
                throw PKIException("Failed to generate key pair for alias $alias: status=$status")
            }

            if (pubKeyRef.value == null || privKeyRef.value == null) {
                throw PKIException("Key generation succeeded but returned null keys")
            }

            privKeyRef.value!! to pubKeyRef.value!!
        }

        // Convert to JWK
        val (privateJwk, publicJwk) = convertNativeKeyToJwk(
            privateKey = privateKey,
            publicKey = publicKey,
            keyType = keyType,
            algorithm = algorithm,
            keyUse = keyUse,
            keyOperations = keyOperations
        )

        // Mark as native key in index
        markAsNativeKeychainKey(alias, keyType)

        // Convert public key to COSE
        // Note: privateJwk and publicJwk are the same (public key only) for native keys
        val publicCoseKey = CoseJoseKeyMappingService.toCoseKey(publicJwk)

        return ManagedKeyPair(
            kid = publicJwk.kid,
            providerId = "software", // Will be overridden by provider
            alias = alias,
            cose = CoseKeyPair(
                privateCoseKey = null, // Native key not exposed
                publicCoseKey = publicCoseKey
            ),
            jose = JoseKeyPair(
                privateJwk = null, // Native key not exposed
                publicJwk = publicJwk
            ),
            privateKey = null // Native key stored in keychain, not as PlatformKey
        )
    }

    /**
     * Convert native SecKey to JWK format.
     */
    private fun convertNativeKeyToJwk(
        privateKey: SecKeyRef,
        publicKey: SecKeyRef,
        keyType: KeyTypeMapping,
        algorithm: SignatureAlgorithm,
        keyUse: JwkUse,
        keyOperations: Array<out KeyOperations>
    ): Pair<Jwk, Jwk> {
        // Get public key data
        val publicKeyData = SecKeyCopyExternalRepresentation(publicKey, null) as? CFDataRef
            ?: throw PKIException("Failed to get public key data")

        val pubKeyLength = CFDataGetLength(publicKeyData).toInt()
        val pubKeyBytes = CFDataGetBytePtr(publicKeyData)
        val pubKeyByteArray = ByteArray(pubKeyLength)
        if (pubKeyBytes != null) {
            platform.posix.memcpy(pubKeyByteArray.refTo(0), pubKeyBytes, pubKeyLength.convert())
        }

        // Convert based on key type
        return when (keyType) {
            KeyTypeMapping.EC -> convertECKeyToJwk(pubKeyByteArray, algorithm, keyUse, keyOperations)
            KeyTypeMapping.RSA -> convertRSAKeyToJwk(pubKeyByteArray, algorithm, keyUse, keyOperations)
            else -> throw PKIException("Unsupported key type: $keyType")
        }
    }

    private fun convertECKeyToJwk(
        publicKeyBytes: ByteArray,
        algorithm: SignatureAlgorithm,
        keyUse: JwkUse,
        keyOperations: Array<out KeyOperations>
    ): Pair<Jwk, Jwk> {
        // Determine expected coordinate size based on curve
        val curve = algorithm.curve ?: throw PKIException("EC curve not specified in algorithm")
        val curveName = curve.jose.value

        val coordinateSize = when (curveName) {
            "P-256" -> 32
            "P-384" -> 48
            "P-521" -> 66
            else -> throw PKIException("Unsupported EC curve: $curveName")
        }

        val expectedSize = 1 + (2 * coordinateSize) // 0x04 prefix + X + Y

        if (publicKeyBytes.size != expectedSize || publicKeyBytes[0] != 0x04.toByte()) {
            throw PKIException("Invalid EC public key format for curve $curveName. Expected size: $expectedSize, got: ${publicKeyBytes.size}, first byte: ${publicKeyBytes[0]}")
        }

        // Extract X and Y coordinates
        val x = publicKeyBytes.copyOfRange(1, 1 + coordinateSize)
        val y = publicKeyBytes.copyOfRange(1 + coordinateSize, 1 + (2 * coordinateSize))

        // Use Signum library to properly construct the public key and convert to JWK
        val signumCurve = ECCurve.fromJwkName(curveName)
        val cryptoPublicKey = publicKeyECFrom(signumCurve, x, y)
        val publicJwk = cryptoPublicKey.toJwk(
            x5c = null,
            alg = algorithm.jose,
            generateKid = false
        ).copy(
            use = keyUse.value,
            key_ops = keyOperations.map { it.jose }.toTypedArray()
        )

        // For native keychain keys, we don't expose the private key material
        // The private key stays in the keychain and is accessed via SecKeyRef
        return publicJwk to publicJwk
    }

    private fun convertRSAKeyToJwk(
        publicKeyBytes: ByteArray,
        algorithm: SignatureAlgorithm,
        keyUse: JwkUse,
        keyOperations: Array<out KeyOperations>
    ): Pair<Jwk, Jwk> {
        // RSA public key parsing is more complex, would need ASN.1 parsing
        // For now, throw an exception - can be implemented if needed
        throw PKIException("RSA native key generation not yet implemented")
    }

    /**
     * Delete any existing keys with the given alias from the keychain.
     * This ensures we don't have stale keys that would be returned on duplicate.
     */
    private fun deleteNativeKeysForAlias(alias: String, privateTag: String, publicTag: String) = memScoped {
        // Delete private key
        val privateTagData = privateTag.encodeToByteArray().usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), privateTag.length.toLong())
        }
        if (privateTagData != null) {
            val query = CFDictionaryCreateMutable(
                null,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )
            if (query != null) {
                CFDictionaryAddValue(query, kSecClass, kSecClassKey)
                CFDictionaryAddValue(query, kSecAttrApplicationTag, privateTagData)
                SecItemDelete(query)
            }
        }

        // Delete public key
        val publicTagData = publicTag.encodeToByteArray().usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), publicTag.length.toLong())
        }
        if (publicTagData != null) {
            val query = CFDictionaryCreateMutable(
                null,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )
            if (query != null) {
                CFDictionaryAddValue(query, kSecClass, kSecClassKey)
                CFDictionaryAddValue(query, kSecAttrApplicationTag, publicTagData)
                SecItemDelete(query)
            }
        }
    }
}

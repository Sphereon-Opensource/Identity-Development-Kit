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

package com.sphereon.crypto.kms

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.kms.keystore.software.getNativeKeychainKey
import com.sphereon.crypto.kms.keystore.software.isNativeKeychainKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCopyKeyExchangeResult
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDHKeyExchangeStandard

/**
 * iOS implementation of native ECDH key agreement using SecKeyCopyKeyExchangeResult.
 *
 * This allows ECDH to be performed with iOS Keychain keys that do not export
 * their private key material (d parameter).
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun performNativeKeychainEcdh(
    privateKeyAlias: String,
    remotePublicKeyJwk: JwkType,
): ByteArray? {
    // Check if this is a native keychain key
    if (!isNativeKeychainKey(privateKeyAlias)) {
        return null
    }

    // Get the private key reference from keychain
    val privateKeyRef = getNativeKeychainKey(privateKeyAlias) ?: return null

    // Convert JWK public key to raw format (04 || X || Y for uncompressed)
    val remotePublicKeyRaw = jwkToRawEcPublicKey(remotePublicKeyJwk) ?: return null

    // Create SecKey from remote public key
    val remotePublicKeyRef = createSecKeyFromRawEcPublicKey(remotePublicKeyRaw) ?: return null

    // Perform ECDH key agreement
    return memScoped {
        val error = alloc<CFErrorRefVar>()

        // Use the standard ECDH algorithm
        val sharedSecretData =
            SecKeyCopyKeyExchangeResult(
                privateKeyRef,
                kSecKeyAlgorithmECDHKeyExchangeStandard,
                remotePublicKeyRef,
                null, // No additional parameters needed for standard ECDH
                error.ptr,
            )

        if (sharedSecretData == null) {
            return@memScoped null
        }

        // Convert CFData to ByteArray
        val length = CFDataGetLength(sharedSecretData).toInt()
        val bytes = CFDataGetBytePtr(sharedSecretData)
        ByteArray(length) { bytes!![it].toByte() }
    }
}

/**
 * Convert a JWK EC public key to raw uncompressed format (04 || X || Y).
 */
private fun jwkToRawEcPublicKey(jwk: JwkType): ByteArray? {
    val x = jwk.x ?: return null
    val y = jwk.y ?: return null

    val xBytes = x.decodeFrom(Encoding.BASE64URL)
    val yBytes = y.decodeFrom(Encoding.BASE64URL)

    // Ensure X and Y are the same length (pad with leading zeros if needed)
    val coordLength = maxOf(xBytes.size, yBytes.size)
    val xPadded = if (xBytes.size < coordLength) ByteArray(coordLength - xBytes.size) + xBytes else xBytes
    val yPadded = if (yBytes.size < coordLength) ByteArray(coordLength - yBytes.size) + yBytes else yBytes

    // Uncompressed format: 0x04 || X || Y
    return byteArrayOf(0x04) + xPadded + yPadded
}

/**
 * Create a SecKeyRef from raw EC public key bytes.
 */
@OptIn(ExperimentalForeignApi::class)
private fun createSecKeyFromRawEcPublicKey(rawPublicKey: ByteArray): SecKeyRef? =
    memScoped {
        // Determine key size from raw key length
        // Uncompressed format: 1 byte (0x04) + X + Y
        val coordLength = (rawPublicKey.size - 1) / 2
        val keySizeBits = coordLength * 8

        // Create CFData from raw key bytes
        val keyData =
            rawPublicKey.usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), rawPublicKey.size.toLong())
            } ?: return null

        // Create attributes dictionary for key import
        val attributes =
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            ) ?: return null

        CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionarySetValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPublic)

        val sizeNumber = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, cValuesOf(keySizeBits).ptr)
        CFDictionarySetValue(attributes, kSecAttrKeySizeInBits, sizeNumber)

        // Create SecKey from raw data
        val error = alloc<CFErrorRefVar>()
        SecKeyCreateWithData(keyData, attributes, error.ptr)
    }

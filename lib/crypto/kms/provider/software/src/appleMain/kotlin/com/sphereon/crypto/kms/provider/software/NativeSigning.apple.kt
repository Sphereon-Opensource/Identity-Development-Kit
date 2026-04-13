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

package com.sphereon.crypto.kms.provider.software

import at.asitplus.signum.indispensable.CryptoSignature
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.kms.keystore.software.isNativeKeychainKey
import com.sphereon.crypto.kms.keystore.software.getNativeKeychainKey
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * iOS implementation that signs data using a private key stored in the keychain.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun signWithNativeKey(
    keyInfo: KeyInfoType<*>,
    input: ByteArray
): ByteArray? {
    val alias = keyInfo.alias ?: return null

    // Check if this is a native keychain key
    if (!isNativeKeychainKey(alias)) {
        return null
    }

    // Get the private key reference from keychain
    val privateKey = getNativeKeychainKey(alias) ?: return null

    // Determine the signature algorithm
    val signatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256

    // Map to SecKey algorithm
    // Note: We use "Message" variants which hash the input before signing
    // This matches the behavior of the JVM/Android implementation where the signature generator
    // takes the raw input and hashes it internally before signing
    val algorithm = when (signatureAlgorithm) {
        SignatureAlgorithm.ECDSA_SHA256 -> kSecKeyAlgorithmECDSASignatureMessageX962SHA256
        SignatureAlgorithm.ECDSA_SHA384 -> kSecKeyAlgorithmECDSASignatureMessageX962SHA384
        SignatureAlgorithm.ECDSA_SHA512 -> kSecKeyAlgorithmECDSASignatureMessageX962SHA512
        SignatureAlgorithm.RSA_SHA256 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
        SignatureAlgorithm.RSA_SHA384 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA384
        SignatureAlgorithm.RSA_SHA512 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA512
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 -> kSecKeyAlgorithmRSASignatureMessagePSSSHA256
        SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1 -> kSecKeyAlgorithmRSASignatureMessagePSSSHA384
        SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1 -> kSecKeyAlgorithmRSASignatureMessagePSSSHA512
        else -> return null // Unsupported algorithm for native signing
    }

    // Sign the data
    return memScoped {
        // Create CFDataRef from input bytes
        val inputData = CFDataCreate(null, input.asUByteArray().refTo(0), input.size.toLong())
            ?: throw IllegalStateException("Failed to create CFData from input")

        val error = alloc<CFErrorRefVar>()
        val signatureData = SecKeyCreateSignature(
            privateKey,
            algorithm,
            inputData,
            error.ptr
        )

        if (signatureData == null) {
            val errorDesc = CFErrorCopyDescription(error.value)?.let {
                CFStringGetCStringPtr(it, kCFStringEncodingUTF8)?.toKString()
            } ?: "Unknown error"
            throw IllegalStateException("Failed to sign data with native keychain key: $errorDesc")
        }

        // Convert CFData to ByteArray (DER format)
        val length = CFDataGetLength(signatureData).toInt()
        val bytes = CFDataGetBytePtr(signatureData)
        val derSignature = ByteArray(length) { bytes!![it].toByte() }

        // iOS returns ECDSA signatures in DER format, but we need raw format (r||s)
        // Use Signum to convert from DER to raw format
        val cryptoSig = CryptoSignature.decodeFromDer(derSignature)

        // If it's EC, convert to raw format; otherwise return DER as-is
        if (cryptoSig is CryptoSignature.EC) {
            // Convert ECDSA signature from DER to raw format (r||s concatenated)
            // Get r and s as byte arrays and concatenate them
            val rBytes = cryptoSig.r.toByteArray()
            val sBytes = cryptoSig.s.toByteArray()

            // Helper function to convert BigInteger.toByteArray() to fixed-length unsigned bytes
            fun bigIntToFixedLengthBytes(bytes: ByteArray, length: Int): ByteArray {
                return when {
                    bytes.size == length -> bytes
                    bytes.size == length + 1 && bytes[0] == 0.toByte() -> bytes.copyOfRange(1, bytes.size)
                    bytes.size < length -> ByteArray(length - bytes.size) + bytes
                    else -> throw IllegalArgumentException("BigInteger value too large for expected length $length (got ${bytes.size} bytes)")
                }
            }

            // Ensure both are 32 bytes for P-256 (pad with leading zeros if needed, remove sign byte if present)
            val rPadded = bigIntToFixedLengthBytes(rBytes, 32)
            val sPadded = bigIntToFixedLengthBytes(sBytes, 32)

            rPadded + sPadded
        } else {
            // RSA signatures are already in the correct format
            derSignature
        }
    }
}

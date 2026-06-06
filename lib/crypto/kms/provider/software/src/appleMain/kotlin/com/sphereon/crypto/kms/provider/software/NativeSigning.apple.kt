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

package com.sphereon.crypto.kms.provider.software

import at.asitplus.awesn1.crypto.X509SignatureValue
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.kms.keystore.software.getNativeKeychainKey
import com.sphereon.crypto.kms.keystore.software.isNativeKeychainKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFStringGetCStringPtr
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecKeyCreateSignature
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA384
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA512
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA384
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA512
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePSSSHA256
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePSSSHA384
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePSSSHA512

/**
 * iOS implementation that signs data using a private key stored in the keychain.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun signWithNativeKey(
    keyInfo: KeyInfoType<*>,
    input: ByteArray,
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
    val algorithm =
        when (signatureAlgorithm) {
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
        val inputData =
            CFDataCreate(null, input.asUByteArray().refTo(0), input.size.toLong())
                ?: throw IllegalStateException("Failed to create CFData from input")

        val error = alloc<CFErrorRefVar>()
        val signatureData =
            SecKeyCreateSignature(
                privateKey,
                algorithm,
                inputData,
                error.ptr,
            )

        if (signatureData == null) {
            val errorDesc =
                CFErrorCopyDescription(error.value)?.let {
                    CFStringGetCStringPtr(it, kCFStringEncodingUTF8)?.toKString()
                } ?: "Unknown error"
            throw IllegalStateException("Failed to sign data with native keychain key: $errorDesc")
        }

        // Convert CFData to ByteArray (DER format)
        val length = CFDataGetLength(signatureData).toInt()
        val bytes = CFDataGetBytePtr(signatureData)
        val derSignature = ByteArray(length) { bytes!![it].toByte() }

        // iOS returns ECDSA signatures in DER format, but we need raw format (r||s)
        val isEcdsa =
            signatureAlgorithm in
                arrayOf(
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureAlgorithm.ECDSA_SHA384,
                    SignatureAlgorithm.ECDSA_SHA512,
                )

        if (isEcdsa) {
            val (r, s) = X509SignatureValue(derSignature).decodeRS()
            val rBytes = r.magnitude
            val sBytes = s.magnitude

            val coordSize =
                when (signatureAlgorithm) {
                    SignatureAlgorithm.ECDSA_SHA384 -> 48
                    SignatureAlgorithm.ECDSA_SHA512 -> 66
                    else -> 32 // P-256
                }

            fun toFixedLength(
                bytes: ByteArray,
                length: Int,
            ): ByteArray =
                when {
                    bytes.size == length -> bytes
                    bytes.size == length + 1 && bytes[0] == 0.toByte() -> bytes.copyOfRange(1, bytes.size)
                    bytes.size < length -> ByteArray(length - bytes.size) + bytes
                    else -> throw IllegalArgumentException("Value too large for expected length $length (got ${bytes.size} bytes)")
                }

            toFixedLength(rBytes, coordSize) + toFixedLength(sBytes, coordSize)
        } else {
            // RSA signatures are already in the correct format
            derSignature
        }
    }
}

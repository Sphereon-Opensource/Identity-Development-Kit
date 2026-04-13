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

package com.sphereon.mdoc.transport.ble

import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.Foundation.*
import platform.posix.memcpy

/**
 * iOS-specific implementation of HKDF provider using CommonCrypto APIs.
 */
class IosHkdfProvider : HkdfProvider {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        // Use NSData for easier interop with CommonCrypto
        val ikmData = ikm.toNSData()
        val saltData = if (salt.isEmpty()) ByteArray(32).toNSData() else salt.toNSData()
        val infoData = info.toNSData()
        
        // Allocate output buffer
        val outputData = NSMutableData.create(length = outputLength.toULong())!!
        
        // Step 1: Extract - PRK = HMAC-SHA256(salt, IKM)
        val prk = NSMutableData.create(length = CC_SHA256_DIGEST_LENGTH.toULong())!!
        
        memScoped {
            val context = alloc<CCHmacContext>()
            CCHmacInit(
                context.ptr,
                kCCHmacAlgSHA256.toUInt(),
                saltData.bytes,
                saltData.length.toULong()
            )
            CCHmacUpdate(context.ptr, ikmData.bytes, ikmData.length.toULong())
            CCHmacFinal(context.ptr, prk.mutableBytes)
        }
        
        // Step 2: Expand - OKM = HKDF-Expand(PRK, info, L)
        val n = (outputLength + CC_SHA256_DIGEST_LENGTH - 1) / CC_SHA256_DIGEST_LENGTH
        val okm = NSMutableData()
        var t = NSMutableData()
        
        for (i in 1..n) {
            memScoped {
                val context = alloc<CCHmacContext>()
                CCHmacInit(
                    context.ptr,
                    kCCHmacAlgSHA256.toUInt(),
                    prk.bytes,
                    prk.length.toULong()
                )
                
                // Update with previous T
                if (t.length > 0u) {
                    CCHmacUpdate(context.ptr, t.bytes, t.length.toULong())
                }
                
                // Update with info
                CCHmacUpdate(context.ptr, infoData.bytes, infoData.length.toULong())
                
                // Update with counter
                val counter = i.toByte()
                CCHmacUpdate(context.ptr, cValuesOf(counter).ptr, 1u)
                
                // Finalize to get T
                val tBytes = NSMutableData.create(length = CC_SHA256_DIGEST_LENGTH.toULong())!!
                CCHmacFinal(context.ptr, tBytes.mutableBytes)
                t = tBytes
                
                okm.appendData(t)
            }
        }
        
        // Return only the requested number of bytes
        return okm.subdataWithRange(NSMakeRange(0u, outputLength.toULong())).toByteArray()
    }
}

/**
 * Convert ByteArray to NSData for iOS interop.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    return if (isEmpty()) {
        NSData()
    } else {
        memScoped {
            NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
        }
    }
}

/**
 * Convert NSData to ByteArray for iOS interop.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun NSData.toByteArray(): ByteArray {
    return ByteArray(length.toInt()).apply {
        if (isNotEmpty()) {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }
    }
}

/**
 * Platform-specific factory for creating HKDF providers on iOS.
 */
actual fun createPlatformHkdfProvider(): HkdfProvider = IosHkdfProvider()

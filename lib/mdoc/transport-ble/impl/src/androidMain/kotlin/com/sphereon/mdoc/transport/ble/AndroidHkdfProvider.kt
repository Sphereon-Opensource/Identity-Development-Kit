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

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android-specific implementation of HKDF provider using javax.crypto APIs.
 */
class AndroidHkdfProvider : HkdfProvider {
    override suspend fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        // HKDF-SHA256 implementation for Android
        // HKDF(salt, IKM) = (PRK, OKM)
        // PRK = HMAC-Hash(salt, IKM)
        // OKM = HKDF-Expand(PRK, info, L)

        val mac = Mac.getInstance("HmacSHA256")

        // Step 1: Extract - PRK = HMAC-Hash(salt, IKM)
        val saltKey = if (salt.isEmpty()) ByteArray(32) else salt  // Use zero salt if empty
        mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        // Step 2: Expand - OKM = HKDF-Expand(PRK, info, L)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val n = (outputLength + 31) / 32  // Ceiling division
        val okm = ByteArray(outputLength)
        var t = ByteArray(0)

        for (i in 1..n) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()

            val copyLength = minOf(outputLength - (i - 1) * 32, 32)
            t.copyInto(okm, (i - 1) * 32, 0, copyLength)
        }

        return okm
    }
}

/**
 * Platform-specific factory for creating HKDF providers on Android.
 */
actual fun createPlatformHkdfProvider(): HkdfProvider = AndroidHkdfProvider()

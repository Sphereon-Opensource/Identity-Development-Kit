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

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES

/**
 * Linux implementation of AES Key Wrap/Unwrap (RFC 3394) using AES-ECB.
 */
@OptIn(DelicateCryptographyApi::class)
internal actual suspend fun aesKeyWrap(
    kek: ByteArray,
    plaintext: ByteArray,
): ByteArray {
    val provider = CryptographyProvider.Default
    val aes = provider.get(AES.ECB)
    val aesKey = aes.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, kek)
    val cipher = aesKey.cipher(padding = false)

    val n = plaintext.size / 8

    var a = AES_KW_DEFAULT_IV.copyOf()
    val r = Array(n) { i -> plaintext.copyOfRange(i * 8, (i + 1) * 8) }

    val block = ByteArray(16)
    for (j in 0..5) {
        for (i in 0 until n) {
            a.copyInto(block, 0, 0, 8)
            r[i].copyInto(block, 8, 0, 8)
            val b = cipher.encrypt(block)

            val t = (n * j) + i + 1
            a = b.copyOfRange(0, 8)
            xorWithCounter(a, t.toLong())

            r[i] = b.copyOfRange(8, 16)
        }
    }

    val result = ByteArray(plaintext.size + 8)
    a.copyInto(result, 0, 0, 8)
    for (i in 0 until n) {
        r[i].copyInto(result, (i + 1) * 8, 0, 8)
    }

    return result
}

@OptIn(DelicateCryptographyApi::class)
internal actual suspend fun aesKeyUnwrap(
    kek: ByteArray,
    ciphertext: ByteArray,
): ByteArray {
    val provider = CryptographyProvider.Default
    val aes = provider.get(AES.ECB)
    val aesKey = aes.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, kek)
    val cipher = aesKey.cipher(padding = false)

    val n = (ciphertext.size / 8) - 1

    var a = ciphertext.copyOfRange(0, 8)
    val r = Array(n) { i -> ciphertext.copyOfRange((i + 1) * 8, (i + 2) * 8) }

    val block = ByteArray(16)
    for (j in 5 downTo 0) {
        for (i in (n - 1) downTo 0) {
            val t = (n * j) + i + 1

            val aXorT = a.copyOf()
            xorWithCounter(aXorT, t.toLong())

            aXorT.copyInto(block, 0, 0, 8)
            r[i].copyInto(block, 8, 0, 8)
            val b = cipher.decrypt(block)

            a = b.copyOfRange(0, 8)
            r[i] = b.copyOfRange(8, 16)
        }
    }

    if (!a.contentEquals(AES_KW_DEFAULT_IV)) {
        throw IllegalStateException(
            "AES Key Unwrap integrity check failed - wrong key or corrupted data",
        )
    }

    val result = ByteArray(n * 8)
    for (i in 0 until n) {
        r[i].copyInto(result, i * 8, 0, 8)
    }

    return result
}

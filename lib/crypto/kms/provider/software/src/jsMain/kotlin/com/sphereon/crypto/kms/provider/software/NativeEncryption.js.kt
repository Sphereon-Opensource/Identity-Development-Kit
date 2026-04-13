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

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * JS implementation of AES Key Wrap/Unwrap (RFC 3394) using WebCrypto native AES-KW.
 * WebCrypto does not support AES-ECB, but it does support AES-KW natively.
 */

private fun ByteArray.toJsInt8Array(): Int8Array {
    val result = Int8Array(this.size)
    for (i in indices) {
        result.asDynamic()[i] = this[i]
    }
    return result
}

private fun ArrayBuffer.toKotlinByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}

internal actual suspend fun aesKeyWrap(
    kek: ByteArray,
    plaintext: ByteArray,
): ByteArray {
    val subtle = js("globalThis.crypto.subtle")

    val kekBuffer = kek.toJsInt8Array().buffer
    val plaintextBuffer = plaintext.toJsInt8Array().buffer

    val wrappingKey =
        (
            subtle.importKey(
                "raw",
                kekBuffer,
                js("({name:'AES-KW'})"),
                false,
                js("['wrapKey']"),
            ) as Promise<dynamic>
        ).await()

    val keyToWrap =
        (
            subtle.importKey(
                "raw",
                plaintextBuffer,
                js("({name:'HMAC',hash:'SHA-256'})"),
                true,
                js("['sign']"),
            ) as Promise<dynamic>
        ).await()

    val wrappedBuffer =
        (
            subtle.wrapKey(
                "raw",
                keyToWrap,
                wrappingKey,
                js("({name:'AES-KW'})"),
            ) as Promise<dynamic>
        ).await()

    return (wrappedBuffer as ArrayBuffer).toKotlinByteArray()
}

internal actual suspend fun aesKeyUnwrap(
    kek: ByteArray,
    ciphertext: ByteArray,
): ByteArray {
    val subtle = js("globalThis.crypto.subtle")

    val kekBuffer = kek.toJsInt8Array().buffer
    val ciphertextBuffer = ciphertext.toJsInt8Array().buffer

    val unwrappingKey =
        (
            subtle.importKey(
                "raw",
                kekBuffer,
                js("({name:'AES-KW'})"),
                false,
                js("['unwrapKey']"),
            ) as Promise<dynamic>
        ).await()

    try {
        val unwrappedKey =
            (
                subtle.unwrapKey(
                    "raw",
                    ciphertextBuffer,
                    unwrappingKey,
                    js("({name:'AES-KW'})"),
                    js("({name:'HMAC',hash:'SHA-256'})"),
                    true,
                    js("['sign']"),
                ) as Promise<dynamic>
            ).await()

        val rawBuffer = (subtle.exportKey("raw", unwrappedKey) as Promise<dynamic>).await()
        return (rawBuffer as ArrayBuffer).toKotlinByteArray()
    } catch (expected: Throwable) {
        throw IllegalStateException(
            "AES Key Unwrap integrity check failed - wrong key or corrupted data",
            expected,
        )
    }
}

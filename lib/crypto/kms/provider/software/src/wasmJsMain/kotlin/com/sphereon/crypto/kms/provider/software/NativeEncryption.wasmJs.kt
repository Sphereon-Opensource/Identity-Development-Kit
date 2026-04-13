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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.sphereon.crypto.kms.provider.software

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop

/**
 * wasmJs implementation of AES Key Wrap/Unwrap (RFC 3394) using WebCrypto native AES-KW.
 * WebCrypto does not support AES-ECB, but it does support AES-KW natively.
 *
 * Uses callback-based JS interop since kotlinx.coroutines.await() is not available
 * for kotlin.js.Promise on the wasmJs target.
 *
 * -- Byte array conversion helpers --
 */

@JsFun("(size) => new Int8Array(size)")
private external fun jsCreateInt8Array(size: Int): JsAny

@JsFun("(arr, i, v) => { arr[i] = v; }")
private external fun jsSetByte(
    arr: JsAny,
    i: Int,
    v: Byte,
)

@JsFun("(arr) => arr.length")
private external fun jsArrayLength(arr: JsAny): Int

@JsFun("(arr, i) => arr[i]")
private external fun jsGetByte(
    arr: JsAny,
    i: Int,
): Byte

// -- WebCrypto AES-KW operations with callbacks --

@JsFun(
    """(kek, plaintext, onSuccess, onError) => {
    (async () => {
        try {
            const kekKey = await crypto.subtle.importKey(
                'raw', kek.buffer.slice(kek.byteOffset, kek.byteOffset + kek.byteLength),
                { name: 'AES-KW' }, false, ['wrapKey']
            );
            const toWrap = await crypto.subtle.importKey(
                'raw', plaintext.buffer.slice(plaintext.byteOffset, plaintext.byteOffset + plaintext.byteLength),
                { name: 'HMAC', hash: 'SHA-256' }, true, ['sign']
            );
            const wrapped = await crypto.subtle.wrapKey('raw', toWrap, kekKey, { name: 'AES-KW' });
            onSuccess(new Int8Array(wrapped));
        } catch (e) {
            onError('' + e);
        }
    })();
}""",
)
private external fun aesKeyWrapAsync(
    kek: JsAny,
    plaintext: JsAny,
    onSuccess: (JsAny) -> Unit,
    onError: (JsAny) -> Unit,
)

@JsFun(
    """(kek, ciphertext, onSuccess, onError) => {
    (async () => {
        try {
            const kekKey = await crypto.subtle.importKey(
                'raw', kek.buffer.slice(kek.byteOffset, kek.byteOffset + kek.byteLength),
                { name: 'AES-KW' }, false, ['unwrapKey']
            );
            const unwrapped = await crypto.subtle.unwrapKey(
                'raw',
                ciphertext.buffer.slice(ciphertext.byteOffset, ciphertext.byteOffset + ciphertext.byteLength),
                kekKey,
                { name: 'AES-KW' },
                { name: 'HMAC', hash: 'SHA-256' },
                true, ['sign']
            );
            const raw = await crypto.subtle.exportKey('raw', unwrapped);
            onSuccess(new Int8Array(raw));
        } catch (e) {
            onError('' + e);
        }
    })();
}""",
)
private external fun aesKeyUnwrapAsync(
    kek: JsAny,
    ciphertext: JsAny,
    onSuccess: (JsAny) -> Unit,
    onError: (JsAny) -> Unit,
)

// -- Conversion between Kotlin ByteArray and JS Int8Array --

private fun ByteArray.toJsInt8Array(): JsAny {
    val result = jsCreateInt8Array(this.size)
    for (i in indices) {
        jsSetByte(result, i, this[i])
    }
    return result
}

private fun JsAny.toKotlinByteArray(): ByteArray {
    val len = jsArrayLength(this)
    return ByteArray(len) { jsGetByte(this, it) }
}

// -- Actual implementations --

internal actual suspend fun aesKeyWrap(
    kek: ByteArray,
    plaintext: ByteArray,
): ByteArray =
    suspendCoroutine { cont ->
        aesKeyWrapAsync(
            kek.toJsInt8Array(),
            plaintext.toJsInt8Array(),
            onSuccess = { result -> cont.resume(result.toKotlinByteArray()) },
            onError = { error -> cont.resumeWithException(Exception("AES-KW wrap failed: $error")) },
        )
    }

internal actual suspend fun aesKeyUnwrap(
    kek: ByteArray,
    ciphertext: ByteArray,
): ByteArray =
    suspendCoroutine { cont ->
        aesKeyUnwrapAsync(
            kek.toJsInt8Array(),
            ciphertext.toJsInt8Array(),
            onSuccess = { result -> cont.resume(result.toKotlinByteArray()) },
            onError = { _ ->
                cont.resumeWithException(
                    IllegalStateException("AES Key Unwrap integrity check failed - wrong key or corrupted data"),
                )
            },
        )
    }

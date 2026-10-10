/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.core.security

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/**
 * JS AEAD backed by `@noble/ciphers` (RFC 8439 IETF ChaCha20-Poly1305).
 *
 * libsodium-bindings cannot back JS: its file-level `libsodium-sumo` externals compile to named
 * ES-module imports, and `libsodium-sumo` offers no such named exports, so the module fails to
 * load. `@noble/ciphers` ships ES modules with named exports and needs no initialisation.
 */
internal actual class ChaCha20Poly1305Aead actual constructor() {
    actual suspend fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray =
        cipher(key, nonce, associatedData)
            .encrypt(plaintext.asUint8Array())
            .asByteArray()

    actual suspend fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray =
        cipher(key, nonce, associatedData)
            .decrypt(ciphertextAndTag.asUint8Array())
            .asByteArray()
}

private fun cipher(key: ByteArray, nonce: ByteArray, associatedData: ByteArray?): NobleAeadCipher =
    if (associatedData == null) {
        chacha20poly1305(key.asUint8Array(), nonce.asUint8Array())
    } else {
        chacha20poly1305(key.asUint8Array(), nonce.asUint8Array(), associatedData.asUint8Array())
    }

// A Kotlin/JS ByteArray is an Int8Array, so both conversions are views over the same buffer.
private fun ByteArray.asUint8Array(): Uint8Array {
    val bytes = unsafeCast<Int8Array>()
    return Uint8Array(bytes.buffer, bytes.byteOffset, bytes.length)
}

private fun Uint8Array.asByteArray(): ByteArray = Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>()

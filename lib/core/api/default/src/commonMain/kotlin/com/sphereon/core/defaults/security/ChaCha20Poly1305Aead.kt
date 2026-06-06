/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.defaults.security

/**
 * Platform AEAD primitive for [ChaCha20Poly1305EncryptionService] (RFC 8439, IETF
 * construction: 32-byte key, 12-byte nonce, 16-byte Poly1305 tag).
 *
 * Backed by libsodium-bindings on JVM / JS / native (`nonWasmMain` actual). libsodium
 * publishes no wasmJs artifact, so the `wasmJsMain` actual throws
 * [UnsupportedOperationException] — ChaCha20-Poly1305 at-rest encryption is unavailable on
 * wasmJs; deployments targeting wasmJs use AES-256-GCM ([AesGcmEncryptionService]) instead.
 *
 * Both operations follow libsodium's combined-mode layout: [seal] returns `ciphertext || tag`
 * and [open] expects the same concatenation. The service splits/joins the 16-byte tag so the
 * [com.sphereon.core.api.security.EncryptedBlob] envelope pins each section explicitly.
 */
internal expect class ChaCha20Poly1305Aead() {
    /**
     * Encrypt [plaintext] under [key] with [nonce], binding [associatedData] (null → no AAD).
     * Returns `ciphertext || tag` (tag = trailing 16 bytes).
     */
    suspend fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray

    /**
     * Decrypt [ciphertextAndTag] (`ciphertext || tag`) under [key] with [nonce] and
     * [associatedData] (null → no AAD). Throws on auth-tag / AAD mismatch.
     */
    suspend fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray
}

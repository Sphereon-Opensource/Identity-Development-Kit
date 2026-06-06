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
 * wasmJs has no ChaCha20-Poly1305 AEAD: libsodium-bindings publishes no wasmJs artifact and the
 * WebCrypto provider has no ChaCha20 support. This actual exists only to satisfy the
 * `expect`/`actual` contract; any use throws [UnsupportedOperationException]. Deployments
 * targeting wasmJs use AES-256-GCM ([AesGcmEncryptionService]) for at-rest encryption.
 */
internal actual class ChaCha20Poly1305Aead actual constructor() {
    actual suspend fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray = unsupported()

    actual suspend fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException(
            "ChaCha20-Poly1305 is not available on wasmJs (no libsodium artifact, and WebCrypto " +
                "has no ChaCha20 support). Use AES-256-GCM (AesGcmEncryptionService) instead.",
        )
}

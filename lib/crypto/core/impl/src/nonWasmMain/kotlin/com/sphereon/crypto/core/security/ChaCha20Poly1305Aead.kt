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

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JVM / JS / native AEAD backed by libsodium's `crypto_aead_chacha20poly1305_ietf_*`
 * (RFC 8439). libsodium performs the AEAD as a single stateless call, so — unlike the JDK
 * `ChaCha20-Poly1305` cipher, which rejects re-init with a previously-seen key+nonce — an
 * encrypt-then-decrypt round-trip under the same key and nonce works as expected.
 *
 * libsodium must be initialised once before use; [ensureInitialised] does that lazily under a
 * [Mutex] (the same idiom as `Argon2idPasswordHasher`). Per-instance state is only ever
 * touched while holding the lock.
 */
internal actual class ChaCha20Poly1305Aead actual constructor() {
    private val initLock = Mutex()
    private var initialised = false

    actual suspend fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray {
        ensureInitialised()
        // Param order matches libsodium's crypto_aead_chacha20poly1305_ietf_encrypt:
        // (message, additionalData, nonce, key). Returns ciphertext || 16-byte tag.
        return AuthenticatedEncryptionWithAssociatedData.chaCha20Poly1305IetfEncrypt(
            plaintext.toUByteArray(),
            (associatedData ?: EMPTY).toUByteArray(),
            nonce.toUByteArray(),
            key.toUByteArray(),
        ).toByteArray()
    }

    actual suspend fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        associatedData: ByteArray?,
    ): ByteArray {
        ensureInitialised()
        // Throws AeadCorrupedOrTamperedDataException on tag / AAD mismatch — propagated to the
        // service's runCatching, which maps it to an Err.
        return AuthenticatedEncryptionWithAssociatedData.chaCha20Poly1305IetfDecrypt(
            ciphertextAndTag.toUByteArray(),
            (associatedData ?: EMPTY).toUByteArray(),
            nonce.toUByteArray(),
            key.toUByteArray(),
        ).toByteArray()
    }

    private suspend fun ensureInitialised() {
        // Always under the lock (uncontended once initialised) so `initialised` is never
        // read or written without synchronisation — avoids a data race without @Volatile.
        initLock.withLock {
            if (!initialised) {
                LibsodiumInitializer.initialize()
                initialised = true
            }
        }
    }

    private companion object {
        private val EMPTY = ByteArray(0)
    }
}

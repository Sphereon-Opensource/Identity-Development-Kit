/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:JsModule("@noble/ciphers/chacha.js")

package com.sphereon.crypto.core.security

import org.khronos.webgl.Uint8Array

/** One-shot ChaCha20-Poly1305 instance from `@noble/ciphers`, bound to a single key and nonce. */
internal external interface NobleAeadCipher {
    /** Returns `ciphertext || tag`. */
    fun encrypt(plaintext: Uint8Array): Uint8Array

    /** Expects `ciphertext || tag`; throws on a tag or AAD mismatch. */
    fun decrypt(ciphertext: Uint8Array): Uint8Array
}

/** Omit [AAD] when there is none: noble accepts an absent AAD but rejects `null`. */
internal external fun chacha20poly1305(
    key: Uint8Array,
    nonce: Uint8Array,
    AAD: Uint8Array = definedExternally,
): NobleAeadCipher

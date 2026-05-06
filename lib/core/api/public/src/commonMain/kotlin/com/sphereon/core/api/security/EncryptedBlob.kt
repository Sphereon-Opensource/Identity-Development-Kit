/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.security

import kotlinx.serialization.Serializable

/**
 * Wire-portable container for an authenticated-encryption ciphertext. Stored as a single
 * column (typically `TEXT`/`bytea` after [serialise]/`deserialise`) so the database
 * never sees plaintext for any field that goes through
 * [com.sphereon.core.api.security.EncryptionService].
 *
 * Carries the [keyId] that produced the ciphertext so the verify path knows which
 * historical key to look up — supports key rotation: the active key encrypts new rows,
 * older rows decrypt with whichever key originally wrote them, and a key never deleted
 * until every row encrypted under it has been re-encrypted.
 *
 * Format is opinionated:
 *  - [iv]: nonce / initialization vector. AES-GCM expects 12 bytes; AES-CCM and other
 *    modes vary.
 *  - [ciphertext]: the encrypted payload, NOT including the auth tag.
 *  - [authTag]: the GCM / CCM authentication tag, separated so callers don't have to
 *    parse the combined output. Always exactly the cipher's tag length (16 bytes for
 *    AES-GCM with 128-bit tag).
 *
 * The struct is intentionally small + serialisable so a column adapter can convert to
 * `bytea` (CBOR) or `text` (JSON / base64-tagged).
 */
@Serializable
data class EncryptedBlob(
    val keyId: String,
    val algorithm: String,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val authTag: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBlob) return false
        return keyId == other.keyId &&
            algorithm == other.algorithm &&
            iv.contentEquals(other.iv) &&
            ciphertext.contentEquals(other.ciphertext) &&
            authTag.contentEquals(other.authTag)
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }

    companion object {
        /** Algorithm string for AES-256-GCM (matches JWE / RFC 7518 §5.3 naming). */
        const val ALG_AES_256_GCM: String = "A256GCM"
    }
}

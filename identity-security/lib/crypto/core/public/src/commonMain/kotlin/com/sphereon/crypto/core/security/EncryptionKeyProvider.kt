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

/**
 * Source of symmetric encryption keys for [EncryptionService]. Pluggable so a
 * production deployment can override the default in-memory provider with one backed by
 * [com.sphereon.core.api.conf.SecretProvider] (load a static key from
 * Vault / AWS Secrets Manager / Azure Key Vault) OR by an EDK module that does
 * KMS-wrapped envelope encryption (the root key never leaves the cloud KMS; the impl
 * unwraps a per-tenant DEK on demand).
 *
 * The provider exposes ONE active key (used to encrypt new ciphertexts) and a lookup
 * method for HISTORICAL keys (used to decrypt older ciphertexts whose `keyId` no
 * longer matches the active key — the rotation path).
 */
interface EncryptionKeyProvider {
    /**
     * Currently-active encryption key. New ciphertexts are written under this key id.
     * Returns null only during a degraded state (e.g. the deployment has not yet
     * configured a key); production deployments MUST always have an active key.
     */
    suspend fun activeKey(): EncryptionKeyMaterial?

    /**
     * Look up an encryption key by id. Used by the decrypt path to find the historical
     * key that wrote an [EncryptedBlob.keyId]. Returns null when the key is unknown
     * (rotated out and the cleanup grace period elapsed) — the caller treats this as
     * "ciphertext is permanently undecryptable" and surfaces the appropriate error.
     */
    suspend fun findKey(keyId: String): EncryptionKeyMaterial?
}

/**
 * A symmetric key, opaque to the caller. The [keyBytes] are sensitive; production
 * impls SHOULD avoid passing them through logs. AES-256 expects 32 bytes; AES-128 and
 * AES-192 are defined for legacy compatibility but NOT recommended for new
 * deployments (whole-suite GCM with a 256-bit key is the modern standard).
 */
data class EncryptionKeyMaterial(
    val keyId: String,
    val keyBytes: ByteArray,
    /**
     * The algorithm this key is meant to be used with — `A256GCM` for the default
     * AES-256-GCM impl. The encryption service validates that the active key's
     * algorithm matches its own algorithm at startup.
     */
    val algorithm: String = EncryptedBlob.ALG_AES_256_GCM,
) {
    init {
        require(keyId.isNotBlank()) { "keyId must not be blank" }
        when (algorithm) {
            EncryptedBlob.ALG_AES_256_GCM -> {
                require(keyBytes.size == 32) {
                    "AES-256-GCM requires a 32-byte key, got ${keyBytes.size}"
                }
            }
            EncryptedBlob.ALG_CHACHA20_POLY1305 -> {
                require(keyBytes.size == 32) {
                    "ChaCha20-Poly1305 requires a 32-byte key, got ${keyBytes.size}"
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptionKeyMaterial) return false
        return keyId == other.keyId && algorithm == other.algorithm && keyBytes.contentEquals(other.keyBytes)
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + keyBytes.contentHashCode()
        return result
    }
}

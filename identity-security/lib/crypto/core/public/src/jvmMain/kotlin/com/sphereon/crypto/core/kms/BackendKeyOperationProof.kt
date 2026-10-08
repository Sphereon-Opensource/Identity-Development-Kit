/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.core.kms

import com.sphereon.crypto.core.KeyInfoType

/**
 * JVM-internal cloud-KMS seam for operation-bound backend key proof.
 *
 * The public encryption result intentionally cannot carry physical provider locators. Implementations
 * of this interface resolve an immutable backend key identity with provider-authenticated metadata,
 * bind the successful crypto response to that identity, and return only its canonical digest.
 */
interface BackendKeyOperationProofProvider {
    suspend fun encryptWithBackendKeyProof(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
    ): BackendKeyProvedEncryption

    suspend fun decryptWithBackendKeyProof(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?,
        expectedBackendKeyIdentityDigest: String,
    ): BackendKeyProvedDecryption
}

/**
 * Provider-local lifecycle for symmetric, hardware-protected, non-exportable encryption keys.
 *
 * [bindingAlias] is a server-derived provisioning handle. Successful creation/resolution returns an
 * immutable backend identity (AWS key ARN or versioned Azure key id); callers must persist/use that
 * exact identity for crypto. Implementations never return key material.
 */
interface BackendSymmetricKmsKeyLifecycle {
    suspend fun resolveImmutableBackendKeyIdentity(bindingAlias: String): String?

    suspend fun createSymmetricNonExportableKey(bindingAlias: String): String

    suspend fun revokeSymmetricNonExportableKey(bindingAlias: String)
}

class BackendKeyProvedEncryption(
    val encryption: EncryptionResult,
    val backendKeyIdentityDigest: String,
) {
    override fun toString(): String = "BackendKeyProvedEncryption(identity=[REDACTED],material=[REDACTED])"
}

class BackendKeyProvedDecryption(
    plaintext: ByteArray,
    val backendKeyIdentityDigest: String,
) : AutoCloseable {
    private var plaintextBytes: ByteArray? = plaintext.copyOf()

    fun <T> usePlaintext(block: (ByteArray) -> T): T {
        val owned =
            synchronized(this) {
                plaintextBytes?.also { plaintextBytes = null }
                    ?: throw IllegalStateException("Backend key proved plaintext is closed")
            }
        return try {
            block(owned)
        } finally {
            owned.fill(0)
        }
    }

    override fun close() {
        synchronized(this) {
            plaintextBytes?.fill(0)
            plaintextBytes = null
        }
    }

    override fun toString(): String = "BackendKeyProvedDecryption(identity=[REDACTED],material=[REDACTED])"
}

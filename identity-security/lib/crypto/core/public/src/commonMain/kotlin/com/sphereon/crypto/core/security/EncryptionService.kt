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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.ServiceFacade

/**
 * Symmetric envelope-encryption facade for sensitive at-rest values (federated IdP
 * client secrets, audit-event payloads, future refresh-token bytes, etc.). The default
 * impl ships AES-256-GCM via whyoleg cryptography-core; production deployments can
 * contribute a KMS-wrapped variant where the data-encryption key (DEK) is wrapped by a
 * cloud KMS root key (`wrapKey`/`unwrapKey`) without changing the call sites.
 *
 * The facade exposes per-operation [ServiceCommand]s via [commands] so routing /
 * audit / replacement happens at command granularity. The convenience methods on the
 * facade (`encrypt`, `decrypt`) delegate to those commands.
 */
interface EncryptionService : ServiceFacade {
    override val serviceId: String get() = SERVICE_ID

    /**
     * Encrypt [plaintext] under the active encryption key. Returns an [EncryptedBlob]
     * carrying the key id + iv + ciphertext + auth tag. The returned blob can be
     * serialised and stored as a single column; later [decrypt] resolves the key id
     * back to the appropriate key (handles rotation).
     *
     * [associatedData] is bound into the auth tag (per AEAD): if the column is
     * tenant-scoped, pass the tenant id so a row's ciphertext cannot be lifted into a
     * different tenant. The same value MUST be supplied on decrypt or the auth-tag
     * check fails.
     */
    suspend fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray? = null,
    ): IdkResult<EncryptedBlob, IdkError>

    /**
     * Decrypt [blob], using the key identified by [EncryptedBlob.keyId]. Returns the
     * original plaintext, or an error when the key is unknown / the auth tag fails.
     * [associatedData] MUST match what was supplied at encrypt time (per AEAD).
     */
    suspend fun decrypt(
        blob: EncryptedBlob,
        associatedData: ByteArray? = null,
    ): IdkResult<ByteArray, IdkError>

    val commands: Commands

    /**
     * Per-operation [ServiceCommand] handles. Each is AppScope and individually
     * replaceable via DI — a deployment can swap encrypt (e.g. to add KMS DEK
     * wrapping) without touching decrypt's verify-only path.
     */
    interface Commands {
        val encrypt: EncryptCommand
        val decrypt: DecryptCommand
    }

    companion object {
        const val SERVICE_ID: String = "core.security.encryption"
    }
}

// ─── Commands ───────────────────────────────────────────────────────────────

data class EncryptArgs(
    val plaintext: ByteArray,
    val associatedData: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptArgs) return false
        return plaintext.contentEquals(other.plaintext) &&
            (associatedData?.contentEquals(other.associatedData ?: ByteArray(0)) ?: (other.associatedData == null))
    }

    override fun hashCode(): Int = plaintext.contentHashCode() * 31 + (associatedData?.contentHashCode() ?: 0)
}

interface EncryptCommand : ServiceCommand<EncryptArgs, EncryptedBlob, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val inputTypeToken get() = INPUT_TYPE_TOKEN
    override val outputTypeToken get() = OUTPUT_TYPE_TOKEN

    companion object {
        const val COMMAND_ID: String = "core.security.encrypt"
        val INPUT_TYPE_TOKEN = typeToken<EncryptArgs>()
        val OUTPUT_TYPE_TOKEN = typeToken<EncryptedBlob>()
    }
}

data class DecryptArgs(
    val blob: EncryptedBlob,
    val associatedData: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecryptArgs) return false
        return blob == other.blob &&
            (associatedData?.contentEquals(other.associatedData ?: ByteArray(0)) ?: (other.associatedData == null))
    }

    override fun hashCode(): Int = blob.hashCode() * 31 + (associatedData?.contentHashCode() ?: 0)
}

data class DecryptResult(
    val plaintext: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is DecryptResult && plaintext.contentEquals(other.plaintext)

    override fun hashCode(): Int = plaintext.contentHashCode()
}

interface DecryptCommand : ServiceCommand<DecryptArgs, DecryptResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val inputTypeToken get() = INPUT_TYPE_TOKEN
    override val outputTypeToken get() = OUTPUT_TYPE_TOKEN

    companion object {
        const val COMMAND_ID: String = "core.security.decrypt"
        val INPUT_TYPE_TOKEN = typeToken<DecryptArgs>()
        val OUTPUT_TYPE_TOKEN = typeToken<DecryptResult>()
    }
}

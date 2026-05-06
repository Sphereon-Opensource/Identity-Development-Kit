/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package com.sphereon.core.defaults.security

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.security.EncryptedBlob
import com.sphereon.core.api.security.TenantEncryptionKeyProvider
import com.sphereon.core.api.security.TenantEncryptionService
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [TenantEncryptionService] backed by AES-256-GCM via whyoleg cryptography.
 * AppScope so a single instance serves every tenant; the per-tenant key is fetched
 * from [TenantEncryptionKeyProvider] on each call.
 *
 * **Tenant id binds into AAD**: even when the caller doesn't supply explicit AAD, the
 * service mixes [tenantId] into the auth tag so a row's ciphertext cannot be lifted
 * into another tenant's namespace by a tampered storage layer. The decrypt path
 * requires the same tenantId; mismatch fails the auth-tag check.
 *
 * **Wire format** matches [com.sphereon.core.defaults.security.AesGcmEncryptionService]
 * — 12-byte iv, 16-byte tag, `A256GCM` algorithm string. Blobs are interchangeable
 * between the per-user and per-tenant variants when both are configured against the
 * same key id.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<TenantEncryptionService>())
class AesGcmTenantEncryptionService(
    private val keyProvider: TenantEncryptionKeyProvider,
) : TenantEncryptionService {
    private val provider = CryptographyProvider.Default
    private val aesGcm = provider.get(AES.GCM)

    override suspend fun encrypt(
        tenantId: String,
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): IdkResult<EncryptedBlob, IdkError> =
        runCatching {
            val key =
                keyProvider.activeKey(tenantId) ?: return Err(
                    internalError(
                        "TenantEncryptionKeyProvider has no active key for tenant '$tenantId' — deployment misconfigured. " +
                            "Set tenants.$tenantId.security.encryption.activeKeyId.",
                    ),
                )
            if (key.algorithm != EncryptedBlob.ALG_AES_256_GCM) {
                return Err(
                    internalError(
                        "Active key algorithm '${key.algorithm}' for tenant '$tenantId' does not match " +
                            "AesGcmTenantEncryptionService's '${EncryptedBlob.ALG_AES_256_GCM}'",
                    ),
                )
            }

            val aad = mixTenantIntoAad(tenantId, associatedData)
            val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key.keyBytes)
            val iv = CryptographyRandom.nextBytes(AES_GCM_IV_BYTES)
            val cipher = aesKey.cipher(tagSize = AES_GCM_TAG_BITS.bits)
            val combined = cipher.encryptWithIv(iv = iv, plaintext = plaintext, associatedData = aad)
            val ciphertext = combined.copyOfRange(0, combined.size - AES_GCM_TAG_BYTES)
            val authTag = combined.copyOfRange(combined.size - AES_GCM_TAG_BYTES, combined.size)

            Ok(
                EncryptedBlob(
                    keyId = key.keyId,
                    algorithm = EncryptedBlob.ALG_AES_256_GCM,
                    iv = iv,
                    ciphertext = ciphertext,
                    authTag = authTag,
                ),
            )
        }.getOrElse { e ->
            Err(internalError("AES-256-GCM encrypt failed for tenant '$tenantId': ${e.message}", e))
        }

    override suspend fun decrypt(
        tenantId: String,
        blob: EncryptedBlob,
        associatedData: ByteArray?,
    ): IdkResult<ByteArray, IdkError> =
        runCatching {
            if (blob.algorithm != EncryptedBlob.ALG_AES_256_GCM) {
                return Err(
                    internalError(
                        "Cannot decrypt blob with algorithm '${blob.algorithm}' — this service handles AES-256-GCM only",
                    ),
                )
            }
            val key =
                keyProvider.findKey(tenantId, blob.keyId) ?: return Err(
                    internalError(
                        "Unknown encryption key id '${blob.keyId}' for tenant '$tenantId' — key was rotated out beyond the " +
                            "retention window, or the row belongs to a different tenant than supplied",
                    ),
                )

            val aad = mixTenantIntoAad(tenantId, associatedData)
            val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key.keyBytes)
            val cipher = aesKey.cipher(tagSize = AES_GCM_TAG_BITS.bits)
            val combined = blob.ciphertext + blob.authTag
            val plaintext = cipher.decryptWithIv(iv = blob.iv, ciphertext = combined, associatedData = aad)
            Ok(plaintext)
        }.getOrElse { e ->
            Err(
                internalError(
                    "AES-256-GCM decrypt failed for tenant '$tenantId' (likely auth-tag mismatch — wrong tenant, wrong AAD, " +
                        "or rotated-out key): ${e.message}",
                    e,
                ),
            )
        }

    /**
     * Mix the tenant id into the AEAD associated-data so a row's ciphertext is bound
     * to its tenant. A storage-layer attacker who copies a row from tenant A's
     * partition into tenant B's partition produces a blob whose decrypt fails the
     * auth-tag check (because the AAD = "tenant:A|<caller-aad>" no longer matches
     * what the decrypt path computes for tenant B).
     */
    private fun mixTenantIntoAad(
        tenantId: String,
        callerAad: ByteArray?
    ): ByteArray {
        val tenantPrefix = "tenant:$tenantId|".encodeToByteArray()
        return if (callerAad == null) tenantPrefix else tenantPrefix + callerAad
    }

    private fun internalError(
        detail: String,
        cause: Throwable? = null
    ): IdkError =
        IdkError(
            code = "ENCRYPTION_FAILURE",
            message =
                IdkError.Message(
                    i18nKey = "core.security.encryption.failure",
                    defaultMessage = detail,
                ),
            severity = IdkError.Severity.ERROR,
            category = ErrorCategory.INTERNAL,
            exception = cause as? Exception,
        )

    companion object {
        private const val AES_GCM_IV_BYTES: Int = 12
        private const val AES_GCM_TAG_BYTES: Int = 16
        private const val AES_GCM_TAG_BITS: Int = 128
    }
}

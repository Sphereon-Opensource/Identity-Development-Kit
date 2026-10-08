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

package com.sphereon.crypto.core.security

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.CommandAdapter
import com.sphereon.di.context.UserScope
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [EncryptionService] facade backed by AES-256-GCM via whyoleg cryptography.
 * Per-operation [com.sphereon.core.api.service.ServiceCommand]s expose `encrypt` and
 * `decrypt` for routed / audited use; the convenience methods on the facade delegate.
 *
 * **Algorithm choice**: AES-GCM with 12-byte iv + 128-bit auth tag matches RFC 7518
 * §5.3 (`A256GCM`) so the wire shape interoperates with JWE if a deployment ever
 * needs it. Only AES-256 is supported by the default; AES-128/192 are NOT exposed
 * because there's no good reason for new code to target them.
 *
 * **Key handling**: keys are sourced from [EncryptionKeyProvider]. The facade does
 * NOT cache keys — every encrypt fetches the active key, every decrypt looks the key
 * up by [EncryptedBlob.keyId]. Provider impls are expected to do whatever caching is
 * appropriate (in-memory provider is trivially fast; KMS-backed providers should
 * cache unwrapped DEKs with a short TTL).
 *
 * **Algorithm guard**: at the start of each encrypt the facade asserts that the
 * provider's active key carries the algorithm this service is hard-wired to. Mismatch
 * surfaces as an INTERNAL error rather than producing a ciphertext under the wrong
 * algorithm string.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<EncryptionService>())
class AesGcmEncryptionService(
    private val keyProvider: EncryptionKeyProvider,
) : EncryptionService {
    private val provider = CryptographyProvider.Default
    private val aesGcm = provider.get(AES.GCM)

    // ─── Facade methods (delegate to commands) ────────────────────

    override suspend fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): IdkResult<EncryptedBlob, IdkError> = commands.encrypt.execute(EncryptArgs(plaintext, associatedData))

    override suspend fun decrypt(
        blob: EncryptedBlob,
        associatedData: ByteArray?,
    ): IdkResult<ByteArray, IdkError> = commands.decrypt.execute(DecryptArgs(blob, associatedData)).map { it.plaintext }

    // ─── Commands ─────────────────────────────────────────────────

    override val commands: EncryptionService.Commands = CommandsImpl()

    private inner class CommandsImpl : EncryptionService.Commands {
        override val encrypt: EncryptCommand = EncryptCommandImpl()
        override val decrypt: DecryptCommand = DecryptCommandImpl()
    }

    private inner class EncryptCommandImpl :
        CommandAdapter<EncryptArgs, EncryptedBlob, IdkError>(id = EncryptCommand.COMMAND_ID),
        EncryptCommand {
        override val id: String get() = EncryptCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is EncryptArgs

        override suspend fun doExecute(
            args: EncryptArgs,
            applyDuring: (EncryptArgs) -> EncryptArgs,
        ): IdkResult<EncryptedBlob, IdkError> {
            val applied = applyDuring(args)
            return doEncrypt(applied.plaintext, applied.associatedData)
        }
    }

    private inner class DecryptCommandImpl :
        CommandAdapter<DecryptArgs, DecryptResult, IdkError>(id = DecryptCommand.COMMAND_ID),
        DecryptCommand {
        override val id: String get() = DecryptCommand.COMMAND_ID

        override suspend fun supports(args: Any): Boolean = args is DecryptArgs

        override suspend fun doExecute(
            args: DecryptArgs,
            applyDuring: (DecryptArgs) -> DecryptArgs,
        ): IdkResult<DecryptResult, IdkError> {
            val applied = applyDuring(args)
            return doDecrypt(applied.blob, applied.associatedData).map { DecryptResult(it) }
        }
    }

    // ─── Worker functions (the actual whyoleg AES.GCM calls) ──────

    private suspend fun doEncrypt(
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): IdkResult<EncryptedBlob, IdkError> =
        runCatching {
            val key =
                keyProvider.activeKey() ?: return Err(
                    internalError("EncryptionKeyProvider has no active key — deployment misconfigured"),
                )
            if (key.algorithm != EncryptedBlob.ALG_AES_256_GCM) {
                return Err(
                    internalError(
                        "Active key algorithm '${key.algorithm}' does not match AesGcmEncryptionService's '${EncryptedBlob.ALG_AES_256_GCM}'",
                    ),
                )
            }

            val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key.keyBytes)
            val iv = CryptographyRandom.nextBytes(AES_GCM_IV_BYTES)
            val cipher = aesKey.cipher(tagSize = AES_GCM_TAG_BITS.bits)
            // whyoleg returns ciphertext || authTag concatenated; split for the
            // EncryptedBlob shape that pins each section explicitly so downstream callers
            // never have to know the tag is appended.
            val combined = cipher.encryptWithIv(iv = iv, plaintext = plaintext, associatedData = associatedData)
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
            Err(internalError("AES-256-GCM encrypt failed: ${e.message}", e))
        }

    private suspend fun doDecrypt(
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
                keyProvider.findKey(blob.keyId) ?: return Err(
                    internalError("Unknown encryption key id '${blob.keyId}' — key was rotated out beyond the retention window"),
                )

            val aesKey = aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key.keyBytes)
            val cipher = aesKey.cipher(tagSize = AES_GCM_TAG_BITS.bits)
            // Re-concatenate ciphertext || authTag for whyoleg's decryptWithIv input shape.
            val combined = blob.ciphertext + blob.authTag
            val plaintext = cipher.decryptWithIv(iv = blob.iv, ciphertext = combined, associatedData = associatedData)
            Ok(plaintext)
        }.getOrElse { e ->
            Err(internalError("AES-256-GCM decrypt failed (likely auth-tag mismatch or wrong AAD): ${e.message}", e))
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

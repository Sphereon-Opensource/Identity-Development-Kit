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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.security.DecryptArgs
import com.sphereon.core.api.security.DecryptCommand
import com.sphereon.core.api.security.DecryptResult
import com.sphereon.core.api.security.EncryptArgs
import com.sphereon.core.api.security.EncryptCommand
import com.sphereon.core.api.security.EncryptedBlob
import com.sphereon.core.api.security.EncryptionKeyProvider
import com.sphereon.core.api.security.EncryptionService
import com.sphereon.core.api.session.CommandAdapter
import com.sphereon.di.context.UserScope
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Multiplatform [EncryptionService] backed by ChaCha20-Poly1305 (RFC 8439) via libsodium
 * ([ChaCha20Poly1305Aead]). The shape mirrors [AesGcmEncryptionService] exactly so consumers
 * can swap algorithms via DI without touching call sites; the only observable differences are
 * the [EncryptedBlob.algorithm] value (`C20P`) and the underlying primitive.
 *
 * **Algorithm choice**: 32-byte key, 12-byte nonce, 16-byte Poly1305 tag (RFC 8439).
 * Pair this service with an [EncryptionKeyProvider] whose active key carries
 * [EncryptedBlob.ALG_CHACHA20_POLY1305].
 *
 * **Why libsodium and not whyoleg**: AES-256-GCM uses whyoleg cryptography because every
 * platform provider backs it. ChaCha20-Poly1305 cannot use whyoleg here — the JDK provider's
 * `ChaCha20-Poly1305` cipher is pooled and rejects re-initialisation with a previously-seen
 * key+nonce ("Matching key and nonce from previous initialization"), which breaks the normal
 * encrypt-then-decrypt-with-the-same-nonce round-trip. libsodium performs the AEAD as a
 * stateless call and has no such guard. It is also already a platform dependency (Argon2id).
 *
 * **Platform support**: libsodium-bindings backs JVM, JS and native targets. It publishes no
 * wasmJs artifact, so the wasmJs [ChaCha20Poly1305Aead] actual throws
 * [UnsupportedOperationException]; this service is therefore unusable on wasmJs (deployments
 * there use AES-256-GCM). That exception is re-thrown rather than wrapped in an [IdkError] so
 * the platform limitation is unmistakable; genuine crypto failures (bad tag, wrong AAD) still
 * surface as `Err`.
 *
 * **Tag handling**: libsodium's combined mode returns `ciphertext || tag`. The service splits
 * the last 16 bytes off as [EncryptedBlob.authTag] so the envelope structure matches
 * [AesGcmEncryptionService]'s — downstream callers never have to know about the appended-tag
 * convention.
 *
 * **Algorithm guard**: at the start of each encrypt the facade asserts that the
 * provider's active key carries the algorithm this service is hard-wired to. Mismatch
 * surfaces as an INTERNAL error rather than producing a ciphertext under the wrong
 * algorithm string.
 *
 * **No `@ContributesBinding`**: [AesGcmEncryptionService] already contributes
 * the default `EncryptionService` binding at UserScope. Auto-contributing this
 * service too would produce a duplicate-binding compile error. Deployments that
 * want ChaCha20-Poly1305 as the active algorithm wire it manually via
 * `replaces = [AesGcmEncryptionService::class]` in their own component, or
 * alongside the AES service when the deployment needs both algorithms for
 * decrypt-side compatibility during a migration.
 */
@Inject
@SingleIn(UserScope::class)
class ChaCha20Poly1305EncryptionService(
    private val keyProvider: EncryptionKeyProvider,
) : EncryptionService {
    private val aead = ChaCha20Poly1305Aead()

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

    // ─── Worker functions (the actual libsodium ChaCha20-Poly1305 calls) ──────

    private suspend fun doEncrypt(
        plaintext: ByteArray,
        associatedData: ByteArray?,
    ): IdkResult<EncryptedBlob, IdkError> =
        runCatching {
            val key =
                keyProvider.activeKey() ?: return Err(
                    internalError("EncryptionKeyProvider has no active key — deployment misconfigured"),
                )
            if (key.algorithm != EncryptedBlob.ALG_CHACHA20_POLY1305) {
                return Err(
                    internalError(
                        "Active key algorithm '${key.algorithm}' does not match ChaCha20Poly1305EncryptionService's '${EncryptedBlob.ALG_CHACHA20_POLY1305}'",
                    ),
                )
            }

            val iv = CryptographyRandom.nextBytes(NONCE_BYTES)
            // libsodium returns ciphertext || authTag concatenated; split for the
            // EncryptedBlob shape that pins each section explicitly so downstream callers
            // never have to know the tag is appended.
            val combined = aead.seal(key = key.keyBytes, nonce = iv, plaintext = plaintext, associatedData = associatedData)
            val ciphertext = combined.copyOfRange(0, combined.size - TAG_BYTES)
            val authTag = combined.copyOfRange(combined.size - TAG_BYTES, combined.size)

            Ok(
                EncryptedBlob(
                    keyId = key.keyId,
                    algorithm = EncryptedBlob.ALG_CHACHA20_POLY1305,
                    iv = iv,
                    ciphertext = ciphertext,
                    authTag = authTag,
                ),
            )
        }.getOrElse { e ->
            // Surface the platform-unsupported case (wasmJs) as-is rather than masking it as a
            // generic encryption error.
            if (e is UnsupportedOperationException) throw e
            Err(internalError("ChaCha20-Poly1305 encrypt failed: ${e.message}", e))
        }

    private suspend fun doDecrypt(
        blob: EncryptedBlob,
        associatedData: ByteArray?,
    ): IdkResult<ByteArray, IdkError> =
        runCatching {
            if (blob.algorithm != EncryptedBlob.ALG_CHACHA20_POLY1305) {
                return Err(
                    internalError(
                        "Cannot decrypt blob with algorithm '${blob.algorithm}' — this service handles ChaCha20-Poly1305 only",
                    ),
                )
            }
            val key =
                keyProvider.findKey(blob.keyId) ?: return Err(
                    internalError("Unknown encryption key id '${blob.keyId}' — key was rotated out beyond the retention window"),
                )

            // Re-concatenate ciphertext || authTag for libsodium's combined-mode input shape.
            val combined = blob.ciphertext + blob.authTag
            val plaintext = aead.open(key = key.keyBytes, nonce = blob.iv, ciphertextAndTag = combined, associatedData = associatedData)
            Ok(plaintext)
        }.getOrElse { e ->
            if (e is UnsupportedOperationException) throw e
            Err(internalError("ChaCha20-Poly1305 decrypt failed (likely auth-tag mismatch or wrong AAD): ${e.message}", e))
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
        /** ChaCha20-Poly1305 nonce width — 96 bits / 12 bytes (RFC 8439). */
        private const val NONCE_BYTES: Int = 12

        /** Poly1305 authentication tag width — 128 bits / 16 bytes. */
        private const val TAG_BYTES: Int = 16
    }
}

/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.identity.matching.impl.protection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.command.GenerateMacArgs
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.ProtectedIdentifierValue
import com.sphereon.identity.matching.protection.IdentifierProtectionPolicy
import com.sphereon.identity.matching.protection.IdentifierProtector
import com.sphereon.identity.matching.protection.normalizeIdentifier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * KMS-backed [IdentifierProtector].
 *
 * Reuses the platform KMS for every cryptographic operation: blind indexing is a
 * domain-separated HMAC-SHA256 via [GenerateMacCommand] and reversible encryption is
 * AES-256-GCM via [KeyManagerService]. No new algorithms are introduced.
 *
 * Key references are tenant-scoped aliases:
 * - blind-index HMAC key alias  = "idfr:bi:<tenantId>"
 * - value encryption key alias  = "idfr:enc:<tenantId>"
 *
 * Both the blind-index message and the encryption AAD are canonicalized with RFC 8785 JCS
 * ([Jcs]) so the same logical input always yields identical bytes across calls and platforms.
 *
 * @param generateMacCommand KMS MAC command used for blind indexing.
 * @param keyManagerService KMS service used for AES-256-GCM encrypt/decrypt.
 * @param providerId KMS provider id (e.g. "software").
 * @param keyVersion Current key-version label recorded in the protected envelope.
 */
@OptIn(ExperimentalEncodingApi::class)
class KmsBackedIdentifierProtector(
    private val generateMacCommand: GenerateMacCommand,
    private val keyManagerService: KeyManagerService,
    private val providerId: String = "software",
    private val keyVersion: String = "v1",
) : IdentifierProtector {
    private companion object {
        const val BI_KEY_PREFIX = "idfr:bi:"
        const val ENC_KEY_PREFIX = "idfr:enc:"
        const val SCOPE_TENANT = "tenant"
        const val IV_LENGTH = 12
        const val TAG_LENGTH = 16
        const val PURPOSE_BI = "identifier-bi"
        const val PURPOSE_ENC = "identifier-enc"
    }

    override suspend fun protect(
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
        plaintext: String,
        policy: IdentifierProtectionPolicy,
        salt: ByteArray?,
    ): IdkResult<ProtectedIdentifierValue, IdkError> {
        val normalized = normalizeIdentifier(plaintext, policy.normalization)

        return when (policy.mode) {
            IdentifierProtectionMode.PLAINTEXT -> {
                Ok(
                    ProtectedIdentifierValue(
                        mode = IdentifierProtectionMode.PLAINTEXT,
                        plaintext = normalized,
                    ),
                )
            }

            IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX -> {
                protectBlinded(
                    tenantId = tenantId,
                    identityId = identityId,
                    type = type,
                    normalized = normalized,
                    mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
                    scope = SCOPE_TENANT,
                )
            }

            IdentifierProtectionMode.SALTED_BLINDED -> {
                val saltBytes =
                    salt
                        ?: return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "SALTED_BLINDED protection requires a non-null salt",
                            ),
                        )
                protectBlinded(
                    tenantId = tenantId,
                    identityId = identityId,
                    type = type,
                    normalized = normalized,
                    mode = IdentifierProtectionMode.SALTED_BLINDED,
                    scope = Base64.encode(saltBytes),
                )
            }
        }
    }

    private suspend fun protectBlinded(
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
        normalized: String,
        mode: IdentifierProtectionMode,
        scope: String,
    ): IdkResult<ProtectedIdentifierValue, IdkError> {
        val hmac =
            computeBlindIndex(tenantId, type, normalized, scope)
                .let { it.getOrNull() ?: return Err(it.errorOrNull() ?: unknown("blind index failed")) }

        val ciphertext =
            encrypt(tenantId, identityId, type, normalized)
                .let { it.getOrNull() ?: return Err(it.errorOrNull() ?: unknown("encryption failed")) }

        return Ok(
            ProtectedIdentifierValue(
                mode = mode,
                valueCiphertext = ciphertext,
                encKeyRef = ENC_KEY_PREFIX + tenantId,
                encKeyVersion = keyVersion,
                valueHmac = hmac,
                hmacKeyRef = BI_KEY_PREFIX + tenantId,
                hmacKeyVersion = keyVersion,
            ),
        )
    }

    override suspend fun blindIndex(
        tenantId: String,
        type: IdentifierType,
        plaintext: String,
        policy: IdentifierProtectionPolicy,
        salt: ByteArray?,
    ): IdkResult<String, IdkError> {
        val normalized = normalizeIdentifier(plaintext, policy.normalization)
        val scope =
            when (policy.mode) {
                IdentifierProtectionMode.SALTED_BLINDED -> {
                    val saltBytes =
                        salt
                            ?: return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "SALTED_BLINDED blind index requires a non-null salt",
                                ),
                            )
                    Base64.encode(saltBytes)
                }

                else -> {
                    SCOPE_TENANT
                }
            }
        return computeBlindIndex(tenantId, type, normalized, scope)
    }

    /**
     * Ensure the tenant's blind-index HMAC key exists before first use. Per-tenant identifier keys
     * are provisioned lazily so any tenant (including the platform tenant during bootstrap) can
     * derive blind indexes without a separate provisioning step. Idempotent and concurrency-tolerant:
     * a key created by a racing caller is treated as success.
     */
    private suspend fun ensureBlindIndexKey(tenantId: String): IdkResult<Unit, IdkError> =
        ensureTenantKey(BI_KEY_PREFIX + tenantId, "blind-index", tenantId) { alias ->
            keyManagerService.generateKeyResult(
                providerId = providerId,
                alias = alias,
                alg = SignatureAlgorithm.HMAC_SHA256,
            )
        }

    /**
     * Resolve-or-provision a per-tenant identifier key under [alias]. Idempotent and
     * concurrency-tolerant: a key created by a racing caller is treated as success.
     */
    private suspend fun ensureTenantKey(
        alias: String,
        label: String,
        tenantId: String,
        generate: suspend (alias: String) -> IdkResult<*, IdkError>,
    ): IdkResult<Unit, IdkError> {
        val keyInfo = KeyInfo<Nothing>(alias = alias, providerId = providerId)
        if (keyManagerService.getKeyResult(keyInfo).isOk) return Ok(Unit)
        val generated = generate(alias)
        return if (generated.isOk || keyManagerService.getKeyResult(keyInfo).isOk) {
            Ok(Unit)
        } else {
            Err(generated.errorOrNull() ?: unknown("$label key provisioning failed for tenant $tenantId"))
        }
    }

    /**
     * Deterministic, domain-separated blind index over the normalized value. The HMAC message is
     * the JCS canonicalization of a fixed-shape object, so the same inputs always hash identically.
     */
    private suspend fun computeBlindIndex(
        tenantId: String,
        type: IdentifierType,
        normalized: String,
        scope: String,
    ): IdkResult<String, IdkError> {
        ensureBlindIndexKey(tenantId).getOrElse { return Err(it) }

        val message =
            Jcs.canonicalize(
                JsonObject(
                    mapOf(
                        "purpose" to JsonPrimitive(PURPOSE_BI),
                        "tenant_id" to JsonPrimitive(tenantId),
                        "type" to JsonPrimitive(type.value),
                        "scope" to JsonPrimitive(scope),
                        "value" to JsonPrimitive(normalized),
                    ),
                ),
            )

        val result =
            generateMacCommand.execute(
                GenerateMacArgs(
                    keyId = BI_KEY_PREFIX + tenantId,
                    message = message,
                    digestAlgorithm = DigestAlg.SHA256,
                    providerId = providerId,
                ),
            )
        val macResult = result.getOrNull() ?: return Err(result.errorOrNull() ?: unknown("MAC generation failed"))
        return Ok(macResult.macMultibase)
    }

    /**
     * Ensure the tenant's AES-256 value-encryption key exists before first use. Like the
     * blind-index key, per-tenant identifier keys are provisioned lazily so any tenant
     * (including the platform tenant during bootstrap) can protect identifiers without a
     * separate provisioning step. Generating with `use=enc` and no signature algorithm mints
     * a 256-bit symmetric AES key on the software provider (the natural AES-GCM shape).
     * Idempotent and concurrency-tolerant: a key created by a racing caller is treated as success.
     */
    private suspend fun ensureEncryptionKey(tenantId: String): IdkResult<Unit, IdkError> =
        ensureTenantKey(ENC_KEY_PREFIX + tenantId, "encryption", tenantId) { alias ->
            keyManagerService.generateKeyResult(
                providerId = providerId,
                alias = alias,
                use = JwkUse.enc,
            )
        }

    /**
     * AES-256-GCM encrypt the normalized value, binding it to the tenant/identity/type via AAD.
     * Stores IV(12) | authTag(16) | ciphertext as a single Base64Url blob.
     */
    private suspend fun encrypt(
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
        normalized: String,
    ): IdkResult<String, IdkError> {
        ensureEncryptionKey(tenantId).getOrElse { return Err(it) }

        val keyInfo = KeyInfo<Nothing>(alias = ENC_KEY_PREFIX + tenantId, providerId = providerId)
        val aad = encryptionAad(tenantId, identityId, type)

        val result =
            keyManagerService.encryptResult(
                keyInfo = keyInfo,
                plaintext = normalized.encodeToByteArray(),
                algorithm = ContentEncryptionAlgorithm.A256GCM,
                additionalAuthenticatedData = aad,
            )
        val encryptResult = result.getOrNull() ?: return Err(result.errorOrNull() ?: unknown("encryption failed"))

        val combined = encryptResult.iv + encryptResult.authTag + encryptResult.ciphertext
        return Ok(Base64.UrlSafe.encode(combined))
    }

    override suspend fun reveal(
        protected: ProtectedIdentifierValue,
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
    ): IdkResult<String, IdkError> {
        val blob =
            protected.valueCiphertext
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Protected value carries no ciphertext to reveal (mode=${protected.mode})",
                    ),
                )

        val combined = Base64.UrlSafe.decode(blob)
        if (combined.size < IV_LENGTH + TAG_LENGTH) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid ciphertext blob: too short"))
        }
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val authTag = combined.copyOfRange(IV_LENGTH, IV_LENGTH + TAG_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH + TAG_LENGTH, combined.size)

        val keyInfo = KeyInfo<Nothing>(alias = ENC_KEY_PREFIX + tenantId, providerId = providerId)
        val aad = encryptionAad(tenantId, identityId, type)

        val result =
            keyManagerService.decryptResult(
                keyInfo = keyInfo,
                ciphertext = ciphertext,
                algorithm = ContentEncryptionAlgorithm.A256GCM,
                iv = iv,
                authTag = authTag,
                additionalAuthenticatedData = aad,
            )
        val decryptResult = result.getOrNull() ?: return Err(result.errorOrNull() ?: unknown("decryption failed"))
        return Ok(decryptResult.plaintext.decodeToString())
    }

    /** Deterministic AAD bound to tenant/identity/type, canonicalized via JCS for stability. */
    private fun encryptionAad(
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
    ): ByteArray =
        Jcs.canonicalize(
            JsonObject(
                mapOf(
                    "v" to JsonPrimitive(1),
                    "tenant_id" to JsonPrimitive(tenantId),
                    "purpose" to JsonPrimitive(PURPOSE_ENC),
                    "identity_id" to JsonPrimitive(identityId ?: ""),
                    "type" to JsonPrimitive(type.value),
                ),
            ),
        )

    private fun unknown(message: String): IdkError = IdkError.UNKNOWN_ERROR(message = message)
}

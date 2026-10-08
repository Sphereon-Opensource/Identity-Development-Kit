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
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.command.DecryptArgs
import com.sphereon.crypto.core.kms.command.DecryptCommand
import com.sphereon.crypto.core.kms.command.EncryptArgs
import com.sphereon.crypto.core.kms.command.EncryptCommand
import com.sphereon.crypto.core.kms.command.GenerateKeyArgs
import com.sphereon.crypto.core.kms.command.GenerateKeyCommand
import com.sphereon.crypto.core.kms.command.GenerateMacArgs
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.crypto.core.kms.command.ListKeysArgs
import com.sphereon.crypto.core.kms.command.ListKeysCommand
import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.ProtectedIdentifierValue
import com.sphereon.identity.matching.protection.IdentifierProtectionPolicy
import com.sphereon.identity.matching.protection.IdentifierProtector
import com.sphereon.identity.matching.protection.normalizeIdentifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * KMS-backed [IdentifierProtector].
 *
 * Reuses the platform KMS for every cryptographic operation: blind indexing is a
 * domain-separated HMAC-SHA256 via [GenerateMacCommand] and reversible encryption is
 * AES-256-GCM via [EncryptCommand] and [DecryptCommand]. No new algorithms are introduced.
 *
 * Key references are tenant-scoped aliases:
 * - blind-index HMAC key alias  = "idfr:bi:<tenantId>"
 * - value encryption key alias  = "idfr:enc:<tenantId>"
 *
 * Both the blind-index message and the encryption AAD are canonicalized with RFC 8785 JCS
 * ([Jcs]) so the same logical input always yields identical bytes across calls and platforms.
 *
 * All key lifecycle and cryptographic work uses commands, not the process-local KMS service. This
 * keeps resolve, generate, MAC, encryption, and decryption on the same routed KMS owner in split
 * deployments. Key discovery uses metadata-only references and never returns private key material.
 *
 * @param providerId KMS provider id; null lets the KMS search its providers for the one
 *   holding (or able to hold) the key, so the same protector works whether keys live on a
 *   local software provider or a routed tenant KMS with per-tenant provider ids.
 * @param keyVersion Current key-version label recorded in the protected envelope.
 */
class KmsBackedIdentifierProtector(
    private val generateKeyCommand: GenerateKeyCommand,
    private val listKeysCommand: ListKeysCommand,
    private val generateMacCommand: GenerateMacCommand,
    private val encryptCommand: EncryptCommand,
    private val decryptCommand: DecryptCommand,
    private val providerId: String? = null,
    private val keyVersion: String = "v1",
) : IdentifierProtector {
    private val keyReferenceCacheMutex = Mutex()
    private val keyReferenceCache = mutableMapOf<TenantKeyCacheKey, ManagedKeyReference>()

    private companion object {
        const val BI_KEY_PREFIX = "idfr:bi:"
        const val ENC_KEY_PREFIX = "idfr:enc:"
        const val SCOPE_TENANT = "tenant"
        const val IV_LENGTH = 12
        const val TAG_LENGTH = 16
        const val PURPOSE_BI = "identifier-bi"
        const val PURPOSE_ENC = "identifier-enc"

        /**
         * Coordinates lazy identifier-key creation across protector instances in this process.
         *
         * Protector instances are session-scoped, while their KMS aliases are tenant-scoped. Without
         * process-wide coordination, two first-use sessions can both observe a missing alias and each
         * generate a different key. Software PKCS12 stores allow alias replacement by default, so the
         * second write can make blind indexes or ciphertext created with the first key unreadable after
         * the in-process key cache is lost on restart.
         */
        // Blind-index and encryption keys are independent aliases. Keep their first-use
        // provisioning separate so a searchable identifier can create both keys in parallel.
        // Each lock remains process-wide, preserving the no-replacement guarantee for its
        // corresponding alias family across session-scoped protector instances.
        val blindIndexKeyProvisioningMutex = Mutex()
        val encryptionKeyProvisioningMutex = Mutex()
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

            IdentifierProtectionMode.SEARCHABLE_ENCRYPTED,
            IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX -> {
                protectBlinded(
                    tenantId = tenantId,
                    identityId = identityId,
                    type = type,
                    normalized = normalized,
                    mode = policy.mode,
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
                    scope = saltBytes.encodeToBase64(),
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
        // Validate both selected aliases before either branch is allowed to create a key. This
        // keeps an invalid/ambiguous blind-index lookup from causing an unrelated encryption-key
        // mutation, while still allowing valid independent provisioning to overlap below.
        val preflight = preflightSearchableKeys(tenantId).getOrElse { return Err(it) }
        // These operations use distinct tenant keys and have no data dependency. Running them
        // together removes one full routed KMS leg from the cold searchable-identifier path.
        // Both results are awaited before returning, so a failure remains fail-closed.
        val (blindIndexResult, encryptionResult) =
            coroutineScope {
                val blindIndex = async { computeBlindIndex(tenantId, type, normalized, scope, preflight[BI_KEY_PREFIX + tenantId]) }
                val encryption = async { encrypt(tenantId, identityId, type, normalized, preflight[ENC_KEY_PREFIX + tenantId]) }
                blindIndex.await() to encryption.await()
            }
        val hmac = blindIndexResult.getOrNull() ?: return Err(blindIndexResult.errorOrNull() ?: unknown("blind index failed"))
        val ciphertext = encryptionResult.getOrNull() ?: return Err(encryptionResult.errorOrNull() ?: unknown("encryption failed"))

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

    private suspend fun preflightSearchableKeys(tenantId: String): IdkResult<Map<String, TenantKeyLookup>, IdkError> {
        val aliases =
            listOf(
                BI_KEY_PREFIX + tenantId to "blind-index",
                ENC_KEY_PREFIX + tenantId to "encryption",
            )
        val unresolved = aliases.filter { (alias, _) ->
            cachedKeyReference(TenantKeyCacheKey(tenantId, providerId, alias)) == null
        }
        if (unresolved.isEmpty()) return Ok(emptyMap())

        val outcomes =
            coroutineScope {
                unresolved.map { (alias, label) ->
                    async { Triple(alias, label, lookupTenantKey(alias, label, tenantId)) }
                }.map { it.await() }
            }
        val resolved = mutableMapOf<String, TenantKeyLookup>()
        for ((alias, _, result) in outcomes) {
            val outcome = result.getOrNull() ?: return Err(result.errorOrNull() ?: unknown("identifier key preflight failed"))
            resolved[alias] = outcome
            if (outcome is TenantKeyLookup.Found) {
                cacheKeyReference(TenantKeyCacheKey(tenantId, providerId, alias), outcome.keyInfo)
            }
        }
        return Ok(resolved)
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
                    saltBytes.encodeToBase64()
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
    private suspend fun ensureBlindIndexKey(
        tenantId: String,
        knownLookup: TenantKeyLookup? = null,
    ): IdkResult<ManagedKeyReference, IdkError> =
        ensureTenantKey(BI_KEY_PREFIX + tenantId, "blind-index", tenantId, blindIndexKeyProvisioningMutex, knownLookup) { alias ->
            GenerateKeyArgs(
                providerId = providerId,
                alias = alias,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.HMAC_SHA256,
                keyVisibility = KeyVisibility.PRIVATE,
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
        provisioningMutex: Mutex,
        knownLookup: TenantKeyLookup? = null,
        generate: (alias: String) -> GenerateKeyArgs,
    ): IdkResult<ManagedKeyReference, IdkError> {
        val cacheKey = TenantKeyCacheKey(tenantId = tenantId, providerId = providerId, alias = alias)
        cachedKeyReference(cacheKey)?.let { return Ok(it) }

        return provisioningMutex.withLock {
            cachedKeyReference(cacheKey)?.let { return@withLock Ok(it) }

            val lookup = knownLookup?.let(::Ok) ?: lookupTenantKey(alias, label, tenantId)
            if (lookup.isErr) return@withLock Err(lookup.error)
            when (val outcome = lookup.value) {
                is TenantKeyLookup.Found -> {
                    cacheKeyReference(cacheKey, outcome.keyInfo)
                    return@withLock Ok(outcome.keyInfo)
                }

                TenantKeyLookup.Missing -> Unit
            }

            val generated = generateKeyCommand.execute(generate(alias))
            if (generated.isOk) {
                val reference = generated.value.keyReference
                    ?: return@withLock Err(unknown("$label key generation returned no metadata receipt for tenant $tenantId"))
                val validated = validateGeneratedKeyReference(reference, alias, label, tenantId)
                    .getOrElse { return@withLock Err(it) }
                cacheKeyReference(cacheKey, validated)
                return@withLock Ok(validated)
            }

            // A different process may have created the same tenant alias after our exact lookup.
            // Reconcile that one explicit conflict case with one exact re-read; unrelated command
            // failures remain the authoritative error and never trigger provider/default guessing.
            val reconciliation = lookupTenantKey(alias, label, tenantId)
            val found = reconciliation.getOrNull() as? TenantKeyLookup.Found
            if (found != null) {
                cacheKeyReference(cacheKey, found.keyInfo)
                Ok(found.keyInfo)
            } else {
                Err(generated.error)
            }
        }
    }

    /**
     * Resolve [alias] to the provider that actually owns the key. The request carries the exact
     * alias to the owning KMS so persistent stores can select one metadata row rather than return
     * the full tenant key catalog. No symmetric key material crosses the command boundary.
     */
    private suspend fun resolveTenantKey(
        alias: String,
        label: String,
        tenantId: String,
    ): IdkResult<ManagedKeyReference, IdkError> {
        val cacheKey = TenantKeyCacheKey(tenantId = tenantId, providerId = providerId, alias = alias)
        cachedKeyReference(cacheKey)?.let { return Ok(it) }
        val lookup = lookupTenantKey(alias, label, tenantId)
        if (lookup.isErr) return Err(lookup.error)
        return when (val outcome = lookup.value) {
            is TenantKeyLookup.Found -> {
                cacheKeyReference(cacheKey, outcome.keyInfo)
                Ok(outcome.keyInfo)
            }

            TenantKeyLookup.Missing -> Err(unknown("$label key resolution failed for tenant $tenantId"))
        }
    }

    private suspend fun lookupTenantKey(
        alias: String,
        label: String,
        tenantId: String,
    ): IdkResult<TenantKeyLookup, IdkError> {
        val lookup = listKeysCommand.execute(ListKeysArgs(providerId = providerId, alias = alias))
        val references =
            lookup.getOrNull()?.keys
                ?: return Err(
                    lookup.errorOrNull()
                        ?: unknown("$label key resolution failed for tenant $tenantId"),
                )
        if (references.any { reference -> reference.alias != alias || (providerId != null && reference.providerId != providerId) }) {
            return Err(unknown("$label exact key resolution returned an unrelated reference for tenant $tenantId"))
        }
        if (references.isEmpty()) return Ok(TenantKeyLookup.Missing)
        val resolved = references.singleOrNull()
            ?: return Err(unknown("$label key provider resolution was ambiguous for tenant $tenantId"))
        if (resolved.providerId.isBlank()) {
            return Err(unknown("$label key provider resolution failed for tenant $tenantId"))
        }
        return Ok(TenantKeyLookup.Found(resolved))
    }

    private fun validateGeneratedKeyReference(
        reference: ManagedKeyReference,
        alias: String,
        label: String,
        tenantId: String,
    ): IdkResult<ManagedKeyReference, IdkError> {
        if (reference.alias != alias) {
            return Err(unknown("$label key generation returned an unexpected alias for tenant $tenantId"))
        }
        if (reference.providerId.isNullOrBlank() || (providerId != null && reference.providerId != providerId)) {
            return Err(unknown("$label key generation returned an unexpected provider for tenant $tenantId"))
        }
        return Ok(reference)
    }

    private suspend fun cachedKeyReference(key: TenantKeyCacheKey): ManagedKeyReference? =
        keyReferenceCacheMutex.withLock { keyReferenceCache[key] }

    private suspend fun cacheKeyReference(
        key: TenantKeyCacheKey,
        reference: ManagedKeyReference,
    ) {
        keyReferenceCacheMutex.withLock { keyReferenceCache[key] = reference }
    }

    private data class TenantKeyCacheKey(
        val tenantId: String,
        val providerId: String?,
        val alias: String,
    )

    private sealed interface TenantKeyLookup {
        data class Found(val keyInfo: ManagedKeyReference) : TenantKeyLookup
        data object Missing : TenantKeyLookup
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
        knownLookup: TenantKeyLookup? = null,
    ): IdkResult<String, IdkError> {
        val keyAlias = BI_KEY_PREFIX + tenantId
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

        val resolvedKeyInfo = ensureBlindIndexKey(tenantId, knownLookup).getOrElse { return Err(it) }
        // A null providerId means that the managed key store may search across multiple
        // configured providers. Carry the provider discovered by the successful key lookup into
        // the local MAC call so an upgraded multi-provider registry cannot select a new default.
        val resolvedProviderId = requireNotNull(resolvedKeyInfo.providerId)
        return generateBlindIndexMac(keyAlias, message, resolvedProviderId)
    }

    private suspend fun generateBlindIndexMac(
        keyAlias: String,
        message: ByteArray,
        providerId: String?,
    ): IdkResult<String, IdkError> {
        val result =
            generateMacCommand.execute(
                GenerateMacArgs(
                    keyId = keyAlias,
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
     * separate provisioning step. AES has no [SignatureAlgorithm], so the encryption key keeps
     * `alg` unset and is constrained by `use=enc` plus the encrypt/decrypt key operations. The
     * software provider then mints the generic 256-bit oct key required by AES-GCM.
     * Idempotent and concurrency-tolerant: a key created by a racing caller is treated as success.
     */
    private suspend fun ensureEncryptionKey(
        tenantId: String,
        knownLookup: TenantKeyLookup? = null,
    ): IdkResult<ManagedKeyReference, IdkError> =
        ensureTenantKey(ENC_KEY_PREFIX + tenantId, "encryption", tenantId, encryptionKeyProvisioningMutex, knownLookup) { alias ->
            GenerateKeyArgs(
                providerId = providerId,
                alias = alias,
                use = JwkUse.enc,
                keyOperations = arrayOf(KeyOperations.ENCRYPT, KeyOperations.DECRYPT),
                keyVisibility = KeyVisibility.PRIVATE,
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
        knownLookup: TenantKeyLookup? = null,
    ): IdkResult<String, IdkError> {
        val keyInfo = ensureEncryptionKey(tenantId, knownLookup).getOrElse { return Err(it) }
        val aad = encryptionAad(tenantId, identityId, type)

        val result =
            encryptCommand.execute(
                EncryptArgs(
                    keyInfo = keyInfo,
                    plaintext = normalized.encodeToByteArray(),
                    algorithm = ContentEncryptionAlgorithm.A256GCM,
                    additionalAuthenticatedData = aad,
                ),
            )
        val encryptResult = result.getOrNull() ?: return Err(result.errorOrNull() ?: unknown("encryption failed"))

        val combined = encryptResult.iv + encryptResult.authTag + encryptResult.ciphertext
        return Ok(combined.encodeToBase64Url())
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

        val combined = blob.decodeFromBase64Url()
        if (combined.size < IV_LENGTH + TAG_LENGTH) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid ciphertext blob: too short"))
        }
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val authTag = combined.copyOfRange(IV_LENGTH, IV_LENGTH + TAG_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH + TAG_LENGTH, combined.size)

        val keyInfo =
            resolveTenantKey(
                alias = ENC_KEY_PREFIX + tenantId,
                label = "encryption",
                tenantId = tenantId,
            ).getOrElse { return Err(it) }
        val aad = encryptionAad(tenantId, identityId, type)

        val result =
            decryptCommand.execute(
                DecryptArgs(
                    keyInfo = keyInfo,
                    ciphertext = ciphertext,
                    algorithm = ContentEncryptionAlgorithm.A256GCM,
                    iv = iv,
                    authTag = authTag,
                    additionalAuthenticatedData = aad,
                ),
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

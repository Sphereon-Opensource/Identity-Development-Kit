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
package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.jarm.JarmMode
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.store.StoreMetadata
import com.sphereon.openid.oid4vp.common.store.StoredEntry
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.StoredAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeError
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStore
import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStoreResult
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private const val MILLIS_PER_SECOND = 1000

/**
 * KV-backed implementation of [ResponseCodeStore].
 *
 * This implementation persists a minimal, serializable envelope in [KvStore] and reconstructs
 * [ParsedAuthorizationResponse] on retrieval from the stored raw `vp_token`.
 *
 * Notes:
 * - Only a subset of the response is stored (raw vp_token + optional state + optional JARM hints).
 * - Consuming a response_code is guarded with an in-process mutex. This guarantees single-use
 *   semantics within a process. For distributed backends, single-use must be enforced by the KV
 *   backend via atomic operations (planned extension).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResponseCodeStore>())
class KvResponseCodeStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
    private val clock: Clock,
) : ResponseCodeStore {
    private val json = Json
    private val mutex = Mutex()

    private val namespace =
        KvNamespace(
            name = "oid4vp.response_code",
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = ResponseCodeEntry.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vp.response_code",
            scopeBinding = KvStoreScopeBinding.TENANT,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(resolveEffectiveStoreConfig(), execution)
    }

    /**
     * Resolve KV store configuration from the config abstraction (if present), otherwise fall back to [storeConfig].
     *
     * This store is tenant-bound by design; configuration is validated to prevent scope drift.
     */
    private fun resolveEffectiveStoreConfig(): KvStoreConfigBase {
        val configured = runCatching { kvStoreService.getStoreConfig(storeConfig.id) }.getOrNull()
        val effective = configured ?: storeConfig
        require(effective.scopeBinding == storeConfig.scopeBinding) {
            "KV store '${storeConfig.id}' must use scopeBinding=${storeConfig.scopeBinding}, but was ${effective.scopeBinding}"
        }
        return effective
    }

    @Serializable
    internal data class ResponseCodeEntry(
        val rawVpToken: String,
        val state: String? = null,
        val jarmMode: String? = null,
        val jarmIssuer: String? = null,
        val createdAt: Long,
        val expiresAt: Long,
        val used: Boolean = false,
    )

    override suspend fun createResponseCode(
        parsedResponse: ParsedAuthorizationResponse,
        validationResult: ValidationResult?,
        state: String?,
        ttlSeconds: Long,
    ): IdkResult<ResponseCodeStoreResult, IdkError> {
        // Note: validationResult is intentionally not persisted here.
        val responseCode = generateSecureResponseCode()
        val now = clock.now().toEpochMilliseconds()
        val expiresAt = now + (ttlSeconds * MILLIS_PER_SECOND)

        val entry =
            ResponseCodeEntry(
                rawVpToken = parsedResponse.rawVpToken,
                state = state,
                jarmMode = parsedResponse.jarmMode?.name,
                jarmIssuer = parsedResponse.jarmIssuer,
                createdAt = now,
                expiresAt = expiresAt,
                used = false,
            )

        return kv
            .put(namespace, responseCode, entry, ttlSeconds.seconds)
            .map {
                ResponseCodeStoreResult(responseCode = responseCode, expiresAt = expiresAt)
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to store response_code: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = ResponseCodeError.STORAGE_ERROR,
                )
            }
    }

    override suspend fun put(
        key: String,
        value: StoredAuthorizationResponse,
        ttlSeconds: Long,
    ): IdkResult<StoreMetadata, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val expiresAt = now + (ttlSeconds * MILLIS_PER_SECOND)

        val entry =
            ResponseCodeEntry(
                rawVpToken = value.parsedResponse.rawVpToken,
                state = value.state,
                jarmMode = value.parsedResponse.jarmMode?.name,
                jarmIssuer = value.parsedResponse.jarmIssuer,
                createdAt = now,
                expiresAt = expiresAt,
                used = value.used,
            )

        return kv
            .put(namespace, key, entry, ttlSeconds.seconds)
            .map {
                StoreMetadata(createdAt = entry.createdAt, expiresAt = entry.expiresAt)
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to put response_code entry: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = ResponseCodeError.STORAGE_ERROR,
                )
            }
    }

    override suspend fun get(key: String): IdkResult<StoredAuthorizationResponse?, IdkError> =
        kv
            .get(namespace, key)
            .map { entry ->
                entry?.toStoredAuthorizationResponse(key)
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to get response_code entry: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = ResponseCodeError.STORAGE_ERROR,
                )
            }

    override suspend fun getEntry(key: String): IdkResult<StoredEntry<StoredAuthorizationResponse>?, IdkError> =
        kv
            .get(namespace, key)
            .map { entry ->
                entry?.let {
                    StoredEntry(
                        value = it.toStoredAuthorizationResponse(key),
                        createdAt = it.createdAt,
                        expiresAt = it.expiresAt,
                    )
                }
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to get response_code entry metadata: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = ResponseCodeError.STORAGE_ERROR,
                )
            }

    override suspend fun getAndConsume(
        key: String,
        consume: Boolean,
    ): IdkResult<StoredAuthorizationResponse, IdkError> {
        return mutex.withLock {
            val now = clock.now().toEpochMilliseconds()

            val entry =
                kv.get(namespace, key).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to retrieve response_code entry: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = ResponseCodeError.STORAGE_ERROR,
                        ),
                    )
                } ?: return@withLock Err(IdkError.fromString(message = "Response code not found", code = ResponseCodeError.INVALID_RESPONSE_CODE))

            if (now > entry.expiresAt) {
                // Best-effort cleanup; TTL should also remove it.
                kv.delete(namespace, key)
                return@withLock Err(IdkError.fromString(message = "Response code has expired", code = ResponseCodeError.EXPIRED_RESPONSE_CODE))
            }

            if (entry.used) {
                return@withLock Err(IdkError.fromString(message = "Response code has already been used", code = ResponseCodeError.USED_RESPONSE_CODE))
            }

            val updatedEntry =
                if (consume) {
                    entry.copy(used = true)
                } else {
                    entry
                }
            if (consume) {
                val ttlSecondsRemaining = ((updatedEntry.expiresAt - now).coerceAtLeast(0L) / 1000L).coerceAtLeast(1L)
                kv.put(namespace, key, updatedEntry, ttlSecondsRemaining.seconds).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to mark response_code as used: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = ResponseCodeError.STORAGE_ERROR,
                        ),
                    )
                }
            }

            Ok(updatedEntry.toStoredAuthorizationResponse(key))
        }
    }

    override suspend fun isConsumed(key: String): IdkResult<Boolean, IdkError> =
        kv.get(namespace, key).map { it?.used ?: false }.mapError { e ->
            IdkError.fromString(
                message = "Failed to check response_code consumption: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = ResponseCodeError.STORAGE_ERROR,
            )
        }

    override suspend fun markConsumed(key: String): IdkResult<Unit, IdkError> {
        return mutex.withLock {
            val now = clock.now().toEpochMilliseconds()
            val entry =
                kv.get(namespace, key).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to get response_code entry: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = ResponseCodeError.STORAGE_ERROR,
                        ),
                    )
                } ?: return@withLock Err(IdkError.fromString(message = "Response code not found", code = ResponseCodeError.INVALID_RESPONSE_CODE))

            if (now > entry.expiresAt) {
                kv.delete(namespace, key)
                return@withLock Err(IdkError.fromString(message = "Response code has expired", code = ResponseCodeError.EXPIRED_RESPONSE_CODE))
            }

            val ttlSecondsRemaining = ((entry.expiresAt - now).coerceAtLeast(0L) / 1000L).coerceAtLeast(1L)
            kv.put(namespace, key, entry.copy(used = true), ttlSecondsRemaining.seconds).getOrElse { e ->
                return@withLock Err(
                    IdkError.fromString(
                        message = "Failed to mark response_code as used: ${e.message}",
                        exception = IllegalStateException(e.toString()),
                        code = ResponseCodeError.STORAGE_ERROR,
                    ),
                )
            }
            Ok(Unit)
        }
    }

    override suspend fun isValid(key: String): IdkResult<Boolean, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        return kv
            .get(namespace, key)
            .map { entry ->
                entry != null && !entry.used && now <= entry.expiresAt
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to validate response_code: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = ResponseCodeError.STORAGE_ERROR,
                )
            }
    }

    override suspend fun delete(key: String): IdkResult<Boolean, IdkError> =
        kv.delete(namespace, key).mapError { e ->
            IdkError.fromString(
                message = "Failed to delete response_code: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = ResponseCodeError.STORAGE_ERROR,
            )
        }

    override suspend fun exists(key: String): IdkResult<Boolean, IdkError> =
        kv.exists(namespace, key).mapError { e ->
            IdkError.fromString(
                message = "Failed to check response_code existence: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = ResponseCodeError.STORAGE_ERROR,
            )
        }

    override suspend fun touch(
        key: String,
        ttlSeconds: Long,
    ): IdkResult<Boolean, IdkError> {
        return mutex.withLock {
            val now = clock.now().toEpochMilliseconds()
            val entry =
                kv.get(namespace, key).getOrElse { e ->
                    return@withLock Err(
                        IdkError.fromString(
                            message = "Failed to touch response_code: ${e.message}",
                            exception = IllegalStateException(e.toString()),
                            code = ResponseCodeError.STORAGE_ERROR,
                        ),
                    )
                } ?: return@withLock Ok(false)

            if (now > entry.expiresAt) {
                kv.delete(namespace, key)
                return@withLock Ok(false)
            }

            val updated = entry.copy(expiresAt = now + (ttlSeconds * MILLIS_PER_SECOND))
            kv.put(namespace, key, updated, ttlSeconds.seconds).getOrElse { e ->
                return@withLock Err(
                    IdkError.fromString(
                        message = "Failed to touch response_code: ${e.message}",
                        exception = IllegalStateException(e.toString()),
                        code = ResponseCodeError.STORAGE_ERROR,
                    ),
                )
            }
            Ok(true)
        }
    }

    override suspend fun cleanupExpired(): IdkResult<Int, IdkError> =
        kv.cleanupExpired(namespace).mapError { e ->
            IdkError.fromString(
                message = "Failed to cleanup expired response_code entries: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = ResponseCodeError.STORAGE_ERROR,
            )
        }

    private fun ResponseCodeEntry.toStoredAuthorizationResponse(responseCode: String): StoredAuthorizationResponse {
        val vpToken =
            parseVpToken(rawVpToken)
                ?: throw IllegalArgumentException("Invalid vp_token format")

        val jarmModeEnum: JarmMode? =
            jarmMode?.let {
                try {
                    enumValueOf<JarmMode>(it)
                } catch (_: Exception) {
                    // Ignored: unrecognized JARM mode value
                    null
                }
            }

        val parsed =
            ParsedAuthorizationResponse(
                vpToken = vpToken,
                state = state,
                rawVpToken = rawVpToken,
                jarmMode = jarmModeEnum,
                jarmIssuer = jarmIssuer,
            )

        return StoredAuthorizationResponse(
            responseCode = responseCode,
            parsedResponse = parsed,
            validationResult = null,
            state = state,
            createdAt = createdAt,
            expiresAt = expiresAt,
            used = used,
        )
    }

    private fun parseVpToken(vpTokenRaw: String): VpToken? {
        return try {
            val trimmed = vpTokenRaw.trim()
            if (!trimmed.startsWith("{")) {
                return null
            }
            val jsonElement = Json.parseToJsonElement(trimmed)
            VpToken.fromJson(jsonElement)
        } catch (_: Exception) {
            // Ignored: vp_token is not valid JSON
            null
        }
    }

    /**
     * Generate a cryptographically secure response code.
     *
     * The response code is:
     * - 32 bytes of random data
     * - Base64-URL encoded (without padding)
     * - Approximately 43 characters long
     */
    private fun generateSecureResponseCode(): String {
        val bytes = CryptographyRandom.nextBytes(RESPONSE_CODE_BYTE_LENGTH)
        return bytes.encodeToBase64Url()
    }

    private companion object {
        private const val RESPONSE_CODE_BYTE_LENGTH = 32
    }
}

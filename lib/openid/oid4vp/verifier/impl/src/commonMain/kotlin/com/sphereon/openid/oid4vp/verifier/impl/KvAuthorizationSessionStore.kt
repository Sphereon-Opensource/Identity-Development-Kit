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
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationRequest
import com.sphereon.openid.oid4vp.common.store.StoreMetadata
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.verifier.MatchedCredential
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionCallbackDispatcher
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionStatusUpdate
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCallbackConfig
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCreateArgs
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionError
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private const val MILLIS_PER_SECOND = 1000
private const val RANDOM_BYTES_SIZE = 16

/**
 * KV-backed implementation of [AuthorizationSessionStore].
 *
 * Stored value is an internal, serializable envelope; public models are reconstructed on read.
 * TTL is enforced by the underlying KV store and mirrored in the stored `expiresAt`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AuthorizationSessionStore>())
class KvAuthorizationSessionStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val dcqlQueryConfigurationStore: DcqlQueryConfigurationStore,
    private val callbackDispatcher: AuthorizationSessionCallbackDispatcher,
    appLogManager: AppLogManager,
    private val execution: SessionExecution,
    private val clock: Clock,
) : AuthorizationSessionStore {
    private val log = appLogManager.withTag("Oid4vpAuthorizationSessionStore")
    private val json = Json

    private val namespace =
        KvNamespace(
            name = "oid4vp.authorization_session",
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = AuthorizationSessionEntry.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vp.authorization_session",
            scopeBinding = KvStoreScopeBinding.TENANT,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(resolveEffectiveStoreConfig(), execution)
    }

    /**
     * Resolve KV store configuration from the config abstraction (if present), otherwise fall back to [storeConfig].
     *
     * Important: this store requires [KvStoreScopeBinding.TENANT] semantics; configuration is validated to prevent
     * accidental scope changes that would break multi-tenant isolation guarantees.
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
    internal data class AuthorizationSessionErrorEntry(
        val code: String,
        val message: String,
    ) {
        fun toPublic(): AuthorizationSessionError = AuthorizationSessionError(code = code, message = message)
    }

    @Serializable
    internal data class AuthorizationSessionCallbackEntry(
        val url: String,
        /**
         * Empty means "all transitions".
         */
        val statuses: List<String> = emptyList(),
    ) {
        fun toPublic(): AuthorizationSessionCallbackConfig =
            AuthorizationSessionCallbackConfig(
                url = url,
                statuses = statuses.mapNotNull { name -> AuthorizationSessionStatus.entries.firstOrNull { it.name == name } },
            )
    }

    @Serializable
    internal data class ParsedAuthorizationResponseEntry(
        val rawVpToken: String,
        val state: String? = null,
        val jarmMode: String? = null,
        val jarmIssuer: String? = null,
    ) {
        fun toPublic(): ParsedAuthorizationResponse {
            val vpToken =
                KvAuthorizationSessionStore.parseVpToken(rawVpToken)
                    ?: throw IllegalArgumentException("Invalid vp_token format")
            val jarmModeEnum =
                jarmMode?.let {
                    try {
                        com.sphereon.oauth2.common.jarm.JarmMode
                            .valueOf(it)
                    } catch (_: Exception) {
                        // Ignored: unrecognized JARM mode value
                        null
                    }
                }
            return ParsedAuthorizationResponse(
                vpToken = vpToken,
                state = state,
                rawVpToken = rawVpToken,
                jarmMode = jarmModeEnum,
                jarmIssuer = jarmIssuer,
            )
        }
    }

    @Serializable
    internal data class MatchedCredentialEntry(
        val credentialQueryId: String,
        val format: String,
        val presentation: String,
        val disclosedClaims: Map<String, JsonElement> = emptyMap(),
    ) {
        fun toPublic(): MatchedCredential =
            MatchedCredential(
                credentialQueryId = credentialQueryId,
                format = format,
                presentation = presentation,
                disclosedClaims =
                    disclosedClaims.mapValues { (_, element) ->
                        when (element) {
                            is JsonPrimitive -> element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.contentOrNull
                            is JsonNull -> null
                            else -> element.toString()
                        }
                    },
            )

        companion object {
            fun fromPublic(matched: MatchedCredential): MatchedCredentialEntry =
                MatchedCredentialEntry(
                    credentialQueryId = matched.credentialQueryId,
                    format = matched.format,
                    presentation = matched.presentation,
                    disclosedClaims =
                        matched.disclosedClaims.mapValues { (_, value) ->
                            when (value) {
                                is JsonElement -> value
                                is String -> JsonPrimitive(value)
                                is Number -> JsonPrimitive(value)
                                is Boolean -> JsonPrimitive(value)
                                null -> JsonNull
                                else -> JsonPrimitive(value.toString())
                            }
                        },
                )
        }
    }

    @Serializable
    internal data class ValidationResultEntry(
        val valid: Boolean,
        val matchedCredentials: List<MatchedCredentialEntry> = emptyList(),
        val errors: List<String> = emptyList(),
    ) {
        fun toPublic(): ValidationResult =
            ValidationResult(
                valid = valid,
                matchedCredentials = matchedCredentials.map { it.toPublic() },
                errors = errors,
            )
    }

    @Serializable
    internal data class AuthorizationSessionEntry(
        val sessionId: String,
        val correlationId: String,
        val queryId: String? = null,
        val dcqlQuery: DcqlQuery,
        val authorizationRequestJson: JsonObject,
        val status: String,
        val error: AuthorizationSessionErrorEntry? = null,
        val parsedResponse: ParsedAuthorizationResponseEntry? = null,
        val validationResult: ValidationResultEntry? = null,
        val callback: AuthorizationSessionCallbackEntry? = null,
        val jarmEncryptionKeyAlias: String? = null,
        val jarmEncryptionKeyProviderId: String? = null,
        val createdAt: Long,
        val updatedAt: Long,
        val expiresAt: Long,
    ) {
        fun toPublic(json: Json): AuthorizationSession {
            val authorizationRequest =
                json.decodeFromJsonElement(
                    com.sphereon.oauth2.common.model.AuthorizationRequest
                        .serializer(),
                    authorizationRequestJson,
                )

            val statusEnum =
                AuthorizationSessionStatus.entries.firstOrNull { it.name == status }
                    ?: AuthorizationSessionStatus.ERROR

            return AuthorizationSession(
                sessionId = sessionId,
                correlationId = correlationId,
                queryId = queryId,
                dcqlQuery = dcqlQuery,
                authorizationRequest = authorizationRequest,
                status = statusEnum,
                error = error?.toPublic(),
                parsedResponse = parsedResponse?.toPublic(),
                validationResult = validationResult?.toPublic(),
                callback = callback?.toPublic(),
                jarmEncryptionKeyAlias = jarmEncryptionKeyAlias,
                jarmEncryptionKeyProviderId = jarmEncryptionKeyProviderId,
                createdAt = createdAt,
                updatedAt = updatedAt,
                expiresAt = expiresAt,
            )
        }
    }

    override suspend fun createSession(
        correlationId: String?,
        args: AuthorizationSessionCreateArgs,
        ttlSeconds: Long,
    ): IdkResult<AuthorizationSession, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val effectiveCorrelationId = correlationId ?: generateSecureId()

        val dcqlQuery = resolveDcqlQuery(args).getOrElse { return Err(it) }

        val authorizationRequestJson = buildAuthorizationRequestJson(args, dcqlQuery).getOrElse { return Err(it) }

        val sessionId = generateSecureId()
        val expiresAt = now + (ttlSeconds * MILLIS_PER_SECOND)

        val entry =
            AuthorizationSessionEntry(
                sessionId = sessionId,
                correlationId = effectiveCorrelationId,
                queryId = args.queryId,
                dcqlQuery = dcqlQuery,
                authorizationRequestJson = authorizationRequestJson,
                status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED.name,
                error = null,
                parsedResponse = null,
                validationResult = null,
                callback =
                    args.callback?.let {
                        AuthorizationSessionCallbackEntry(
                            url = it.url,
                            statuses = it.statuses.map(AuthorizationSessionStatus::name),
                        )
                    },
                createdAt = now,
                updatedAt = now,
                expiresAt = expiresAt,
            )

        return kv
            .put(namespace, effectiveCorrelationId, entry, ttlSeconds.seconds)
            .map {
                val created = entry.toPublic(json)
                dispatchIfConfigured(created)
                created
            }.mapError { e ->
                IdkError.fromString(message = "Failed to create authorization session: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_AUTH_SESSION_STORE_ERROR")
            }
    }

    override suspend fun getByCorrelationId(correlationId: String): IdkResult<AuthorizationSession?, IdkError> = get(correlationId)

    override suspend fun updateStatus(
        correlationId: String,
        status: AuthorizationSessionStatus,
        error: AuthorizationSessionError?,
    ): IdkResult<AuthorizationSession, IdkError> =
        update(correlationId) { existing, now ->
            val updatedError = error?.let { AuthorizationSessionErrorEntry(code = it.code, message = it.message) } ?: existing.error
            existing.copy(
                status = status.name,
                error = updatedError,
                updatedAt = now,
            )
        }

    override suspend fun storeResponse(
        correlationId: String,
        parsedResponse: ParsedAuthorizationResponse,
    ): IdkResult<AuthorizationSession, IdkError> =
        update(correlationId) { existing, now ->
            existing.copy(
                status = AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_RECEIVED.name,
                parsedResponse =
                    ParsedAuthorizationResponseEntry(
                        rawVpToken = parsedResponse.rawVpToken,
                        state = parsedResponse.state,
                        jarmMode = parsedResponse.jarmMode?.name,
                        jarmIssuer = parsedResponse.jarmIssuer,
                    ),
                updatedAt = now,
            )
        }

    override suspend fun storeValidationResult(
        correlationId: String,
        validationResult: ValidationResult,
    ): IdkResult<AuthorizationSession, IdkError> =
        update(correlationId) { existing, now ->
            val nextStatus =
                if (validationResult.valid) {
                    AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED
                } else {
                    AuthorizationSessionStatus.ERROR
                }
            val nextError =
                if (!validationResult.valid && validationResult.errors.isNotEmpty()) {
                    AuthorizationSessionErrorEntry(code = "validation_failed", message = validationResult.errors.joinToString("; "))
                } else {
                    existing.error
                }
            existing.copy(
                status = nextStatus.name,
                error = nextError,
                validationResult =
                    ValidationResultEntry(
                        valid = validationResult.valid,
                        matchedCredentials =
                            validationResult.matchedCredentials.map {
                                MatchedCredentialEntry.fromPublic(it)
                            },
                        errors = validationResult.errors,
                    ),
                updatedAt = now,
            )
        }

    override suspend fun getForRequestUri(
        correlationId: String,
        markRetrieved: Boolean,
    ): IdkResult<AuthorizationSession?, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val existing =
            kv.get(namespace, correlationId).getOrElse { e ->
                return Err(
                    IdkError.fromString(
                        message = "Failed to read authorization session: ${e.message}",
                        exception = IllegalStateException(e.toString()),
                        code = "OID4VP_AUTH_SESSION_STORE_ERROR",
                    ),
                )
            } ?: return Ok(null)

        if (existing.expiresAt <= now) {
            kv.delete(namespace, correlationId)
            return Ok(null)
        }

        if (existing.status != AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED.name) {
            return Ok(null)
        }

        if (!markRetrieved) {
            return Ok(existing.toPublic(json))
        }

        return update(correlationId) { entry, updatedNow ->
            entry.copy(
                status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_RETRIEVED.name,
                updatedAt = updatedNow,
            )
        }.map { it }
    }

    override suspend fun put(
        key: String,
        value: AuthorizationSession,
        ttlSeconds: Long,
    ): IdkResult<StoreMetadata, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val expiresAt = now + (ttlSeconds * MILLIS_PER_SECOND)

        val authorizationRequestJson =
            json
                .encodeToJsonElement(
                    com.sphereon.oauth2.common.model.AuthorizationRequest
                        .serializer(),
                    value.authorizationRequest,
                ).jsonObject

        val entry =
            AuthorizationSessionEntry(
                sessionId = value.sessionId,
                correlationId = value.correlationId,
                queryId = value.queryId,
                dcqlQuery = value.dcqlQuery,
                authorizationRequestJson = authorizationRequestJson,
                status = value.status.name,
                error = value.error?.let { AuthorizationSessionErrorEntry(code = it.code, message = it.message) },
                parsedResponse =
                    value.parsedResponse?.let {
                        ParsedAuthorizationResponseEntry(
                            rawVpToken = it.rawVpToken,
                            state = it.state,
                            jarmMode = it.jarmMode?.name,
                            jarmIssuer = it.jarmIssuer,
                        )
                    },
                validationResult = value.validationResult?.let { ValidationResultEntry(valid = it.valid, errors = it.errors) },
                callback = value.callback?.let { AuthorizationSessionCallbackEntry(url = it.url, statuses = it.statuses.map(AuthorizationSessionStatus::name)) },
                jarmEncryptionKeyAlias = value.jarmEncryptionKeyAlias,
                jarmEncryptionKeyProviderId = value.jarmEncryptionKeyProviderId,
                createdAt = value.createdAt,
                updatedAt = now,
                expiresAt = expiresAt,
            )

        return kv
            .put(namespace, key, entry, ttlSeconds.seconds)
            .map { putResult ->
                dispatchIfConfigured(entry.toPublic(json))
                StoreMetadata(createdAt = putResult.metadata.createdAtEpochMillis, expiresAt = putResult.metadata.expiresAtEpochMillis)
            }.mapError { e ->
                IdkError.fromString(message = "Failed to put authorization session: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_AUTH_SESSION_STORE_ERROR")
            }
    }

    override suspend fun get(key: String): IdkResult<AuthorizationSession?, IdkError> =
        kv.get(namespace, key).map { it?.toPublic(json) }.mapError { e ->
            IdkError.fromString(
                message = "Failed to read authorization session: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = "OID4VP_AUTH_SESSION_STORE_ERROR",
            )
        }

    override suspend fun getEntry(key: String): IdkResult<com.sphereon.openid.oid4vp.common.store.StoredEntry<AuthorizationSession>?, IdkError> =
        kv
            .getEntry(namespace, key)
            .map { entry ->
                entry?.let {
                    com.sphereon.openid.oid4vp.common.store.StoredEntry(
                        value = it.value.toPublic(json),
                        createdAt = it.metadata.createdAtEpochMillis,
                        expiresAt = it.metadata.expiresAtEpochMillis,
                    )
                }
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to read authorization session entry: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = "OID4VP_AUTH_SESSION_STORE_ERROR",
                )
            }

    override suspend fun delete(key: String): IdkResult<Boolean, IdkError> =
        kv.delete(namespace, key).mapError { e ->
            IdkError.fromString(message = "Failed to delete authorization session: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_AUTH_SESSION_STORE_ERROR")
        }

    override suspend fun exists(key: String): IdkResult<Boolean, IdkError> =
        kv.exists(namespace, key).mapError { e ->
            IdkError.fromString(message = "Failed to check authorization session existence: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_AUTH_SESSION_STORE_ERROR")
        }

    override suspend fun touch(
        key: String,
        ttlSeconds: Long,
    ): IdkResult<Boolean, IdkError> =
        kv.touch(namespace, key, ttlSeconds.seconds).mapError { e ->
            IdkError.fromString(message = "Failed to touch authorization session: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_AUTH_SESSION_STORE_ERROR")
        }

    override suspend fun cleanupExpired(): IdkResult<Int, IdkError> =
        kv.cleanupExpired(namespace).mapError { e ->
            IdkError.fromString(message = "Failed to cleanup expired authorization sessions: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_AUTH_SESSION_STORE_ERROR")
        }

    private suspend fun resolveDcqlQuery(args: AuthorizationSessionCreateArgs): IdkResult<DcqlQuery, IdkError> {
        args.dcqlQuery?.let { return Ok(it) }
        val queryId =
            args.queryId
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Either dcqlQuery or queryId must be provided"))

        val config =
            dcqlQueryConfigurationStore.getByQueryId(queryId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "dcqlQueryConfiguration:$queryId", message = "DCQL query configuration not found: $queryId"))

        if (!config.enabled) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "DCQL query configuration is disabled: $queryId"))
        }
        return Ok(config.dcqlQuery)
    }

    private fun buildAuthorizationRequestJson(
        args: AuthorizationSessionCreateArgs,
        dcqlQuery: DcqlQuery,
    ): IdkResult<JsonObject, IdkError> {
        val redirectUri = args.redirectUri ?: args.responseUri ?: args.clientId

        val dcqlQueryJson =
            try {
                Json.encodeToJsonElement(DcqlQuery.serializer(), dcqlQuery).jsonObject
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to serialize DCQL query", throwable = expected))
            }

        val request =
            buildOid4vpAuthorizationRequest(
                clientId = args.clientId,
                redirectUri = redirectUri,
                responseType = "vp_token",
            ) {
                nonce(args.nonce)
                dcqlQuery(dcqlQueryJson)

                responseMode(args.responseMode)
                if (args.responseMode == ResponseMode.DIRECT_POST || args.responseMode == ResponseMode.DIRECT_POST_JWT) {
                    args.responseUri?.let { responseUri(it) }
                }

                args.state?.let { state(it) }
            }

        return Ok(
            json
                .encodeToJsonElement(
                    com.sphereon.oauth2.common.model.AuthorizationRequest
                        .serializer(),
                    request,
                ).jsonObject,
        )
    }

    private suspend fun update(
        correlationId: String,
        transform: (AuthorizationSessionEntry, nowEpochMillis: Long) -> AuthorizationSessionEntry,
    ): IdkResult<AuthorizationSession, IdkError> {
        val now = clock.now().toEpochMilliseconds()

        val existing =
            kv.get(namespace, correlationId).getOrElse { e ->
                return Err(
                    IdkError.fromString(
                        message = "Failed to read authorization session: ${e.message}",
                        exception = IllegalStateException(e.toString()),
                        code = "OID4VP_AUTH_SESSION_STORE_ERROR",
                    ),
                )
            } ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "authorizationSession:$correlationId", message = "Authorization session not found"))

        if (existing.expiresAt <= now) {
            kv.delete(namespace, correlationId)
            return Err(IdkError.fromString(message = "Authorization session expired", code = "OID4VP_AUTH_SESSION_EXPIRED"))
        }

        val updated = transform(existing, now)
        val ttlSecondsRemaining = ((updated.expiresAt - now).coerceAtLeast(0L) / MILLIS_PER_SECOND).coerceAtLeast(1L)

        kv.put(namespace, correlationId, updated, ttlSecondsRemaining.seconds).getOrElse { e ->
            return Err(IdkError.fromString(message = "Failed to update authorization session: ${e.message}", exception = IllegalStateException(e.toString()), code = "OID4VP_AUTH_SESSION_STORE_ERROR"))
        }

        val public = updated.toPublic(json)
        dispatchIfConfigured(public)
        return Ok(public)
    }

    private suspend fun dispatchIfConfigured(session: AuthorizationSession) {
        val callback = session.callback ?: return
        val shouldEmit = callback.statuses.isEmpty() || callback.statuses.contains(session.status)
        if (!shouldEmit) {
            return
        }

        val update =
            AuthorizationSessionStatusUpdate(
                correlationId = session.correlationId,
                status = session.status,
                updatedAt = session.updatedAt,
                errorCode = session.error?.code,
                errorMessage = session.error?.message,
            )

        callbackDispatcher.dispatch(callback.url, update).fold(
            success = { },
            failure = { e ->
                // Best-effort: do not fail the main flow because callback delivery failed.
                log.warn("Failed to dispatch authorization session callback: ${e.message.defaultMessage}")
            },
        )
    }

    private fun generateSecureId(): String {
        val bytes = CryptographyRandom.nextBytes(RANDOM_BYTES_SIZE)
        return bytes.encodeToHex()
    }

    private companion object {
        fun parseVpToken(vpTokenRaw: String): VpToken? {
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
    }
}

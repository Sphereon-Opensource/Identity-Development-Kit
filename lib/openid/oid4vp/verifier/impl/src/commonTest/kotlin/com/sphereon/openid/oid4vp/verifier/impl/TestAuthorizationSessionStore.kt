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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.common.store.StoreMetadata
import com.sphereon.openid.oid4vp.common.store.StoredEntry
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCreateArgs
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionError
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import kotlin.time.Clock

/**
 * Minimal in-memory [AuthorizationSessionStore] for unit tests.
 *
 * This store is not intended to fully emulate TTL behavior; it just supports the methods used by command tests.
 */
internal class TestAuthorizationSessionStore : AuthorizationSessionStore {
    private val clock = Clock.System

    private data class Stored(
        val value: AuthorizationSession,
        val createdAt: Long,
        val expiresAt: Long,
    )

    private val entries = mutableMapOf<String, Stored>()

    override suspend fun createSession(
        correlationId: String?,
        args: AuthorizationSessionCreateArgs,
        ttlSeconds: Long,
    ): IdkResult<AuthorizationSession, IdkError> {
        // Not needed for current command tests; return a simple error if used unexpectedly.
        return Ok(
            AuthorizationSession(
                instanceId = args.instanceId,
                sessionId = correlationId ?: "test",
                correlationId = correlationId ?: "test",
                queryId = args.queryId,
                dcqlQuery = args.dcqlQuery ?: throw IllegalArgumentException("dcqlQuery is required in test store"),
                authorizationRequest =
                    com.sphereon.oauth2.common.model.AuthorizationRequest(
                        clientId = args.clientId,
                        redirectUri = args.redirectUri ?: args.responseUri ?: args.clientId,
                        state = args.state,
                    ),
                status = AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED,
                createdAt = clock.now().toEpochMilliseconds(),
                updatedAt = clock.now().toEpochMilliseconds(),
                expiresAt = clock.now().toEpochMilliseconds() + ttlSeconds * 1000,
            ),
        )
    }

    override suspend fun getByCorrelationId(correlationId: String): IdkResult<AuthorizationSession?, IdkError> = get(correlationId)

    override suspend fun updateStatus(
        correlationId: String,
        status: AuthorizationSessionStatus,
        error: AuthorizationSessionError?,
    ): IdkResult<AuthorizationSession, IdkError> {
        val existing =
            entries[correlationId] ?: return Ok(
                AuthorizationSession(
                    instanceId = "verifier-instance-test-store-missing-session",
                    sessionId = correlationId,
                    correlationId = correlationId,
                    dcqlQuery =
                        com.sphereon.openid.oid4vp.dcql
                            .DcqlQuery(credentials = emptyList()),
                    authorizationRequest =
                        com.sphereon.oauth2.common.model.AuthorizationRequest(
                            clientId = "test",
                            redirectUri = "test",
                        ),
                    status = AuthorizationSessionStatus.ERROR,
                    error = AuthorizationSessionError(code = "not_found", message = "not found"),
                    createdAt = 0,
                    updatedAt = 0,
                    expiresAt = 0,
                ),
            )
        val updated = existing.value.copy(status = status, error = error, updatedAt = clock.now().toEpochMilliseconds())
        entries[correlationId] = existing.copy(value = updated)
        return Ok(updated)
    }

    override suspend fun storeResponse(
        correlationId: String,
        parsedResponse: ParsedAuthorizationResponse,
    ): IdkResult<AuthorizationSession, IdkError> {
        val existing =
            entries[correlationId] ?: return Ok(
                AuthorizationSession(
                    instanceId = "verifier-instance-test-store-missing-session",
                    sessionId = correlationId,
                    correlationId = correlationId,
                    dcqlQuery =
                        com.sphereon.openid.oid4vp.dcql
                            .DcqlQuery(credentials = emptyList()),
                    authorizationRequest =
                        com.sphereon.oauth2.common.model.AuthorizationRequest(
                            clientId = "test",
                            redirectUri = "test",
                        ),
                    status = AuthorizationSessionStatus.ERROR,
                    error = AuthorizationSessionError(code = "not_found", message = "not found"),
                    createdAt = 0,
                    updatedAt = 0,
                    expiresAt = 0,
                ),
            )
        val updated =
            existing.value.copy(
                status = AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_RECEIVED,
                parsedResponse = parsedResponse,
                updatedAt = clock.now().toEpochMilliseconds(),
            )
        entries[correlationId] = existing.copy(value = updated)
        return Ok(updated)
    }

    override suspend fun storeValidationResult(
        correlationId: String,
        validationResult: ValidationResult,
    ): IdkResult<AuthorizationSession, IdkError> {
        val existing =
            entries[correlationId] ?: return Ok(
                AuthorizationSession(
                    instanceId = "verifier-instance-test-store-missing-session",
                    sessionId = correlationId,
                    correlationId = correlationId,
                    dcqlQuery =
                        com.sphereon.openid.oid4vp.dcql
                            .DcqlQuery(credentials = emptyList()),
                    authorizationRequest =
                        com.sphereon.oauth2.common.model.AuthorizationRequest(
                            clientId = "test",
                            redirectUri = "test",
                        ),
                    status = AuthorizationSessionStatus.ERROR,
                    error = AuthorizationSessionError(code = "not_found", message = "not found"),
                    createdAt = 0,
                    updatedAt = 0,
                    expiresAt = 0,
                ),
            )
        val updated =
            existing.value.copy(
                status = if (validationResult.valid) AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED else AuthorizationSessionStatus.ERROR,
                validationResult = validationResult,
                updatedAt = clock.now().toEpochMilliseconds(),
            )
        entries[correlationId] = existing.copy(value = updated)
        return Ok(updated)
    }

    override suspend fun getForRequestUri(
        correlationId: String,
        markRetrieved: Boolean,
    ): IdkResult<AuthorizationSession?, IdkError> {
        val session = entries[correlationId]?.value ?: return Ok(null)
        // Test store doesn't enforce status gating; production store does.
        return Ok(session)
    }

    override suspend fun put(
        key: String,
        value: AuthorizationSession,
        ttlSeconds: Long,
    ): IdkResult<StoreMetadata, IdkError> {
        val now = clock.now().toEpochMilliseconds()
        val expiresAt = now + ttlSeconds * 1000
        entries[key] = Stored(value = value, createdAt = now, expiresAt = expiresAt)
        return Ok(StoreMetadata(createdAt = now, expiresAt = expiresAt))
    }

    override suspend fun get(key: String): IdkResult<AuthorizationSession?, IdkError> = Ok(entries[key]?.value)

    override suspend fun getEntry(key: String): IdkResult<StoredEntry<AuthorizationSession>?, IdkError> {
        val stored = entries[key] ?: return Ok(null)
        return Ok(StoredEntry(value = stored.value, createdAt = stored.createdAt, expiresAt = stored.expiresAt))
    }

    override suspend fun delete(key: String): IdkResult<Boolean, IdkError> = Ok(entries.remove(key) != null)

    override suspend fun exists(key: String): IdkResult<Boolean, IdkError> = Ok(entries.containsKey(key))

    override suspend fun touch(
        key: String,
        ttlSeconds: Long,
    ): IdkResult<Boolean, IdkError> = Ok(entries.containsKey(key))

    override suspend fun cleanupExpired(): IdkResult<Int, IdkError> = Ok(0)
}

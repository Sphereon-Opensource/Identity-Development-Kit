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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import com.sphereon.oauth2.server.authorization.storage.SessionStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * In-memory implementation of SessionStorage
 *
 * IMPORTANT: This is suitable for development/testing only.
 * Production deployments should use persistent storage (Redis, SQL, etc.)
 *
 * Thread Safety: Basic implementation - consider locking for production use.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SessionStorage>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemorySessionStorageImpl", exact = true)
class InMemorySessionStorageImpl(
    private val backingStorage: InMemoryOAuth2BackingStorage,
) : SessionStorage {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    override suspend fun createSession(session: AuthorizationSession): IdkResult<String, AuthorizationServerError.StorageError> {
        return try {
            if (partition.sessions.containsKey(session.sessionId)) {
                return Err(
                    AuthorizationServerError.StorageError(
                        operation = "createSession",
                        details = "Session with ID ${session.sessionId} already exists",
                        exception = null,
                    ),
                )
            }
            partition.sessions[session.sessionId] = session
            Ok(session.sessionId)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "createSession",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
    }

    override suspend fun getSession(sessionId: String): IdkResult<AuthorizationSession?, AuthorizationServerError.StorageError> =
        try {
            Ok(partition.sessions[sessionId])
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "getSession",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun updateSession(
        sessionId: String,
        session: AuthorizationSession,
    ): IdkResult<Unit, AuthorizationServerError> {
        return try {
            if (sessionId !in partition.sessions) {
                return Err(AuthorizationServerError.SessionNotFound(sessionId = sessionId))
            }

            partition.sessions[sessionId] = session
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "updateSession",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
    }

    override suspend fun deleteSession(sessionId: String): IdkResult<Unit, AuthorizationServerError.StorageError> =
        try {
            partition.sessions.remove(sessionId)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "deleteSession",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findSessionsByUser(userId: String): IdkResult<List<AuthorizationSession>, AuthorizationServerError.StorageError> =
        try {
            val sessions = partition.sessions.values.filter { it.authenticatedUserId == userId }
            Ok(sessions)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findSessionsByUser",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findSessionsByClient(clientId: String): IdkResult<List<AuthorizationSession>, AuthorizationServerError.StorageError> =
        try {
            val sessions = partition.sessions.values.filter { it.clientId == clientId }
            Ok(sessions)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findSessionsByClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun cleanupExpiredSessions(): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val now = Clock.System.now()
            val expired =
                partition.sessions.filter { (_, session) ->
                    session.expiresAt < now || session.status == SessionStatus.EXPIRED
                }
            expired.keys.forEach { partition.sessions.remove(it) }
            Ok(expired.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "cleanupExpiredSessions",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun deleteSessionsForUser(userId: String): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val toDelete =
                partition.sessions.filter { (_, session) ->
                    session.authenticatedUserId == userId
                }
            toDelete.keys.forEach { partition.sessions.remove(it) }
            Ok(toDelete.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "deleteSessionsForUser",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun deleteSessionsForClient(clientId: String): IdkResult<Int, AuthorizationServerError.StorageError> =
        try {
            val toDelete =
                partition.sessions.filter { (_, session) ->
                    session.clientId == clientId
                }
            toDelete.keys.forEach { partition.sessions.remove(it) }
            Ok(toDelete.size)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "deleteSessionsForClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun sessionExists(sessionId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        try {
            Ok(partition.sessions.containsKey(sessionId))
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "sessionExists",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    @ContributesTo(AppScope::class)
    interface Graph {
        val sessionStorage: SessionStorage
    }
}

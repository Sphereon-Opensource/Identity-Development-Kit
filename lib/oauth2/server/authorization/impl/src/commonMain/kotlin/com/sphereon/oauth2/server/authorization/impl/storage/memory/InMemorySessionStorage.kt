/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.Err
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.SessionStatus
import com.sphereon.oauth2.server.authorization.storage.SessionStorage
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

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
    private val backingStorage: InMemoryOAuth2BackingStorage
) : SessionStorage {

    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    override suspend fun createSession(
        session: AuthorizationSession
    ): IdkResult<String, AuthorizationServerError.StorageError> {
        return try {
            if (partition.sessions.containsKey(session.sessionId)) {
                return Err(AuthorizationServerError.StorageError(
                    operation = "createSession",
                    details = "Session with ID ${session.sessionId} already exists",
                    exception = null
                ))
            }
            partition.sessions[session.sessionId] = session
            Ok(session.sessionId)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "createSession",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun getSession(
        sessionId: String
    ): IdkResult<AuthorizationSession?, AuthorizationServerError.StorageError> {
        return try {
            Ok(partition.sessions[sessionId])
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "getSession",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun updateSession(
        sessionId: String,
        session: AuthorizationSession
    ): IdkResult<Unit, AuthorizationServerError> {
        return try {
            val existing = partition.sessions[sessionId]
                ?: return Err(AuthorizationServerError.SessionNotFound(sessionId = sessionId))

            partition.sessions[sessionId] = session
            Ok(Unit)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "updateSession",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun deleteSession(
        sessionId: String
    ): IdkResult<Unit, AuthorizationServerError.StorageError> {
        return try {
            partition.sessions.remove(sessionId)
            Ok(Unit)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "deleteSession",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun findSessionsByUser(
        userId: String
    ): IdkResult<List<AuthorizationSession>, AuthorizationServerError.StorageError> {
        return try {
            val sessions = partition.sessions.values.filter { it.authenticatedUserId == userId }
            Ok(sessions)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "findSessionsByUser",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun findSessionsByClient(
        clientId: String
    ): IdkResult<List<AuthorizationSession>, AuthorizationServerError.StorageError> {
        return try {
            val sessions = partition.sessions.values.filter { it.clientId == clientId }
            Ok(sessions)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "findSessionsByClient",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun cleanupExpiredSessions(): IdkResult<Int, AuthorizationServerError.StorageError> {
        return try {
            val now = Clock.System.now()
            val expired = partition.sessions.filter { (_, session) ->
                session.expiresAt < now || session.status == SessionStatus.EXPIRED
            }
            expired.keys.forEach { partition.sessions.remove(it) }
            Ok(expired.size)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "cleanupExpiredSessions",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun deleteSessionsForUser(
        userId: String
    ): IdkResult<Int, AuthorizationServerError.StorageError> {
        return try {
            val toDelete = partition.sessions.filter { (_, session) ->
                session.authenticatedUserId == userId
            }
            toDelete.keys.forEach { partition.sessions.remove(it) }
            Ok(toDelete.size)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "deleteSessionsForUser",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun deleteSessionsForClient(
        clientId: String
    ): IdkResult<Int, AuthorizationServerError.StorageError> {
        return try {
            val toDelete = partition.sessions.filter { (_, session) ->
                session.clientId == clientId
            }
            toDelete.keys.forEach { partition.sessions.remove(it) }
            Ok(toDelete.size)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "deleteSessionsForClient",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun sessionExists(
        sessionId: String
    ): IdkResult<Boolean, AuthorizationServerError.StorageError> {
        return try {
            Ok(partition.sessions.containsKey(sessionId))
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "sessionExists",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    /**
     * Component interface for DI access to SessionStorage in tests
     */
    @ContributesTo(AppScope::class)
    interface Component {
        val sessionStorage: SessionStorage
    }
}

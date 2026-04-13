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
 *
 */

package com.sphereon.di.context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import software.amazon.app.platform.scope.Scope
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.context.UserContextInstance
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * Interface for performing operations across different context/session boundaries.
 * This is useful for scenarios like NFC taps from background services that need to
 * interact with authenticated user sessions.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CrossContextOperations", exact = true)
interface CrossContextOperations {

    /**
     * Executes an operation in a different user context.
     *
     * @param targetContextId The ID of the target user context
     * @param operation The operation to execute in the target context
     * @return The result of the operation, or null if the context doesn't exist
     */
    suspend fun <T> executeInUserContext(
        targetContextId: String,
        operation: suspend (UserContext, Scope) -> T
    ): T?

    /**
     * Executes an operation in a different session context.
     *
     * @param targetContextId The ID of the target user context
     * @param targetSessionId The ID of the target session within that user context
     * @param operation The operation to execute in the target session
     * @return The result of the operation, or null if the context or session doesn't exist
     */
    suspend fun <T> executeInSessionContext(
        targetContextId: String,
        targetSessionId: String?,
        operation: suspend (UserContextInstance, SessionInstance?) -> T
    ): T?

    /**
     * Finds and executes an operation in the "most suitable" authenticated context.
     * This is useful for background operations that need to act on behalf of a user.
     *
     * @param operation The operation to execute
     * @param preferredTenantId Optional preferred tenant ID to prioritize
     * @return The result of the operation, or null if no suitable context is found
     */
    suspend fun <T> executeInBestAuthenticatedContext(
        preferredTenantId: String? = null,
        operation: suspend (UserContext, Scope) -> T
    ): T?

    /**
     * Gets a service instance from a specific context/session combination.
     *
     * @param targetContextId The ID of the target user context
     * @param targetSessionId The ID of the target session (optional)
     * @param serviceType The type of service to retrieve
     * @return The service instance, or null if not found
     */
    fun <T : Any> getServiceFromContext(
        targetContextId: String,
        targetSessionId: String? = null,
        serviceType: KClass<T>
    ): T?

    /**
     * Lists all available authenticated contexts.
     * Excludes anonymous contexts.
     */
    fun getAvailableAuthenticatedContexts(): Map<String, UserContext>

    /**
     * Lists all available sessions for a given context.
     */
    fun getAvailableSessionsForContext(contextId: String): Map<String, SessionContext>
}

/**
 * Data class representing a context/session combination for cross-boundary operations.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ContextSessionPair", exact = true)
data class ContextSessionPair(
    val contextId: String,
    val sessionId: String?,
    val context: UserContext,
    val session: SessionContext? = null
) {
    val isAnonymous: Boolean
        get() = context.principal.toString() == IdentityConstants.ANONYMOUS_PRINCIPAL_ID

    val isAuthenticated: Boolean
        get() = !isAnonymous
}

/**
 * Exception thrown when a cross-context operation fails.
 */
class CrossContextOperationException(
    message: String,
    val sourceContextId: String? = null,
    val targetContextId: String? = null,
    val targetSessionId: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Result wrapper for cross-context operations.
 */
sealed class CrossContextResult<out T> {
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("CrossContextResultSuccess", exact = true)
    data class Success<T>(val value: T, val executedIn: ContextSessionPair) : CrossContextResult<T>()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("CrossContextResultContextNotFound", exact = true)
    data class ContextNotFound(val contextId: String) : CrossContextResult<Nothing>()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("CrossContextResultSessionNotFound", exact = true)
    data class SessionNotFound(val contextId: String, val sessionId: String) : CrossContextResult<Nothing>()

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("CrossContextResultOperationFailed", exact = true)
    data class OperationFailed(val error: Throwable, val contextId: String, val sessionId: String? = null) : CrossContextResult<Nothing>()

    object NoSuitableContextFound : CrossContextResult<Nothing>()
}

// Convenience extension function
inline fun <reified T : Any> CrossContextOperations.getServiceFromContext(
    targetContextId: String,
    targetSessionId: String? = null
): T? = getServiceFromContext(targetContextId, targetSessionId, T::class)
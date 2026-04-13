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

package com.sphereon.core.defaults.context

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.coroutines.withContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.di.context.CrossContextOperations
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserContextManager
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionInstance
import kotlinx.coroutines.currentCoroutineContext
import software.amazon.app.platform.scope.Scope
import kotlin.reflect.KClass


@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CrossContextOperations>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("CrossContextOperationsImpl", exact = true)
class CrossContextOperationsImpl(
    private val userContextManager: UserContextManager
) : CrossContextOperations {

    override suspend fun <T> executeInUserContext(
        targetContextId: String,
        operation: suspend (UserContext, Scope) -> T
    ): T? {
        val contextInstance = userContextManager.getById(targetContextId) ?: return null
        return withContext(currentCoroutineContext()) {
            operation(contextInstance.context, contextInstance.scope)
        }
    }

    override suspend fun <T> executeInSessionContext(
        targetContextId: String,
        targetSessionId: String?,
        operation: suspend (UserContextInstance, SessionInstance?) -> T
    ): T? {
        val contextInstance = userContextManager.getById(targetContextId) ?: return null

        val sessionInstance = if (targetSessionId != null) {
            val sessionManager = contextInstance.sessionContextManager
            sessionManager.getById(targetSessionId)
        } else {
            null
        }

        return withContext(currentCoroutineContext()) {
            operation(contextInstance, sessionInstance)
        }
    }

    override suspend fun <T> executeInBestAuthenticatedContext(
        preferredTenantId: String?,
        operation: suspend (UserContext, Scope) -> T
    ): T? {
        // Get all available authenticated contexts
        val allContextIds = userContextManager.listIds()

        // Filter for authenticated contexts (non-anonymous, non-background)
        val authenticatedContexts = allContextIds
            .mapNotNull { userContextManager.getById(it) }
            .filter { instance ->
                !instance.contextId.startsWith("anonymous") && !instance.contextId.startsWith("background")
            }

        if (authenticatedContexts.isEmpty()) return null

        // Prefer the specified tenant if provided
        val targetContext = if (preferredTenantId != null) {
            authenticatedContexts.find { it.context.tenant.tenantId == preferredTenantId }
                ?: authenticatedContexts.first()
        } else {
            authenticatedContexts.first()
        }

        return withContext(currentCoroutineContext()) {
            operation(targetContext.context, targetContext.scope)
        }
    }

    override fun getAvailableAuthenticatedContexts(): Map<String, UserContext> {
        val allContextIds = userContextManager.listIds()
        return allContextIds
            .filter { contextId ->
                !contextId.startsWith("anonymous") && !contextId.startsWith("background")
            }
            .mapNotNull { contextId ->
                userContextManager.getById(contextId)?.let { contextId to it.context }
            }
            .toMap()
    }

    override fun getAvailableSessionsForContext(contextId: String): Map<String, SessionContext> {
        val contextInstance = userContextManager.getById(contextId) ?: return emptyMap()
        val sessionManager = contextInstance.sessionContextManager
        val allSessionIds = sessionManager.listIds()
        return allSessionIds.mapNotNull { sessionId: String ->
            sessionManager.getById(sessionId)?.let { instance ->
                sessionId to instance.sessionContext
            }
        }.toMap()
    }

    override fun <T : Any> getServiceFromContext(
        targetContextId: String,
        targetSessionId: String?,
        serviceType: KClass<T>
    ): T? {
        val contextInstance = userContextManager.getById(targetContextId) ?: return null

        return if (targetSessionId != null) {
            // Get service from specific session
            val sessionManager = contextInstance.sessionContextManager
            val sessionInstance = sessionManager.getById(targetSessionId) ?: return null
            try {
                sessionInstance.getService<T>(serviceType.simpleName ?: return null)
            } catch (e: Exception) {
                null
            }
        } else {
            // Get service from context
            try {
                contextInstance.getService<T>(serviceType.simpleName ?: return null)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Component interface to expose CrossContextOperations publicly from the AppComponent.
     */
    @ContributesTo(AppScope::class)
    interface Component {
        val crossContextOperations: CrossContextOperations
    }
}

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
 *
 */

package com.sphereon.ktor.server.inject.interceptor

import com.sphereon.core.api.app.CoreApiAppExtensionGraph
import com.sphereon.core.api.log.LogService
import com.sphereon.di.app.AppGraph
import com.sphereon.ktor.server.inject.context.RequestScopedContext
import com.sphereon.ktor.server.inject.resolver.PrincipalResolver
import com.sphereon.ktor.server.inject.resolver.TenantResolver
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.util.AttributeKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Interceptor that resolves and sets up user context and session for each Ktor request.
 *
 * This interceptor is automatically installed by the KotlinInject plugin. It performs the following:
 * 1. Resolves tenant from the request (via [TenantResolver])
 * 2. Resolves principal from the request (via [PrincipalResolver])
 * 3. Creates or retrieves the user context (ID-based, no active state)
 * 4. Creates or retrieves the session (using a generated session ID)
 * 5. Stores instances in [RequestScopedContext] as a call attribute
 *
 * **Key Design Principles:**
 * - ID-based resolution (never sets makeActive = true)
 * - Thread-safe via call-scoped attributes
 * - No global state
 * - Creates contexts/sessions lazily
 * - Uses IDK scoped loggers (AppLogManager) for consistency
 * - Multiplatform compatible (JVM, JavaScript, Native, GraalVM)
 *
 * @property appGraph The root application graph
 * @property tenantResolver Resolver for extracting tenant information
 * @property principalResolver Resolver for extracting principal information
 */
class UserContextInterceptor(
    private val appGraph: AppGraph,
    private val tenantResolver: TenantResolver,
    private val principalResolver: PrincipalResolver,
) {
    companion object {
        /**
         * Attribute key for storing the request-scoped context.
         */
        val RequestContextKey = AttributeKey<RequestScopedContext>("RequestScopedContext")
        private const val LOG_TAG = "KotlinInjectPlugin"

        /**
         * Generate a unique session ID using Kotlin's multiplatform UUID support.
         */
        @OptIn(ExperimentalUuidApi::class)
        private fun generateSessionId(): String = Uuid.random().toString()
    }

    // Get the app-scoped logger from the graph
    private val appLogger: LogService by lazy {
        (appGraph as CoreApiAppExtensionGraph).appLogManager.withTag(LOG_TAG)
    }

    suspend fun intercept(call: ApplicationCall): RequestScopedContext {
        try {
            // Resolve tenant and principal
            val tenantInput = tenantResolver.resolve(call)
            val principalInput = principalResolver.resolve(call)

            appLogger.debug("Processing request [tenant=$tenantInput, principal=$principalInput]")

            // Create or get user context (ID-based, no active state)
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    tenantInput = tenantInput,
                    principalInput = principalInput,
                    makeActive = false, // ID-based resolution, no global active state
                )

            // Generate a unique session ID for this request (multiplatform compatible)
            // In production, you might want to use a session cookie or similar
            val sessionId = generateSessionId()
            // Honour the inbound X-Correlation-Id header if present so audit /
            // log lines from this request thread back to the calling system;
            // otherwise the session id is its own natural correlation anchor.
            val correlationId = call.request.header("X-Correlation-Id") ?: sessionId

            // Create or get session (using generated session ID)
            val sessionInstance =
                contextInstance.sessionContextManager.createOrGetFromId(
                    sessionId = sessionId,
                    correlationId = correlationId,
                    makeActive = false, // ID-based resolution, no global active state
                )

            // Store context in call attributes
            val requestContext =
                RequestScopedContext(
                    userInstance = contextInstance,
                    sessionInstance = sessionInstance,
                )
            call.attributes.put(RequestContextKey, requestContext)

            appLogger.trace("Context set: userContext=${contextInstance.contextId}, session=${sessionInstance.sessionId}")
            return requestContext
        } catch (expected: Exception) {
            appLogger.error("Error processing request in UserContextInterceptor: ${expected.message}", expected)
            throw expected
        }
    }
}

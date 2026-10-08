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

package com.sphereon.ktor.server.inject.context

import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.session.SessionInstance

/**
 * Request-scoped holder for user context and session instances.
 *
 * This class is stored as an attribute on each Ktor ApplicationCall, providing
 * thread-safe isolation of context and session instances per request.
 *
 * The [com.sphereon.ktor.server.inject.interceptor.UserContextInterceptor] populates
 * these instances at the beginning of each request, and extension functions use them
 * to resolve services from the correct kotlin-inject scopes.
 *
 * **Thread Safety:**
 * - Each ApplicationCall has its own instance (call-scoped attribute)
 * - No shared state between concurrent requests
 * - Thread-safe by design
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val context = call.requestContext
 *         val userService = context.userInstance.graph.someService
 *         val sessionService = context.sessionInstance.graph.someOtherService
 *     }
 * }
 * ```
 */
data class RequestScopedContext(
    /**
     * User context instance for the current request.
     * Contains the tenant/principal-scoped graph and scope.
     */
    val userInstance: UserContextInstance,
    /**
     * Session instance for the current request.
     * Contains the session-scoped graph and scope.
     */
    val sessionInstance: SessionInstance,
)

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

package com.sphereon.ktor.server.inject

import com.sphereon.di.app.AppGraph
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.session.SessionInstance
import com.sphereon.ktor.server.inject.context.RequestScopedContext
import com.sphereon.ktor.server.inject.interceptor.UserContextInterceptor
import io.ktor.server.application.ApplicationCall
import kotlin.reflect.KClass

/**
 * Extension property to access the AppGraph from within a route.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val config = call.appGraph.appConfigEnvironment
 *         // Use app-scoped services
 *     }
 * }
 * ```
 */
val ApplicationCall.appGraph: AppGraph
    get() = application.kotlinInject.appGraph

/**
 * Extension property to access the request-scoped context.
 * Contains both user context instance and session instance.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val context = call.requestContext
 *         val tenant = context.userInstance.userContext.tenant
 *         val principal = context.userInstance.userContext.principal
 *     }
 * }
 * ```
 */
val ApplicationCall.requestContext: RequestScopedContext
    get() = attributes[UserContextInterceptor.RequestContextKey]

/**
 * Extension property to access the UserContextInstance for the current request.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val userInstance = call.userInstance
 *         val tenant = userInstance.userContext.tenant
 *         val principal = userInstance.userContext.principal
 *     }
 * }
 * ```
 */
val ApplicationCall.userInstance: UserContextInstance
    get() = requestContext.userInstance

/**
 * Extension property to access the SessionInstance for the current request.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val sessionInstance = call.sessionInstance
 *         val sessionId = sessionInstance.sessionId
 *     }
 * }
 * ```
 */
val ApplicationCall.sessionInstance: SessionInstance
    get() = requestContext.sessionInstance

/**
 * Extension function to get a service from the UserScope.
 *
 * This function provides a convenient way to access user-scoped services
 * without manually navigating the graph tree.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val myService = call.getUserService<MyUserScopedService>()
 *         myService.doSomething()
 *     }
 * }
 * ```
 *
 * @param T The service interface type
 * @return The service instance from UserScope
 * @throws IllegalStateException if the service is not found
 */
inline fun <reified T : Any> ApplicationCall.getUserService(): T {
    val serviceType = T::class
    val userInstance = this.userInstance

    // Try to get from scope first
    val service =
        userInstance.scope.getService<T>(serviceType.simpleName ?: "Unknown")
            ?: findServiceInGraph(userInstance.graph, serviceType)
            ?: throw IllegalStateException(
                "Service ${serviceType.simpleName} not found in UserScope for context ${userInstance.contextId}. " +
                    "Ensure the service is annotated with @SingleIn(UserScope::class) and @ContributesBinding(UserScope::class)",
            )

    return service
}

/**
 * Extension function to get a service from the SessionScope.
 *
 * This function provides a convenient way to access session-scoped services
 * without manually navigating the graph tree.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val myService = call.getSessionService<MySessionScopedService>()
 *         myService.doSomething()
 *     }
 * }
 * ```
 *
 * @param T The service interface type
 * @return The service instance from SessionScope
 * @throws IllegalStateException if the service is not found
 */
inline fun <reified T : Any> ApplicationCall.getSessionService(): T {
    val serviceType = T::class
    val sessionInstance = this.sessionInstance

    // Try to get from scope first
    val service =
        sessionInstance.scope.getService<T>(serviceType.simpleName ?: "Unknown")
            ?: findServiceInGraph(sessionInstance.graph, serviceType)
            ?: throw IllegalStateException(
                "Service ${serviceType.simpleName} not found in SessionScope for session ${sessionInstance.sessionId}. " +
                    "Ensure the service is annotated with @SingleIn(SessionScope::class) and @ContributesBinding(SessionScope::class)",
            )

    return service
}

/**
 * Extension function to get a service from the AppScope.
 *
 * This function provides a convenient way to access app-scoped services
 * without manually navigating the graph tree.
 *
 * **Usage:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         val myService = call.getAppService<MyAppScopedService>()
 *         myService.doSomething()
 *     }
 * }
 * ```
 *
 * @param T The service interface type
 * @return The service instance from AppScope
 * @throws IllegalStateException if the service is not found
 */
inline fun <reified T : Any> ApplicationCall.getAppService(): T {
    val serviceType = T::class
    val appGraph = this.appGraph

    // Get from scope - this is the proper way in GraalVM
    val service = appGraph.rootScopeProvider.rootScope.getService<T>(serviceType.simpleName ?: "Unknown")

    if (service != null) {
        return service
    }

    // Fallback: try graph inspection (won't work in GraalVM native image without reflection config)
    return findServiceInGraph(appGraph, serviceType)
        ?: throw IllegalStateException(
            "Service ${serviceType.simpleName} not found in AppScope. " +
                "Ensure the service is annotated with @SingleIn(AppScope::class) and @ContributesBinding(AppScope::class). " +
                "The service must be accessible through the scope registry.",
        )
}

/**
 * Helper function to find a service in a kotlin-inject graph.
 *
 * Uses platform-specific resolution strategy:
 * - JVM: Full Kotlin reflection
 * - JS: Dynamic property access
 * - Native: Scope registry only (graph inspection not available)
 *
 * This function searches the graph for:
 * 1. Properties that return the service type
 * 2. Zero-parameter functions that return the service type (provider functions)
 */
@PublishedApi
internal fun <T : Any> findServiceInGraph(
    graph: Any,
    serviceType: KClass<T>,
): T? {
    val resolver = GraphServiceResolver()
    return resolver.findService(graph, serviceType)
}

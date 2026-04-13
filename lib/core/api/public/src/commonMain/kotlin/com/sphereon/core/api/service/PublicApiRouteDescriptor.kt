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

package com.sphereon.core.api.service

/**
 * App-scoped route metadata descriptor for session-scoped commands.
 *
 * Session-scoped commands (registered via map multibinding) cannot
 * be discovered by app-scoped route resolvers because they don't exist in the
 * [ServiceCommandRegistry]. This descriptor carries lightweight route metadata
 * (commandId, httpMethod, httpPath) into AppScope so the route resolver can
 * build routing tables without needing actual command instances.
 *
 * **Usage:**
 * ```kotlin
 * @ContributesTo(AppScope::class)
 * interface MyPublicApiRoutes {
 *     @Provides @IntoSet
 *     fun myRoute(): PublicApiRouteDescriptor = PublicApiRouteDescriptor.of(
 *         commandId = "my.service.get",
 *         httpMethod = "GET",
 *         httpPath = "/api/my/path/{id}"
 *     )
 * }
 * ```
 */
interface PublicApiRouteDescriptor {
    val commandId: String
    val httpMethod: String
    val httpPath: String

    companion object {
        fun of(
            commandId: String,
            httpMethod: String,
            httpPath: String,
        ): PublicApiRouteDescriptor =
            object : PublicApiRouteDescriptor {
                override val commandId = commandId
                override val httpMethod = httpMethod
                override val httpPath = httpPath
            }
    }
}

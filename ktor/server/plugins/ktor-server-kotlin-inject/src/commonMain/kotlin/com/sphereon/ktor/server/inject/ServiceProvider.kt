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

package com.sphereon.ktor.server.inject

import kotlin.reflect.KClass

/**
 * Interface that components can implement to provide type-safe service lookup
 * without requiring reflection.
 *
 * This is the GraalVM-friendly way to access services from components.
 *
 * **Implementation Example:**
 * ```kotlin
 * @Component
 * abstract class MyAppComponent : ServiceProvider {
 *     abstract val appConfigEnvironment: AppConfigEnvironment
 *
 *     override fun <T : Any> getService(serviceType: KClass<T>): T? {
 *         @Suppress("UNCHECKED_CAST")
 *         return when (serviceType) {
 *             AppConfigEnvironment::class -> appConfigEnvironment as T
 *             // Add other services here
 *             else -> null
 *         }
 *     }
 * }
 * ```
 */
interface ServiceProvider {
    /**
     * Get a service by its type.
     *
     * @param serviceType The KClass of the service interface
     * @return The service instance, or null if not found
     */
    fun <T : Any> getService(serviceType: KClass<T>): T?
}

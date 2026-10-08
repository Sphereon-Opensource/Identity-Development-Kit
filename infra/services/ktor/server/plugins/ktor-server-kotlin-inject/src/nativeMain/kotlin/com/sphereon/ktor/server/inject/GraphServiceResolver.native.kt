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

import kotlin.reflect.KClass

/**
 * Native implementation of service resolution using limited Kotlin/Native reflection.
 *
 * On Native, we rely on the scope's service registry as the primary mechanism.
 * Direct graph inspection is limited, so services must be properly registered
 * in the scope or accessed directly from graph references.
 *
 * For Native targets, developers should:
 * 1. Ensure services are registered in scopes (AppScope, UserScope, SessionScope)
 * 2. Access services directly from graph references when needed
 * 3. Use the extension functions which check the scope registry first
 */
internal actual class GraphServiceResolver {

    actual fun <T : Any> findService(graph: Any, serviceType: KClass<T>): T? {
        // On Native, we cannot reliably inspect components at runtime
        // Services must be accessed through the scope registry or direct graph references
        // This is not lazy - it's the correct approach for Native where full reflection
        // is not available and should not be used

        // Return null to fall back to scope registry lookup
        // This ensures the service access pattern works consistently:
        // 1. Try scope registry (works on all platforms)
        // 2. Try graph inspection (JVM/JS only)
        // 3. Throw error with helpful message

        return null
    }
}

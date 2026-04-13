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

import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.session.SessionInstance
import kotlin.reflect.KClass

/**
 * JavaScript implementation of service resolution.
 *
 * On JS, we access services through the KSP-generated `Inject<ComponentName>` classes,
 * which have `val` properties for all contributed services. These properties use lazy
 * initialization and automatically fall back to provider methods.
 *
 * The key insight is that Kotlin/JS compiles property getters to mangled functions like:
 * `get_propertyName_HASH_k$`. We need to find and call these getters dynamically.
 *
 * This approach is scalable and works with @DependencyGraph contributions from any library.
 */
internal actual class GraphServiceResolver {
    actual fun <T : Any> findService(
        graph: Any,
        serviceType: KClass<T>,
    ): T? {
        return try {
            // Get the service type name
            val serviceName = serviceType.simpleName ?: return null

            // Access the graph dynamically
            val componentDynamic = graph.asDynamic()

            // For wrapped instances (UserContextInstance, SessionInstance), access the inner graph
            val actualGraph =
                when (graph) {
                    is UserContextInstance -> graph.graph.asDynamic()
                    is SessionInstance -> graph.graph.asDynamic()
                    else -> componentDynamic
                }

            // Try common property name patterns
            val propertyNames = generatePropertyNames(serviceName)

            for (propertyName in propertyNames) {
                try {
                    // On Kotlin/JS, properties are compiled to getter functions with mangled names
                    // like: get_propertyName_HASH_k$
                    // We need to find and call these getters

                    // Get the prototype to access the getter functions
                    val proto = js("Object.getPrototypeOf(actualGraph)")
                    val prototypeKeys: Array<String> = js("Object.getOwnPropertyNames(proto)")

                    // Look for getter functions that match: get_<propertyName>_*_k$
                    val getterPattern = "get_${propertyName}_"
                    val matchingGetters = prototypeKeys.filter { it.startsWith(getterPattern) && it.endsWith("_k$") }

                    for (getterName in matchingGetters) {
                        try {
                            val getter = actualGraph[getterName]
                            if (getter != js("undefined") && js("typeof getter === 'function'") as Boolean) {
                                // Call the getter function
                                val value = getter.call(actualGraph)

                                if (value != null && value != js("undefined")) {
                                    @Suppress("UNCHECKED_CAST")
                                    return value as? T
                                }
                            }
                        } catch (_: Exception) {
                            // Getter call failed, try next one
                            continue
                        }
                    }
                } catch (_: Exception) {
                    // Property access failed, try next property name
                    continue
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates possible property names for a given service type name.
     *
     * Examples:
     * - AppConfigEnvironment -> [appConfigEnvironment, AppConfigEnvironment, configEnvironment]
     * - UserContextLogManager -> [userContextLogManager, logManager, UserContextLogManager]
     * - SessionLogManager -> [sessionLogManager, logManager, SessionLogManager]
     */
    private fun generatePropertyNames(typeName: String): List<String> {
        val names = mutableListOf<String>()

        // 1. Standard camelCase version (most common)
        names.add(typeName.replaceFirstChar { it.lowercase() })

        // 2. Original type name (sometimes used as-is)
        names.add(typeName)

        // 3. Shortened versions by removing common prefixes
        val prefixesToTry = listOf("App", "UserContext", "Session", "Default", "Core")
        for (prefix in prefixesToTry) {
            if (typeName.startsWith(prefix) && typeName.length > prefix.length) {
                val shortened = typeName.removePrefix(prefix)
                names.add(shortened.replaceFirstChar { it.lowercase() })
            }
        }

        // 4. Common short names
        when {
            typeName.contains("LogManager") -> names.add("logManager")
            typeName.contains("Config") && !typeName.contains("Environment") -> names.add("config")
            typeName.contains("Manager") -> names.add("manager")
            typeName.contains("Service") -> names.add("service")
            typeName.contains("Factory") -> names.add("factory")
        }

        return names.distinct()
    }
}

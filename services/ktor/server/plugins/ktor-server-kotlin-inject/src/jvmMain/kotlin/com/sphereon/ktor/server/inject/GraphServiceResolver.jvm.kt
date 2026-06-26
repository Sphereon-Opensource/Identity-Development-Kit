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

import com.sphereon.core.api.log.Log
import kotlin.reflect.KClass

/**
 * JVM implementation of service resolution.
 *
 * Strategy:
 * 1. Check the [ServiceProvider] registry (no reflection at all).
 * 2. Fall back to Java reflection ([findServiceInClass]) — SVM-safe at the API level.
 *
 * Both paths are GraalVM-native-image safe. We deliberately do NOT use kotlin-reflect
 * (`kClass.memberProperties` / `memberFunctions`): kotlin-reflect is unsupported under
 * GraalVM SVM (touching it raises `AssertionError: Built-in class kotlin.Any is not found`),
 * and its mere presence on the runtime classpath rewires `kotlin.jvm.internal.Reflection`
 * to the SVM-incompatible `ReflectionFactoryImpl`. Keeping this resolver reflection-free
 * lets the artifact be excluded from the runtime classpath entirely.
 */
@Suppress("TooGenericExceptionCaught")
internal actual class GraphServiceResolver {
    private val logger = Log.app().withTag("GraphServiceResolver")

    actual fun <T : Any> findService(
        graph: Any,
        serviceType: KClass<T>,
    ): T? {
        return try {
            // FIRST: Check if graph implements ServiceProvider (GraalVM-friendly, no reflection!)
            if (graph is ServiceProvider) {
                graph.getService(serviceType)?.let { return it }
            }

            // SECOND: Try Java reflection (GraalVM compatible with proper config).
            // NOTE: there is no kotlin-reflect fallback by design (see class KDoc) — the
            // ServiceProvider registry + Java reflection cover the runtime, and excluding
            // kotlin-reflect from the classpath is required for the native image to boot.
            findServiceInClass(graph, graph.javaClass, serviceType)
        } catch (expected: Exception) {
            // Log the error for debugging
            val debug = System.getProperty("debug.service.resolver") == "true"
            if (debug) {
                logger.error("Error finding service ${serviceType.simpleName}: ${expected.message}", exception = expected)
            }
            null
        }
    }

    private fun <T : Any> findServiceInClass(
        graph: Any,
        javaClass: Class<*>,
        serviceType: KClass<T>,
    ): T? {
        val targetClass = serviceType.java
        val debug = System.getProperty("debug.service.resolver") == "true" // Set debug.service.resolver system property to true for debugging

        if (debug) {
            logger.debug("Looking for ${serviceType.simpleName} in ${javaClass.simpleName}")
            logger.debug("Target interface: ${targetClass.name}")
        }

        // kotlin-inject generates val properties as getter methods
        // The generated graph will have methods that return the service type
        // We need to find ANY method that returns our target type

        // IMPORTANT: We match by RETURN TYPE, not by name
        // kotlin-inject might generate different property names than the interface name

        // Search through all declared methods in current class
        for (method in javaClass.declaredMethods) {
            // Check if return type matches (exact match or subtype)
            if (!targetClass.isAssignableFrom(method.returnType)) {
                continue
            }

            // Check if it's a zero-parameter method (kotlin val property)
            if (method.parameterCount != 0) {
                continue
            }

            if (debug) {
                logger.debug("Found matching method: ${method.name} -> ${method.returnType.simpleName}")
            }

            try {
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return method.invoke(graph) as? T
            } catch (expected: Exception) {
                if (debug) {
                    val rootCause =
                        if (expected is java.lang.reflect.InvocationTargetException) {
                            expected.targetException
                        } else {
                            expected
                        }
                    logger.error(
                        "Failed to invoke ${method.name}: ${rootCause::class.simpleName}: ${rootCause.message}",
                        exception = rootCause,
                    )
                }
                continue
            }
        }

        // If not found in declared methods, check public methods (includes inherited)
        // This is important for kotlin-inject generated components that implement interfaces
        for (method in javaClass.methods) {
            if (!targetClass.isAssignableFrom(method.returnType)) {
                continue
            }

            if (method.parameterCount != 0) {
                continue
            }

            if (debug) {
                logger.debug("Found matching public method: ${method.name} -> ${method.returnType.simpleName}")
            }

            try {
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return method.invoke(graph) as? T
            } catch (expected: Exception) {
                if (debug) {
                    val rootCause =
                        if (expected is java.lang.reflect.InvocationTargetException) {
                            expected.targetException
                        } else {
                            expected
                        }
                    logger.error(
                        "Failed to invoke ${method.name}: ${rootCause::class.simpleName}: ${rootCause.message}",
                        exception = rootCause,
                    )
                }
                continue
            }
        }

        // If still not found, try superclass
        val superclass = javaClass.superclass
        if (superclass != null && superclass != Any::class.java) {
            return findServiceInClass(graph, superclass, serviceType)
        }

        return null
    }
}

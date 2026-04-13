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
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

/**
 * JVM implementation of service resolution.
 *
 * Strategy:
 * 1. Try Java reflection first (works in GraalVM native image if properly configured)
 * 2. Fall back to Kotlin reflection if available (JVM only, not in native image)
 *
 * This approach provides compatibility with both regular JVM and GraalVM native image.
 */
@Suppress("TooGenericExceptionCaught")
internal actual class GraphServiceResolver {
    actual fun <T : Any> findService(
        graph: Any,
        serviceType: KClass<T>,
    ): T? {
        return try {
            // FIRST: Check if graph implements ServiceProvider (GraalVM-friendly, no reflection!)
            if (graph is ServiceProvider) {
                graph.getService(serviceType)?.let { return it }
            }

            // SECOND: Try Java reflection (GraalVM compatible with proper config)
            findServiceInClass(graph, graph.javaClass, serviceType)
                ?: tryKotlinReflection(graph, serviceType) // LAST: Fallback to Kotlin reflection (JVM only)
        } catch (expected: Exception) {
            // Log the error for debugging
            val debug = System.getProperty("debug.service.resolver") == "true"
            if (debug) {
                println("[GraphServiceResolver] Error finding service ${serviceType.simpleName}: ${expected.message}")
                expected.printStackTrace()
            }
            null
        }
    }

    /**
     * Try using Kotlin reflection as fallback.
     * This won't work in GraalVM native image but provides compatibility on regular JVM.
     */
    private fun <T : Any> tryKotlinReflection(
        graph: Any,
        serviceType: KClass<T>,
    ): T? {
        return try {
            val kClass = graph::class

            // First, try to find a property that returns the service type
            val property =
                kClass.memberProperties.find { prop ->
                    prop.returnType.classifier == serviceType
                }

            if (property != null) {
                @Suppress("UNCHECKED_CAST")
                return property.call(graph) as? T
            }

            // If not found, try to find a zero-parameter function that returns the service type
            val function =
                kClass.memberFunctions.find { func ->
                    func.parameters.size == 1 && // Only the instance parameter
                        func.returnType.classifier == serviceType &&
                        (
                            func.name.startsWith("provide") ||
                                func.name.startsWith("get") ||
                                func.name.contains(serviceType.simpleName ?: "")
                        )
                }

            @Suppress("UNCHECKED_CAST")
            function?.call(graph) as? T
        } catch (_: Exception) {
            // Kotlin reflection not available (likely in native image)
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
            println("[GraphServiceResolver] Looking for ${serviceType.simpleName} in ${javaClass.simpleName}")
            println("[GraphServiceResolver] Target interface: ${targetClass.name}")
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
                println("[GraphServiceResolver] Found matching method: ${method.name} -> ${method.returnType.simpleName}")
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
                    println("[GraphServiceResolver] Failed to invoke ${method.name}: ${rootCause::class.simpleName}: ${rootCause.message}")
                    rootCause.printStackTrace()
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
                println("[GraphServiceResolver] Found matching public method: ${method.name} -> ${method.returnType.simpleName}")
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
                    println("[GraphServiceResolver] Failed to invoke ${method.name}: ${rootCause::class.simpleName}: ${rootCause.message}")
                    rootCause.printStackTrace()
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

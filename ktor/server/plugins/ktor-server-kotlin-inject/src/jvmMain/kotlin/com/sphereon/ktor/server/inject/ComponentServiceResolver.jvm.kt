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
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.memberFunctions

/**
 * JVM implementation of service resolution.
 *
 * Strategy:
 * 1. Try Java reflection first (works in GraalVM native image if properly configured)
 * 2. Fall back to Kotlin reflection if available (JVM only, not in native image)
 *
 * This approach provides compatibility with both regular JVM and GraalVM native image.
 */
internal actual class ComponentServiceResolver {

    actual fun <T : Any> findService(component: Any, serviceType: KClass<T>): T? {
        return try {
            // FIRST: Check if component implements ServiceProvider (GraalVM-friendly, no reflection!)
            if (component is ServiceProvider) {
                component.getService(serviceType)?.let { return it }
            }

            // SECOND: Try Java reflection (GraalVM compatible with proper config)
            findServiceInClass(component, component.javaClass, serviceType)
                ?: tryKotlinReflection(component, serviceType) // LAST: Fallback to Kotlin reflection (JVM only)
        } catch (e: Exception) {
            // Log the error for debugging
            val debug = System.getProperty("debug.service.resolver") == "true"
            if (debug) {
                println("[ComponentServiceResolver] Error finding service ${serviceType.simpleName}: ${e.message}")
                e.printStackTrace()
            }
            null
        }
    }

    /**
     * Try using Kotlin reflection as fallback.
     * This won't work in GraalVM native image but provides compatibility on regular JVM.
     */
    private fun <T : Any> tryKotlinReflection(component: Any, serviceType: KClass<T>): T? {
        return try {
            val kClass = component::class

            // First, try to find a property that returns the service type
            val property = kClass.memberProperties.find { prop ->
                prop.returnType.classifier == serviceType
            }

            if (property != null) {
                @Suppress("UNCHECKED_CAST")
                return property.call(component) as? T
            }

            // If not found, try to find a zero-parameter function that returns the service type
            val function = kClass.memberFunctions.find { func ->
                func.parameters.size == 1 && // Only the instance parameter
                        func.returnType.classifier == serviceType &&
                        (func.name.startsWith("provide") ||
                                func.name.startsWith("get") ||
                                func.name.contains(serviceType.simpleName ?: ""))
            }

            @Suppress("UNCHECKED_CAST")
            function?.call(component) as? T
        } catch (e: Exception) {
            // Kotlin reflection not available (likely in native image)
            null
        }
    }

    private fun <T : Any> findServiceInClass(component: Any, javaClass: Class<*>, serviceType: KClass<T>): T? {
        val targetClass = serviceType.java
        val debug = System.getProperty("debug.service.resolver") == "true" // Set debug.service.resolver system property to true for debugging

        if (debug) {
            println("[ComponentServiceResolver] Looking for ${serviceType.simpleName} in ${javaClass.simpleName}")
            println("[ComponentServiceResolver] Target interface: ${targetClass.name}")
        }

        // kotlin-inject generates val properties as getter methods
        // The generated component will have methods that return the service type
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
                println("[ComponentServiceResolver] Found matching method: ${method.name} -> ${method.returnType.simpleName}")
            }

            try {
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return method.invoke(component) as? T
            } catch (e: Exception) {
                if (debug) {
                    val rootCause = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
                    println("[ComponentServiceResolver] Failed to invoke ${method.name}: ${rootCause::class.simpleName}: ${rootCause.message}")
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
                println("[ComponentServiceResolver] Found matching public method: ${method.name} -> ${method.returnType.simpleName}")
            }

            try {
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                return method.invoke(component) as? T
            } catch (e: Exception) {
                if (debug) {
                    val rootCause = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
                    println("[ComponentServiceResolver] Failed to invoke ${method.name}: ${rootCause::class.simpleName}: ${rootCause.message}")
                    rootCause.printStackTrace()
                }
                continue
            }
        }

        // If still not found, try superclass
        val superclass = javaClass.superclass
        if (superclass != null && superclass != Any::class.java) {
            return findServiceInClass(component, superclass, serviceType)
        }

        return null
    }
}

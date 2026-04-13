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

package com.sphereon.core.api.conf

import kotlin.js.Json

/**
 * JavaScript implementation of the Env object that uses process.env to get environment variables in Node.js.
 * In browser environments, environment variables are typically not directly accessible.
 */
actual object Env {
    /**
     * Returns the value of the specified environment variable.
     * In Node.js, this uses process.env.
     * In browser environments, this will return null as environment variables are not directly accessible.
     */
    actual fun get(name: String): String? {
        return try {
            // Try to access process.env (Node.js)
            val processObj = js("typeof process !== 'undefined' ? process : undefined")
            if (processObj != undefined && processObj.env != undefined) {
                val env = processObj.env as Json
                val value = env[name]
                if (value != undefined) value.toString() else null
            } else {
                // In browser environments, environment variables are not directly accessible
                null
            }
        } catch (e: Throwable) {
            // Fallback to null if any error occurs
            null
        }
    }

    /**
     * Returns all environment variables as a map.
     * In Node.js, this uses process.env.
     * In browser environments, this will return an empty map as environment variables are not directly accessible.
     */
    actual fun getAll(): Map<String, String> {
        return try {
            // Try to access process.env (Node.js)
            val processObj = js("typeof process !== 'undefined' ? process : undefined")
            if (processObj != undefined && processObj.env != undefined) {
                val env = processObj.env as Json
                val result = mutableMapOf<String, String>()

                // Convert JS object to Kotlin Map
                js("Object.keys(env)").unsafeCast<Array<String>>().forEach { key ->
                    val value = env[key]
                    if (value != undefined) {
                        result[key] = value.toString()
                    }
                }

                result
            } else {
                // In browser environments, environment variables are not directly accessible
                emptyMap()
            }
        } catch (e: Throwable) {
            // Fallback to empty map if any error occurs
            emptyMap()
        }
    }
}

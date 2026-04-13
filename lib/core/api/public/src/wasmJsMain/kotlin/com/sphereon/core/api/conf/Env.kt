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

@file:Suppress("OPT_IN_USAGE")

package com.sphereon.core.api.conf

/**
 * Reads a single environment variable from Node.js process.env.
 * Returns null in browser environments where process is not defined.
 */
private fun getEnvVar(name: String): JsString? =
    js("(typeof process !== 'undefined' && process.env ? (process.env[name] !== undefined ? process.env[name] : null) : null)")

/**
 * Returns all Node.js process.env keys as a JsArray<JsString>.
 * Returns null in browser environments.
 */
private fun getEnvKeys(): JsArray<JsString>? =
    js("(typeof process !== 'undefined' && process.env ? Object.keys(process.env) : null)")

/**
 * wasmJs implementation of the Env object.
 * In Node.js environments, accesses process.env via js() interop.
 * In browser environments, returns null/empty as environment variables are not accessible.
 */
actual object Env {
    actual fun get(name: String): String? {
        return try {
            getEnvVar(name)?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    actual fun getAll(): Map<String, String> {
        return try {
            val keys = getEnvKeys() ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            for (i in 0 until keys.length) {
                val key = keys[i].toString()
                getEnvVar(key)?.let { result[key] = it.toString() }
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}

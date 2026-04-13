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

package com.sphereon.core.api.conf

/**
 * JVM implementation of the Env object that uses System.getenv() to get environment variables.
 */
actual object Env {
    /**
     * Returns the value of the specified environment variable.
     */
    actual fun get(name: String): String? = System.getenv(name)

    /**
     * Returns all environment variables as a map.
     */
    actual fun getAll(): Map<String, String> = System.getenv()
}

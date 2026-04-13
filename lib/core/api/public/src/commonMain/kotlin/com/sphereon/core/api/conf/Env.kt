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

/**
 * Provides access to environment variables.
 * This object acts as an abstraction for retrieving the value of environment variables
 * by their names, making it portable across different platforms.
 */
expect object Env {
    /**
     * Retrieves the value of a property corresponding to the specified name.
     *
     * @param name the name of the property to retrieve.
     * @return the value of the property if found, or null if the property does not exist.
     */
    fun get(name: String): String?

    /**
     * Retrieves all environment variables as a map.
     *
     * @return a map containing all environment variables, with variable names as keys and their values as values.
     */
    fun getAll(): Map<String, String>
}

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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Linux implementation of the Env object that uses platform.posix.getenv to get environment variables.
 */
@OptIn(ExperimentalForeignApi::class)
actual object Env {
    /**
     * Returns the value of the specified environment variable.
     */
    actual fun get(name: String): String? {
        val value = getenv(name) ?: return null
        return value.toKString()
    }

    /**
     * Returns all environment variables as a map.
     * 
     * Note: This is a simplified implementation that returns an empty map.
     * A proper implementation would use __environ from platform.posix, but it's not directly accessible
     * through the standard Kotlin/Native POSIX bindings.
     */
    @OptIn(ExperimentalForeignApi::class)
    actual fun getAll(): Map<String, String> {
        // Return an empty map as a placeholder
        // A proper implementation would use __environ from platform.posix
        // We adjust in property source to not use the resulting map
        return emptyMap()
    }
}

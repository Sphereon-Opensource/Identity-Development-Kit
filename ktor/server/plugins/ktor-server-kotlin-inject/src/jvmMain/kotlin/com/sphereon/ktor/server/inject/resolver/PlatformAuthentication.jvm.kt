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

package com.sphereon.ktor.server.inject.resolver

import io.ktor.server.application.*
import io.ktor.server.auth.*

/**
 * JVM implementation of platform-specific authentication resolution.
 * Uses Ktor's authentication plugin to extract principal if available.
 */
internal actual fun resolvePlatformAuthentication(call: ApplicationCall): String? {
    return try {
        val principal = call.principal<UserIdPrincipal>()
        principal?.name
    } catch (e: Exception) {
        // Authentication plugin not available or not configured
        null
    }
}

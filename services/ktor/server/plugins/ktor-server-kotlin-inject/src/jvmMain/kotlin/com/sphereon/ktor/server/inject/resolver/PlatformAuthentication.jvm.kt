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

@file:Suppress("TooGenericExceptionCaught") // Authentication resolution must handle missing auth plugin gracefully

package com.sphereon.ktor.server.inject.resolver

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.principal

/**
 * JVM implementation of platform-specific authentication resolution.
 * Uses Ktor's authentication plugin to extract principal if available.
 */
internal actual fun resolvePlatformAuthentication(call: ApplicationCall): String? =
    try {
        val principal = call.principal<UserIdPrincipal>()
        principal?.name
    } catch (_: Exception) {
        // Authentication plugin not available or not configured
        null
    }

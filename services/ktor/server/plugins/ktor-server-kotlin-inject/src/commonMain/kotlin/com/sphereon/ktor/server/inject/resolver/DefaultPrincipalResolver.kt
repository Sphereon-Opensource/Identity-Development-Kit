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

package com.sphereon.ktor.server.inject.resolver

import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.di.context.PrincipalInput
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Default principal resolver that extracts principal from HTTP header.
 *
 * Resolution order:
 * 1. Configured HTTP header (e.g., "X-User-ID")
 * 2. Platform-specific authentication principal (if available)
 * 3. Falls back to "anonymous"
 *
 * @property headerName The HTTP header name to extract principal from (default: "X-User-ID")
 */
class DefaultPrincipalResolver(
    private val headerName: String = "X-User-ID",
) : PrincipalResolver {
    override fun resolve(call: ApplicationCall): PrincipalInput {
        // Try to get from header first
        val headerPrincipal = call.request.header(headerName)
        if (headerPrincipal != null) {
            return DefaultPrincipalInputString(headerPrincipal)
        }

        // Try platform-specific authentication
        val authPrincipal = resolvePlatformAuthentication(call)
        if (authPrincipal != null) {
            return DefaultPrincipalInputString(authPrincipal)
        }

        // Fallback to anonymous
        return DefaultPrincipalInputString("anonymous")
    }
}

/**
 * Platform-specific authentication resolution.
 * Returns null if authentication is not available or not configured.
 */
internal expect fun resolvePlatformAuthentication(call: ApplicationCall): String?

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

import com.sphereon.di.context.PrincipalInput
import io.ktor.server.application.ApplicationCall

/**
 * Strategy interface for resolving principal (user) information from Ktor application calls.
 *
 * Implement this interface to provide custom principal resolution logic
 * (e.g., from JWT claims, Ktor authentication, OAuth token, etc.).
 *
 * **Default Implementation:**
 * [DefaultPrincipalResolver] uses the principal established by the platform's
 * validated JWT authentication, or the canonical anonymous principal when no
 * JWT was validated. It never reads identity from request headers.
 *
 * **Custom Implementation Example:**
 * ```kotlin
 * class JwtPrincipalResolver : PrincipalResolver {
 *     override fun resolve(call: ApplicationCall): PrincipalInput {
 *         val jwt = extractJwt(call)
 *         val userId = jwt.getClaim("sub")
 *         return DefaultPrincipalInputString(userId)
 *     }
 * }
 * ```
 *
 * @see DefaultPrincipalResolver
 */
interface PrincipalResolver {
    /**
     * Resolve principal input from the Ktor application call.
     *
     * @param call The Ktor application call
     * @return PrincipalInput containing user identification information
     */
    fun resolve(call: ApplicationCall): PrincipalInput
}

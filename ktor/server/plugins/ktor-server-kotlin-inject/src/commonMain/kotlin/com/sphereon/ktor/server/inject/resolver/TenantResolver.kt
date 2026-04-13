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

import com.sphereon.di.context.TenantInput
import io.ktor.server.application.*

/**
 * Strategy interface for resolving tenant information from Ktor application calls.
 *
 * Implement this interface to provide custom tenant resolution logic
 * (e.g., from JWT claims, subdomain, database lookup, etc.).
 *
 * **Default Implementation:**
 * [DefaultTenantResolver] extracts tenant ID from a configured HTTP header.
 *
 * **Custom Implementation Example:**
 * ```kotlin
 * class JwtTenantResolver : TenantResolver {
 *     override fun resolve(call: ApplicationCall): TenantInput {
 *         val jwt = extractJwt(call)
 *         val tenantId = jwt.getClaim("tenant_id")
 *         return DefaultTenantInputString(tenantId)
 *     }
 * }
 * ```
 *
 * @see DefaultTenantResolver
 */
interface TenantResolver {
    /**
     * Resolve tenant input from the Ktor application call.
     *
     * @param call The Ktor application call
     * @return TenantInput containing tenant identification information
     */
    fun resolve(call: ApplicationCall): TenantInput
}

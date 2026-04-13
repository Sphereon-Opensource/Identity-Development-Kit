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

import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.context.TenantInput
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Default tenant resolver that extracts tenant ID from HTTP header.
 *
 * If the header is not present, falls back to "default" tenant.
 *
 * @property headerName The HTTP header name to extract tenant ID from (default: "X-Tenant-ID")
 */
class DefaultTenantResolver(
    private val headerName: String = "X-Tenant-ID",
) : TenantResolver {
    override fun resolve(call: ApplicationCall): TenantInput {
        val tenantId = call.request.header(headerName) ?: "default"
        return DefaultTenantInputString(tenantId)
    }
}

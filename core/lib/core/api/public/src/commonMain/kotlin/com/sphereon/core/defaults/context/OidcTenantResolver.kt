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

package com.sphereon.core.defaults.context

import com.sphereon.di.Order
import com.sphereon.di.context.TenantInput
import com.sphereon.di.context.TenantResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves tenant from JWT/OIDC token claims.
 *
 * This resolver extracts tenant information from common JWT claims used by OIDC providers.
 * It runs at HIGH priority (30) to be checked before email-based and static resolvers.
 *
 * **Supported claim names** (checked in order):
 * - `tenant_id` - Common in multi-tenant OIDC setups
 * - `tid` - Azure AD tenant ID
 * - `tenantId` - Alternative casing
 * - `org_id` - Organization ID (Auth0, etc.)
 * - `organization_id` - Full organization ID name
 * - `tenant` - Generic tenant claim
 *
 * **Usage:**
 * This resolver is automatically registered via multibinding. When using
 * `TenantResolutionHandler.resolveTenant()` with a `JwtClaimsInput`, this
 * resolver will be considered based on its priority.
 *
 * ```kotlin
 * val jwtInput = JwtClaimsParser.toJwtClaimsInput(jwt)!!
 * val tenant = tenantResolutionHandler.resolveTenant(jwtInput)
 * ```
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<TenantResolver>())
class OidcTenantResolver : TenantResolver {
    override val order: Int = Order.HIGH.orderValue

    private val tenantClaimNames =
        listOf(
            "tenant_id",
            "tid",
            "tenantId",
            "org_id",
            "organization_id",
            "tenant",
        )

    /**
     * Returns true if the input is a [JwtClaimsInput] containing JWT claims.
     */
    override fun supports(tenantInput: TenantInput): Boolean = tenantInput is JwtClaimsInput

    /**
     * Extracts tenant ID from JWT claims.
     *
     * @param tenantInput The JWT claims input
     * @return The extracted tenant ID (trimmed, lowercase)
     * @throws IllegalArgumentException if no tenant claim is found
     */
    override suspend fun resolveTenant(tenantInput: TenantInput): String {
        val claims = (tenantInput as JwtClaimsInput).claims
        return tenantClaimNames
            .firstNotNullOfOrNull { claimName ->
                claims[claimName]?.extractStringValue()
            }?.trim()
            ?.lowercase()
            ?: throw IllegalArgumentException("No tenant claim found in JWT. Checked: $tenantClaimNames")
    }

    /**
     * Safely extracts string value from a JsonElement.
     */
    private fun JsonElement.extractStringValue(): String? =
        try {
            jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
}

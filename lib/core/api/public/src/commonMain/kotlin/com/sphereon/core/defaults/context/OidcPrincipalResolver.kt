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

package com.sphereon.core.defaults.context

import com.sphereon.di.Order
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.PrincipalResolver
import com.sphereon.di.context.TenantAware
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * Resolves principal from JWT/OIDC token claims.
 *
 * This resolver extracts the user/principal identity from standard OIDC claims.
 * It runs at HIGH priority (30) to be checked before email-based and static resolvers.
 *
 * **Supported claim names** (checked in order):
 * - `sub` - Standard OIDC subject claim (unique user identifier)
 * - `email` - User's email address (common fallback)
 * - `preferred_username` - Preferred username claim
 * - `user_id` - Alternative user ID claim
 * - `userId` - Alternative casing
 *
 * **Usage:**
 * This resolver is automatically registered via multibinding. When using
 * `PrincipalResolutionHandler.resolvePrincipal()` with a `JwtClaimsInput`,
 * this resolver will be considered based on its priority.
 *
 * ```kotlin
 * val jwtInput = JwtClaimsParser.toJwtClaimsInput(jwt)!!
 * val tenant = tenantResolutionHandler.resolveTenant(jwtInput)
 * val principal = principalResolutionHandler.resolvePrincipal(jwtInput, tenant)
 * ```
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PrincipalResolver>())
class OidcPrincipalResolver : PrincipalResolver {

    override val priority: Int = Order.HIGH.orderValue

    private val principalClaimNames = listOf(
        "sub",
        "email",
        "preferred_username",
        "user_id",
        "userId"
    )

    /**
     * Returns true if the input is a [JwtClaimsInput] containing JWT claims.
     */
    override fun supports(principalInput: PrincipalInput): Boolean {
        return principalInput is JwtClaimsInput
    }

    /**
     * Extracts principal ID from JWT claims.
     *
     * @param principalInput The JWT claims input
     * @param tenant The resolved tenant (available for tenant-scoped principal resolution)
     * @return The extracted principal ID (trimmed)
     * @throws IllegalArgumentException if no principal claim is found
     */
    override fun resolvePrincipal(principalInput: PrincipalInput, tenant: TenantAware): String {
        val claims = (principalInput as JwtClaimsInput).claims
        return principalClaimNames.firstNotNullOfOrNull { claimName ->
            claims[claimName]?.extractStringValue()
        }?.trim()
            ?: throw IllegalArgumentException("No principal claim found in JWT. Checked: $principalClaimNames")
    }

    /**
     * Safely extracts string value from a JsonElement.
     */
    private fun JsonElement.extractStringValue(): String? {
        return try {
            jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}

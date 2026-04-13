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

import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.TenantInput
import kotlinx.serialization.json.JsonElement
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Input type for tenant/principal resolution from JWT claims.
 *
 * This class supports both header-based AND token-based resolution simultaneously.
 * It implements both [TenantInput] and [PrincipalInput] allowing the same input
 * to be used with both tenant and principal resolvers.
 *
 * **Usage:**
 * ```kotlin
 * val claims = JwtClaimsParser.parseClaimsOrNull(jwt)
 * val input = JwtClaimsInput(claims = claims!!, rawToken = jwt)
 *
 * // Use with resolvers
 * val tenant = tenantResolutionHandler.resolveTenant(input)
 * val principal = principalResolutionHandler.resolvePrincipal(input, tenant)
 * ```
 *
 * @property claims Parsed JWT claims as a map of claim name to JSON value
 * @property rawToken The original raw JWT token string (optional, for audit/logging)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwtClaimsInput", exact = true)
data class JwtClaimsInput(
    val claims: Map<String, JsonElement>,
    val rawToken: String? = null,
) : TenantInput,
    PrincipalInput {
    /**
     * Returns the claims map for tenant resolution.
     */
    override val tenant: Any get() = claims

    /**
     * Returns the claims map for principal resolution.
     */
    override val principal: Any get() = claims

    override fun toString(): String = "JwtClaimsInput(claimKeys=${claims.keys}, hasRawToken=${rawToken != null})"
}

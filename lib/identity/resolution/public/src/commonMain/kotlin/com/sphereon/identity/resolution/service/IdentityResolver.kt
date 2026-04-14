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
 */

package com.sphereon.identity.resolution.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.identity.resolution.model.IdentityResolutionResult
import com.sphereon.identity.resolution.model.ResolverConfig

/**
 * Pluggable identity resolver. Multiple implementations registered via multibinding.
 * Each resolver is a strategy (matching, OIDC introspection, LDAP, etc.)
 * that can be enabled/disabled per tenant via [ResolverConfig].
 */
@JsExportCompat
interface IdentityResolver {
    /** Unique resolver ID (e.g., "identity-matching", "oidc-introspection") */
    val resolverId: String

    /** Default priority (higher = tried first) */
    val priority: Int get() = 0

    /** Whether this resolver supports the given tenant + config */
    suspend fun supports(
        tenantId: String,
        resolverConfig: ResolverConfig?,
    ): Boolean

    /**
     * Resolve identifier to an internal identity.
     * Returns Ok with `resolved=false` if this resolver can't resolve (next resolver tried).
     * Returns Err for hard failures that should stop the chain.
     */
    suspend fun resolve(
        identifier: String,
        tenantId: String,
        resolverConfig: ResolverConfig?,
    ): IdkResult<IdentityResolutionResult, IdkError>
}

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

import com.sphereon.core.api.auth.AuthHeaders
import com.sphereon.di.context.IdentityMetadata
import com.sphereon.di.context.IdentityResolutionInput
import com.sphereon.di.context.IdentityResolutionPipeline
import com.sphereon.di.context.IdentityResolutionResult
import com.sphereon.di.context.PrincipalResolver
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.ResolutionSource
import com.sphereon.di.context.TenantResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Default IDK identity resolution pipeline.
 *
 * Runs all registered [TenantResolver]s and [PrincipalResolver]s in priority order
 * and reports what was resolved and from where. This implementation is intentionally
 * simple — it has NO opinion about whether tokens beat headers. Security strategy
 * is EDK's job (see [SecureIdentityResolutionPipeline] in authzen-impl).
 *
 * When EDK is on the classpath, its secure pipeline replaces this binding.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultIdentityResolutionPipeline(
    tenantResolvers: Set<TenantResolver>,
    principalResolvers: Set<PrincipalResolver>,
) : IdentityResolutionPipeline {
    private val sortedTenantResolvers = tenantResolvers.sorted()
    private val sortedPrincipalResolvers = principalResolvers.sorted()

    override suspend fun resolve(input: IdentityResolutionInput): IdentityResolutionResult {
        val headerTenantId = input.headers[HEADER_TENANT_ID]?.trim()?.takeIf { it.isNotBlank() }
        val headerPrincipalId = input.headers[HEADER_PRINCIPAL_ID]?.trim()?.takeIf { it.isNotBlank() }
        val serviceId = input.headers[AuthHeaders.X_SERVICE_ID]?.trim()?.takeIf { it.isNotBlank() }

        // Build resolver inputs from available data
        val resolverInputs = buildResolverInputs(input, headerTenantId)

        // Resolve tenant: try all inputs against all resolvers in priority order
        val (tenantId, tenantSource) = resolveTenant(resolverInputs, input)

        // Resolve principal: try all inputs against all resolvers in priority order
        val (principalId, principalSource) = resolvePrincipal(resolverInputs, tenantId, input)

        // Determine primary resolution source (HEADER > TOKEN > HOST > PATH)
        val resolvedFrom =
            when {
                tenantSource == ResolutionSource.HEADER || principalSource == ResolutionSource.HEADER -> ResolutionSource.HEADER
                tenantSource == ResolutionSource.TOKEN || principalSource == ResolutionSource.TOKEN -> ResolutionSource.TOKEN
                tenantSource == ResolutionSource.HOST || principalSource == ResolutionSource.HOST -> ResolutionSource.HOST
                tenantSource == ResolutionSource.PATH || principalSource == ResolutionSource.PATH -> ResolutionSource.PATH
                else -> tenantSource
            }

        // Detect principal type (includes X-Service-Id detection for parity with SecureIdentityResolutionPipeline)
        val principalType = detectPrincipalType(principalId, input.tokenClaims, serviceId)

        // Extract metadata from token claims
        val issuer = input.tokenClaims?.get("iss")?.extractString()
        val audience = input.tokenClaims?.get("aud")?.extractString()

        return IdentityResolutionResult(
            tenantId = tenantId,
            principalId = principalId,
            principalType = principalType,
            metadata =
                IdentityMetadata(
                    issuer = issuer,
                    audience = audience,
                    resolvedFrom = resolvedFrom,
                    headerTenantId = headerTenantId,
                    headerPrincipalId = headerPrincipalId,
                ),
        )
    }

    private fun buildResolverInputs(
        input: IdentityResolutionInput,
        headerTenantId: String?,
    ): List<ResolverInput> =
        buildList {
            // Header-based input (highest priority — matches SecureIdentityResolutionPipeline)
            if (headerTenantId != null) {
                add(ResolverInput(DefaultTenantInputString(headerTenantId), ResolutionSource.HEADER))
            }
            // Token-based input
            val claims = input.tokenClaims
            if (claims != null) {
                add(ResolverInput(JwtClaimsInput(claims), ResolutionSource.TOKEN))
            }
            // Host-based input (subdomain tenancy)
            val host = input.hostHeader?.trim()?.takeIf { it.isNotBlank() }
            if (host != null) {
                add(ResolverInput(HostTenantInput(host), ResolutionSource.HOST))
            }
            // Path-based input (path-prefix tenancy)
            val path = input.pathPrefix?.trim()?.takeIf { it.isNotBlank() }
            if (path != null) {
                add(ResolverInput(PathTenantInput(path), ResolutionSource.PATH))
            }
        }

    private suspend fun resolveTenant(
        resolverInputs: List<ResolverInput>,
        input: IdentityResolutionInput,
    ): Pair<String?, ResolutionSource> {
        for (resolverInput in resolverInputs) {
            val tenantInput = resolverInput.input as? com.sphereon.di.context.TenantInput ?: continue
            for (resolver in sortedTenantResolvers) {
                if (resolver.supports(tenantInput)) {
                    return try {
                        val tenantId = resolver.resolveTenant(tenantInput)
                        tenantId to resolverInput.source
                    } catch (_: Exception) {
                        // Ignored: tenant resolver failed, trying next
                        continue
                    }
                }
            }
        }
        return null to ResolutionSource.UNKNOWN
    }

    private fun resolvePrincipal(
        resolverInputs: List<ResolverInput>,
        tenantId: String?,
        input: IdentityResolutionInput,
    ): Pair<String?, ResolutionSource> {
        val tenantAware =
            tenantId?.let {
                object : com.sphereon.di.context.TenantAware {
                    override val tenant = TenantContextDataImpl(it)
                }
            } ?: object : com.sphereon.di.context.TenantAware {
                override val tenant = TenantContextDataImpl("<unknown>")
            }

        // For principal resolution, also try header principal ID
        val headerPrincipalId = input.headers[HEADER_PRINCIPAL_ID]?.trim()?.takeIf { it.isNotBlank() }
        val principalInputs =
            buildList {
                for (resolverInput in resolverInputs) {
                    if (resolverInput.input is com.sphereon.di.context.PrincipalInput) {
                        add(resolverInput)
                    }
                }
                if (headerPrincipalId != null) {
                    add(ResolverInput(DefaultPrincipalInputString(headerPrincipalId), ResolutionSource.HEADER))
                }
            }

        for (resolverInput in principalInputs) {
            val principalInput = resolverInput.input as? com.sphereon.di.context.PrincipalInput ?: continue
            for (resolver in sortedPrincipalResolvers) {
                if (resolver.supports(principalInput)) {
                    return try {
                        val principalId = resolver.resolvePrincipal(principalInput, tenantAware)
                        principalId to resolverInput.source
                    } catch (_: Exception) {
                        // Ignored: principal resolver failed, trying next
                        continue
                    }
                }
            }
        }
        return null to ResolutionSource.UNKNOWN
    }

    internal fun detectPrincipalType(
        principalId: String?,
        tokenClaims: Map<String, JsonElement>?,
        serviceId: String? = null,
    ): PrincipalType {
        // X-Service-Id present → SERVICE takes highest priority (parity with SecureIdentityResolutionPipeline)
        if (serviceId != null) {
            return PrincipalType.SERVICE
        }

        if (principalId == null && tokenClaims == null) {
            return PrincipalType.ANONYMOUS
        }

        if (tokenClaims != null) {
            // client_credentials grant → WORKLOAD (no sub, or sub == client_id with no user-facing claims)
            val grantType = tokenClaims["grant_type"]?.extractString()
            if (grantType == "client_credentials") {
                return PrincipalType.WORKLOAD
            }

            // azp (authorized party) without sub often indicates workload
            val sub = tokenClaims["sub"]?.extractString()
            val azp = tokenClaims["azp"]?.extractString()
            if (sub != null && azp != null && sub == azp && !tokenClaims.containsKey("email")) {
                return PrincipalType.WORKLOAD
            }

            if (sub != null) {
                return PrincipalType.USER
            }
        }

        // Has a principal but no token → could be header-based service identity
        if (principalId != null) {
            return PrincipalType.SERVICE
        }

        return PrincipalType.ANONYMOUS
    }

    private fun JsonElement.extractString(): String? =
        try {
            jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Ignored: element is not a JSON primitive
            null
        }

    private data class ResolverInput(
        val input: Any,
        val source: ResolutionSource,
    )

    companion object {
        const val HEADER_TENANT_ID = "X-Tenant-Id"
        const val HEADER_PRINCIPAL_ID = "X-Principal-Id"
    }
}

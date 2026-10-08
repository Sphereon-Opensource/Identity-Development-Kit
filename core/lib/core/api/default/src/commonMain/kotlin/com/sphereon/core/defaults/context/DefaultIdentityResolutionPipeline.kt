/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.core.defaults.context

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
 * Default identity pipeline for IDK deployments.
 *
 * Tenant and principal authority comes exclusively from validated JWT claims.
 * Headers, hosts, and path segments remain observable transport metadata but
 * can never establish, fill, or override an authenticated identity.
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
        val claims = input.tokenClaims
        val jwtInput = claims?.let(::JwtClaimsInput)
        val tenantId = jwtInput?.let { resolveTenant(it) }
        val principalId = jwtInput?.let { resolvePrincipal(it, tenantId) }
        val resolvedFrom = if (claims != null && (tenantId != null || principalId != null)) {
            ResolutionSource.TOKEN
        } else {
            ResolutionSource.UNKNOWN
        }

        return IdentityResolutionResult(
            tenantId = tenantId,
            principalId = principalId,
            principalType = detectPrincipalType(principalId, claims),
            metadata = IdentityMetadata(
                issuer = claims?.get("iss")?.extractString(),
                audience = claims?.get("aud")?.extractString(),
                resolvedFrom = resolvedFrom,
            ),
        )
    }

    private suspend fun resolveTenant(input: JwtClaimsInput): String? {
        for (resolver in sortedTenantResolvers) {
            if (!resolver.supports(input)) continue
            try {
                return resolver.resolveTenant(input)
            } catch (_: Exception) {
                // Try the next JWT resolver. Never fall back to transport data.
            }
        }
        return null
    }

    private fun resolvePrincipal(input: JwtClaimsInput, tenantId: String?): String? {
        val tenantAware = object : com.sphereon.di.context.TenantAware {
            override val tenant = TenantContextDataImpl(tenantId ?: "<unknown>")
        }
        for (resolver in sortedPrincipalResolvers) {
            if (!resolver.supports(input)) continue
            try {
                return resolver.resolvePrincipal(input, tenantAware)
            } catch (_: Exception) {
                // Try the next JWT resolver. Never fall back to transport data.
            }
        }
        return null
    }

    internal fun detectPrincipalType(
        principalId: String?,
        tokenClaims: Map<String, JsonElement>?,
    ): PrincipalType {
        if (tokenClaims == null) return PrincipalType.ANONYMOUS
        if (tokenClaims["grant_type"]?.extractString() == "client_credentials") return PrincipalType.WORKLOAD

        val sub = tokenClaims["sub"]?.extractString()
        val azp = tokenClaims["azp"]?.extractString()
        if (sub != null && azp != null && sub == azp && !tokenClaims.containsKey("email")) {
            return PrincipalType.WORKLOAD
        }
        return if (principalId != null || sub != null) PrincipalType.USER else PrincipalType.ANONYMOUS
    }

    private fun JsonElement.extractString(): String? =
        runCatching { jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }.getOrNull()

}

package com.sphereon.oauth2.server.authorization.impl.oidc

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Maps OIDC scopes to their corresponding claim sets per OpenID Connect Core Section 5.4
 */
interface OidcScopeClaimsMapper {
    fun claimsForScopes(scopes: Set<String>): Set<String>
    fun filterClaims(userClaims: Map<String, Any>, scopes: Set<String>): Map<String, Any>
    fun allStandardClaimKeys(): Set<String>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OidcScopeClaimsMapper>())
class OidcScopeClaimsMapperImpl : OidcScopeClaimsMapper {

    companion object {
        private val SCOPE_CLAIMS = mapOf(
            "openid" to setOf("sub"),
            "profile" to setOf(
                "name", "family_name", "given_name", "middle_name",
                "nickname", "preferred_username", "profile", "picture",
                "website", "gender", "birthdate", "zoneinfo", "locale", "updated_at"
            ),
            "email" to setOf("email", "email_verified"),
            "address" to setOf("address"),
            "phone" to setOf("phone_number", "phone_number_verified")
        )
    }

    override fun claimsForScopes(scopes: Set<String>): Set<String> {
        return scopes.flatMap { scope -> SCOPE_CLAIMS[scope] ?: emptySet() }.toSet()
    }

    override fun filterClaims(userClaims: Map<String, Any>, scopes: Set<String>): Map<String, Any> {
        val allowedClaims = claimsForScopes(scopes)
        return userClaims.filterKeys { it in allowedClaims }
    }

    override fun allStandardClaimKeys(): Set<String> {
        return SCOPE_CLAIMS.values.flatten().toSet()
    }
}

/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.oidc

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Maps OIDC scopes to their corresponding claim sets per OpenID Connect Core Section 5.4
 */
interface OidcScopeClaimsMapper {
    fun claimsForScopes(scopes: Set<String>): Set<String>

    fun filterClaims(
        userClaims: Map<String, Any>,
        scopes: Set<String>,
    ): Map<String, Any>

    fun allStandardClaimKeys(): Set<String>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OidcScopeClaimsMapper>())
class OidcScopeClaimsMapperImpl : OidcScopeClaimsMapper {
    override fun claimsForScopes(scopes: Set<String>): Set<String> = scopes.flatMap { scope -> SCOPE_CLAIMS[scope] ?: emptySet() }.toSet()

    override fun filterClaims(
        userClaims: Map<String, Any>,
        scopes: Set<String>,
    ): Map<String, Any> {
        val allowedClaims = claimsForScopes(scopes)
        return userClaims.filterKeys { it in allowedClaims }
    }

    override fun allStandardClaimKeys(): Set<String> = SCOPE_CLAIMS.values.flatten().toSet()

    companion object {
        private val SCOPE_CLAIMS =
            mapOf(
                "openid" to setOf("sub"),
                "profile" to
                    setOf(
                        "name",
                        "family_name",
                        "given_name",
                        "middle_name",
                        "nickname",
                        "preferred_username",
                        "profile",
                        "picture",
                        "website",
                        "gender",
                        "birthdate",
                        "zoneinfo",
                        "locale",
                        "updated_at",
                    ),
                "email" to setOf("email", "email_verified"),
                "address" to setOf("address"),
                "phone" to setOf("phone_number", "phone_number_verified"),
            )
    }
}

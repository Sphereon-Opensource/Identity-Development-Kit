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

package com.sphereon.openid.oid4vp.auth.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Result of a successful OID4VP authentication.
 *
 * @property userId The resolved user ID (becomes 'sub' claim in tokens).
 * @property claims Mapped OIDC claims from the credentials (raw key-value pairs).
 *                  When [claimSource] is [ClaimSource.CANONICAL_BINDING], these are the
 *                  reconciled canonical claims from the identity link binding. Otherwise,
 *                  they are wallet-only mapped claims.
 * @property jwtClaims JWT claims payload as JSON string, built using JwtPayload from the
 *                     crypto-jose module. Contains standard OIDC claims (sub, iat, auth_time,
 *                     acr, amr) plus mapped claims from verifiable credentials. The OAuth2
 *                     Authorization Server (e.g., Keycloak) is responsible for adding
 *                     issuer/audience/expiration and signing this payload when creating
 *                     the actual ID token.
 * @property isNewUser Whether the user was newly created during this authentication.
 * @property authenticatedAt Timestamp of successful authentication (becomes 'auth_time').
 * @property acr Authentication Context Class Reference. When claims come from a reconciled
 *               binding with assurance metadata, this reflects the OIDC provider's ACR.
 *               Defaults to "urn:sphereon:oid4vp:vp" for wallet-only flows.
 * @property amr Authentication Methods References. When claims come from a reconciled
 *               binding with assurance metadata, this reflects the OIDC provider's AMR.
 *               Defaults to ["vp"] for wallet-only flows.
 * @property claimSource Indicates whether claims originate from a canonical binding or wallet-only mapping.
 */
@Serializable
data class Oid4vpAuthResult(
    @SerialName("user_id")
    val userId: String,

    val claims: Map<String, JsonElement>,

    @SerialName("jwt_claims")
    val jwtClaims: String? = null,

    @SerialName("is_new_user")
    val isNewUser: Boolean,

    @SerialName("authenticated_at")
    val authenticatedAt: Instant,

    val acr: String = DEFAULT_ACR,

    val amr: List<String> = DEFAULT_AMR,

    @SerialName("claim_source")
    val claimSource: ClaimSource = ClaimSource.WALLET_ONLY
) {
    companion object {
        /**
         * Default Authentication Context Class Reference for OID4VP.
         */
        const val DEFAULT_ACR = "urn:sphereon:oid4vp:vp"

        /**
         * Default Authentication Methods References for OID4VP.
         */
        val DEFAULT_AMR = listOf("vp")

        /**
         * Authentication Context Class Reference for reconciled (IDV) sessions.
         */
        const val RECONCILED_ACR = "urn:sphereon:oid4vp:vp+idv"

        /**
         * Default Authentication Methods References for reconciled (IDV) sessions.
         */
        val RECONCILED_AMR = listOf("vp", "idv")
    }
}

/**
 * Indicates the origin of claims in an [Oid4vpAuthResult].
 */
@Serializable
enum class ClaimSource {
    /** Claims were mapped directly from wallet credential presentation only. */
    @SerialName("wallet_only")
    WALLET_ONLY,

    /** Claims are canonical claims from a reconciled identity link binding (wallet + OIDC merged). */
    @SerialName("canonical_binding")
    CANONICAL_BINDING
}

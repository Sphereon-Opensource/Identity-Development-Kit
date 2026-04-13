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

package com.sphereon.openid.oid4vci.holder.dsl

import com.sphereon.openid.oid4vci.common.dsl.AuthorizationDetailsBuilder
import com.sphereon.openid.oid4vci.common.dsl.IaeInteractionType
import com.sphereon.openid.oid4vci.common.dsl.Oid4vciDsl
import com.sphereon.openid.oid4vci.common.dsl.PkceBuilder
import com.sphereon.openid.oid4vci.holder.FollowUpIaeArgs
import com.sphereon.openid.oid4vci.holder.InitiateIaeArgs
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Top-level entry points
// ---------------------------------------------------------------------------

/**
 * Build an [InitiateIaeArgs] using the DSL.
 *
 * OID4VCI 1.1 Section 6.1.1 — Initial IAE Request (holder/wallet side).
 *
 * Example:
 * ```kotlin
 * val args = initiateIaeArgs {
 *     iaeEndpoint("https://as.example.com/iae")
 *     clientId("wallet-app")
 *     redirectUri("https://wallet.example.com/callback")
 *
 *     interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION, IaeInteractionType.REDIRECT_TO_WEB)
 *
 *     pkce {
 *         codeChallenge("E9Melhoa2...")
 *         codeChallengeMethod("S256")
 *     }
 *
 *     authorizationDetails {
 *         openidCredential("UniversityDegree")
 *         openidCredential("MembershipCard")
 *     }
 * }
 * ```
 */
fun initiateIaeArgs(builder: InitiateIaeArgsBuilder.() -> Unit): InitiateIaeArgs = InitiateIaeArgsBuilder().apply(builder).build()

/**
 * Build a [FollowUpIaeArgs] using the DSL.
 *
 * OID4VCI 1.1 Section 6.3 — Follow-up IAE Request (holder/wallet side).
 *
 * Example:
 * ```kotlin
 * val args = followUpIaeArgs {
 *     iaeEndpoint("https://as.example.com/iae")
 *     authSession("wxroVrBY2MCq4dDNGXACS")
 *     vpResponse(vpResponseJsonObject)
 * }
 * ```
 */
fun followUpIaeArgs(builder: FollowUpIaeArgsBuilder.() -> Unit): FollowUpIaeArgs = FollowUpIaeArgsBuilder().apply(builder).build()

// ---------------------------------------------------------------------------
// InitiateIaeArgsBuilder
// ---------------------------------------------------------------------------

/**
 * Builder for [InitiateIaeArgs].
 *
 * At least one interaction type must be provided via [interactionTypes].
 */
@Oid4vciDsl
class InitiateIaeArgsBuilder {
    private var iaeEndpoint: String? = null
    private var clientId: String? = null
    private var redirectUri: String? = null
    private val interactionTypeUrns = mutableListOf<String>()
    private var authorizationDetails: List<kotlinx.serialization.json.JsonElement>? = null
    private var scope: String? = null
    private var codeChallenge: String? = null
    private var codeChallengeMethod: String? = null

    /** URL of the AS IAE endpoint (POST /iae). */
    fun iaeEndpoint(url: String) {
        iaeEndpoint = url
    }

    /** OAuth2 client_id of the wallet/holder. */
    fun clientId(id: String) {
        clientId = id
    }

    /** Redirect URI registered for the client. */
    fun redirectUri(uri: String) {
        redirectUri = uri
    }

    /**
     * Set the ordered list of interaction type URNs the wallet supports.
     *
     * The URNs are sent as a comma-separated string per OID4VCI 1.1 Section 6.1.1.
     * At least one type must be provided.
     *
     * @param types One or more [IaeInteractionType] values.
     */
    fun interactionTypes(vararg types: IaeInteractionType) {
        require(types.isNotEmpty()) { "At least one interaction type must be provided" }
        interactionTypeUrns.clear()
        interactionTypeUrns.addAll(types.map { it.urn })
    }

    /**
     * Configure PKCE via the [PkceBuilder] DSL.
     *
     * Sets both [codeChallenge] and [codeChallengeMethod] in one block.
     * Required when [IaeInteractionType.REDIRECT_TO_WEB] is among the supported types.
     */
    fun pkce(builder: PkceBuilder.() -> Unit) {
        val pkce = PkceBuilder().apply(builder).build()
        codeChallenge = pkce.codeChallenge
        codeChallengeMethod = pkce.codeChallengeMethod
    }

    /**
     * Shorthand to set PKCE inline without a nested builder.
     *
     * @param challenge The code_challenge value.
     * @param method The code_challenge_method (default: "S256").
     */
    fun pkce(
        challenge: String,
        method: String = "S256",
    ) {
        codeChallenge = challenge
        codeChallengeMethod = method
    }

    /**
     * Configure RFC 9396 authorization_details via the [AuthorizationDetailsBuilder] DSL.
     *
     * Mutually exclusive with [scope]; at most one should be set.
     */
    fun authorizationDetails(builder: AuthorizationDetailsBuilder.() -> Unit) {
        authorizationDetails = AuthorizationDetailsBuilder().apply(builder).build()
    }

    /**
     * Set the OAuth2 scope string.
     *
     * Mutually exclusive with [authorizationDetails]; at most one should be set.
     */
    fun scope(value: String) {
        scope = value
    }

    internal fun build(): InitiateIaeArgs {
        require(interactionTypeUrns.isNotEmpty()) {
            "At least one interaction type must be provided via interactionTypes()"
        }
        return InitiateIaeArgs(
            iaeEndpoint = requireNotNull(iaeEndpoint) { "iaeEndpoint must be set" },
            clientId = requireNotNull(clientId) { "clientId must be set" },
            redirectUri = requireNotNull(redirectUri) { "redirectUri must be set" },
            interactionTypesSupported = interactionTypeUrns.toList(),
            authorizationDetails = authorizationDetails,
            scope = scope,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
        )
    }
}

// ---------------------------------------------------------------------------
// FollowUpIaeArgsBuilder
// ---------------------------------------------------------------------------

/**
 * Builder for [FollowUpIaeArgs].
 *
 * Either [vpResponse] (for an openid4vp_presentation follow-up) or
 * [codeVerifier] (for a redirect_to_web follow-up) should be set,
 * but the spec allows both to be absent in edge cases.
 */
@Oid4vciDsl
class FollowUpIaeArgsBuilder {
    private var iaeEndpoint: String? = null
    private var authSession: String? = null
    private var openid4vpResponse: JsonObject? = null
    private var codeVerifier: String? = null

    /** URL of the AS IAE endpoint (POST /iae). */
    fun iaeEndpoint(url: String) {
        iaeEndpoint = url
    }

    /**
     * The auth_session token returned by the most recent IAE response.
     *
     * Rotates on every response; must not be reused across round-trips.
     */
    fun authSession(token: String) {
        authSession = token
    }

    /**
     * Set the OpenID4VP VP Token response object.
     *
     * Present when responding to an [IaeInteractionType.OPENID4VP_PRESENTATION] challenge.
     */
    fun vpResponse(response: JsonObject) {
        openid4vpResponse = response
    }

    /**
     * Set the PKCE code_verifier.
     *
     * Supplied when responding to a [IaeInteractionType.REDIRECT_TO_WEB] challenge,
     * if PKCE verification is performed inline at the IAE endpoint (rather than deferred
     * to the token endpoint).
     */
    fun codeVerifier(verifier: String) {
        codeVerifier = verifier
    }

    internal fun build(): FollowUpIaeArgs =
        FollowUpIaeArgs(
            iaeEndpoint = requireNotNull(iaeEndpoint) { "iaeEndpoint must be set" },
            authSession = requireNotNull(authSession) { "authSession must be set" },
            openid4vpResponse = openid4vpResponse,
            codeVerifier = codeVerifier,
        )
}

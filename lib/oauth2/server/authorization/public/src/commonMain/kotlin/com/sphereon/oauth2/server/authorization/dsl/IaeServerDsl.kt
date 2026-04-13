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

package com.sphereon.oauth2.server.authorization.dsl

import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestArgs
import com.sphereon.openid.oid4vci.common.dsl.AuthorizationDetailsBuilder
import com.sphereon.openid.oid4vci.common.dsl.IaeInteractionType
import com.sphereon.openid.oid4vci.common.dsl.Oid4vciDsl
import com.sphereon.openid.oid4vci.common.dsl.PkceBuilder
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Top-level entry points
// ---------------------------------------------------------------------------

/**
 * Build a [HandleIaeInitialRequestArgs] using the DSL.
 *
 * OID4VCI 1.1 Section 6.2 — Initial IAE Request (Authorization Server side).
 *
 * Example:
 * ```kotlin
 * val args = handleIaeInitialArgs {
 *     clientId("wallet-app")
 *     redirectUri("https://wallet.example.com/callback")
 *     interactionTypes(IaeInteractionType.OPENID4VP_PRESENTATION)
 *
 *     pkce {
 *         codeChallenge("E9Melhoa2...")
 *         codeChallengeMethod("S256")
 *     }
 *
 *     authorizationDetails {
 *         openidCredential("UniversityDegree")
 *     }
 *
 *     issuerState("state-from-offer")
 *     signedRequest("eyJ...")
 * }
 * ```
 */
fun handleIaeInitialArgs(builder: HandleIaeInitialRequestArgsBuilder.() -> Unit): HandleIaeInitialRequestArgs = HandleIaeInitialRequestArgsBuilder().apply(builder).build()

/**
 * Build a [HandleIaeFollowUpArgs] using the DSL.
 *
 * OID4VCI 1.1 Section 6.3 — Follow-up IAE Request (Authorization Server side).
 *
 * Example:
 * ```kotlin
 * val args = handleIaeFollowUpArgs {
 *     authSession("wxroVrBY2MCq4dDNGXACS")
 *     vpResponse(vpResponseJsonObject)
 * }
 * ```
 */
fun handleIaeFollowUpArgs(builder: HandleIaeFollowUpArgsBuilder.() -> Unit): HandleIaeFollowUpArgs = HandleIaeFollowUpArgsBuilder().apply(builder).build()

// ---------------------------------------------------------------------------
// HandleIaeInitialRequestArgsBuilder
// ---------------------------------------------------------------------------

/**
 * Builder for [HandleIaeInitialRequestArgs].
 *
 * The [responseType] field defaults to `"code"` per OID4VCI 1.1 Section 6.2.
 * At least one interaction type must be provided via [interactionTypes].
 */
@Oid4vciDsl
class HandleIaeInitialRequestArgsBuilder {
    private var clientId: String? = null

    /** The response_type; defaults to "code" per OID4VCI 1.1 Section 6.2. */
    var responseType: String = "code"

    private var redirectUri: String? = null
    private val interactionTypeUrns = mutableListOf<String>()
    private var authorizationDetails: List<kotlinx.serialization.json.JsonElement>? = null
    private var scope: String? = null
    private var codeChallenge: String? = null
    private var codeChallengeMethod: String? = null
    private var issuerState: String? = null
    private var request: String? = null

    /** OAuth2 client_id of the requesting wallet/holder. */
    fun clientId(id: String) {
        clientId = id
    }

    /** Redirect URI registered for the client. */
    fun redirectUri(uri: String) {
        redirectUri = uri
    }

    /**
     * Set the ordered list of interaction type URNs the client supports.
     *
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

    /**
     * Set the credential offer issuer_state, forwarded from the OID4VCI offer if present.
     */
    fun issuerState(state: String) {
        issuerState = state
    }

    /**
     * Set the signed request object (JAR, RFC 9101).
     *
     * When present, authorization parameters are extracted from the JWT and override
     * any individually supplied form parameters.
     */
    fun signedRequest(jar: String) {
        request = jar
    }

    internal fun build(): HandleIaeInitialRequestArgs {
        require(interactionTypeUrns.isNotEmpty()) {
            "At least one interaction type must be provided via interactionTypes()"
        }
        return HandleIaeInitialRequestArgs(
            clientId = requireNotNull(clientId) { "clientId must be set" },
            responseType = responseType,
            redirectUri = requireNotNull(redirectUri) { "redirectUri must be set" },
            interactionTypesSupported = interactionTypeUrns.toList(),
            authorizationDetails = authorizationDetails,
            scope = scope,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            issuerState = issuerState,
            request = request,
        )
    }
}

// ---------------------------------------------------------------------------
// HandleIaeFollowUpArgsBuilder
// ---------------------------------------------------------------------------

/**
 * Builder for [HandleIaeFollowUpArgs].
 *
 * Either [vpResponse] (for openid4vp_presentation follow-up) or
 * [codeVerifier] (for redirect_to_web follow-up) should typically be set,
 * but both are optional per spec.
 */
@Oid4vciDsl
class HandleIaeFollowUpArgsBuilder {
    private var authSession: String? = null
    private var openid4vpResponse: JsonObject? = null
    private var codeVerifier: String? = null

    /**
     * The auth_session token from the most recent IAE server response.
     *
     * Rotates on every response; must not be reused across round-trips.
     */
    fun authSession(token: String) {
        authSession = token
    }

    /**
     * Set the OpenID4VP VP Token response object.
     *
     * Present when the client is responding to an [IaeInteractionType.OPENID4VP_PRESENTATION]
     * challenge.
     */
    fun vpResponse(response: JsonObject) {
        openid4vpResponse = response
    }

    /**
     * Set the PKCE code_verifier.
     *
     * Supplied when the AS verifies PKCE inline at the IAE endpoint (rather than deferred
     * to the token endpoint). Associated with [IaeInteractionType.REDIRECT_TO_WEB] flow.
     */
    fun codeVerifier(verifier: String) {
        codeVerifier = verifier
    }

    internal fun build(): HandleIaeFollowUpArgs =
        HandleIaeFollowUpArgs(
            authSession = requireNotNull(authSession) { "authSession must be set" },
            openid4vpResponse = openid4vpResponse,
            codeVerifier = codeVerifier,
        )
}

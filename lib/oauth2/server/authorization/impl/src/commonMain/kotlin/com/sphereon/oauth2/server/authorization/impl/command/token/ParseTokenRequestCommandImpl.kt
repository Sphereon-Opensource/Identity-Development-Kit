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
 */

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.defaults.context.JwtClaimsParser
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.ClientAssertion
import com.sphereon.oauth2.common.model.ClientAttestation
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlinx.serialization.json.jsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of ParseTokenRequestCommand
 *
 * Parses OAuth2 token endpoint requests according to RFC 6749 Section 4.
 *
 * Supported grant types:
 * - authorization_code (RFC 6749 Section 4.1.3)
 * - refresh_token (RFC 6749 Section 6)
 * - client_credentials (RFC 6749 Section 4.4)
 * - token-exchange (RFC 8693)
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseTokenRequestCommandImpl", exact = true)
class ParseTokenRequestCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseTokenRequestArgs, TokenRequestData>(
    commandId = ParseTokenRequestCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ParseTokenRequestArgs>(),
    outputTypeToken = typeToken<TokenRequestData>(),
), ParseTokenRequestCommand {

    override val commandId: String get() = ParseTokenRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseTokenRequestArgs

    override suspend fun doExecute(
        args: ParseTokenRequestArgs,
        applyDuring: (ParseTokenRequestArgs) -> ParseTokenRequestArgs
    ): IdkResult<TokenRequestData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.requestBody, applied.requestHeaders).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        requestBody: Map<String, List<String>>,
        requestHeaders: Map<String, String>
    ): IdkResult<TokenRequestData, AuthorizationServerError> {
        // Extract grant_type (REQUIRED)
        val grantTypeString = requestBody["grant_type"]?.firstOrNull()
        if (grantTypeString.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: grant_type",
                    exception = null
                )
            )
        }

        // Parse grant type to enum
        val grantType = when (grantTypeString) {
            "authorization_code" -> GrantType.AUTHORIZATION_CODE
            "refresh_token" -> GrantType.REFRESH_TOKEN
            "client_credentials" -> GrantType.CLIENT_CREDENTIALS
            "urn:ietf:params:oauth:grant-type:pre-authorized_code" -> GrantType.PRE_AUTHORIZED_CODE
            "urn:ietf:params:oauth:grant-type:token-exchange" -> GrantType.TOKEN_EXCHANGE
            else -> return Err(
                AuthorizationServerError.UnsupportedGrantType(
                    grantType = grantTypeString
                )
            )
        }

        // Extract attestation headers (draft-ietf-oauth-attestation-based-client-auth)
        val attestationHeader = requestHeaders["OAuth-Client-Attestation"]
            ?: requestHeaders["oauth-client-attestation"]
        val attestationPopHeader = requestHeaders["OAuth-Client-Attestation-PoP"]
            ?: requestHeaders["oauth-client-attestation-pop"]

        // Extract JWT assertion from body (RFC 7523)
        val assertionType = requestBody["client_assertion_type"]?.firstOrNull()
        val assertion = requestBody["client_assertion"]?.firstOrNull()

        // Extract client credentials from headers if present (Basic Auth)
        val authHeader = requestHeaders["Authorization"] ?: requestHeaders["authorization"]
        val (clientIdFromAuth, clientSecretFromAuth) = parseBasicAuth(authHeader)

        // Extract client_id from body (for public clients or if not in header)
        val clientIdFromBody = requestBody["client_id"]?.firstOrNull()
        val clientSecretFromBody = requestBody["client_secret"]?.firstOrNull()

        // Determine final client_id and client_secret
        // Prefer header over body (per OAuth2 spec)
        val clientId = clientIdFromAuth ?: clientIdFromBody
        val clientSecret = clientSecretFromAuth ?: clientSecretFromBody

        // Build client authentication config
        // Priority: attestation headers > JWT assertion > Basic auth > Post auth > None > Anonymous
        val clientAuthentication = when {
            // 1. Attestation headers (highest priority)
            attestationHeader != null && attestationPopHeader != null ->
                ClientAuthenticationConfig.AttestationJwt(
                    ClientAttestation(attestationHeader, attestationPopHeader)
                )
            // 2. JWT assertion in body (RFC 7523)
            assertionType != null && assertion != null -> {
                val assertionClientId = clientId ?: ""
                when (assertionType) {
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" ->
                        ClientAuthenticationConfig.PrivateKeyJwt(
                            ClientAssertion(assertionClientId, assertionType, assertion)
                        )
                    else ->
                        ClientAuthenticationConfig.SecretJwt(
                            ClientAssertion(assertionClientId, assertionType, assertion)
                        )
                }
            }
            // 3. Basic auth (existing)
            clientIdFromAuth != null && clientSecretFromAuth != null ->
                ClientAuthenticationConfig.Basic(ClientCredentials(clientIdFromAuth, clientSecretFromAuth))
            // 4. Post auth (existing)
            clientId != null && clientSecret != null ->
                ClientAuthenticationConfig.Post(ClientCredentials(clientId, clientSecret))
            // 5. None (public client)
            clientId != null ->
                ClientAuthenticationConfig.None(clientId)
            // 6. Anonymous
            else ->
                ClientAuthenticationConfig.Anonymous
        }

        // For attestation auth, extract client_id from attestation JWT sub claim if not in body
        val resolvedClientId = if (clientAuthentication is ClientAuthenticationConfig.AttestationJwt && clientId == null) {
            extractClientIdFromAttestationJwt(attestationHeader)
        } else {
            clientId
        }

        // Extract DPoP JWT from header if present (RFC 9449)
        val dpopProof = requestHeaders["DPoP"] ?: requestHeaders["dpop"]

        // Get HTTP URL from request (needed for DPoP verification)
        // TODO: This should be passed from the caller
        val httpUrl = "https://example.com/token"  // Placeholder

        // Parse based on grant type
        val grantParameters = when (grantType) {
            GrantType.AUTHORIZATION_CODE -> parseAuthorizationCodeGrant(requestBody)
            GrantType.REFRESH_TOKEN -> parseRefreshTokenGrant(requestBody)
            GrantType.CLIENT_CREDENTIALS -> parseClientCredentialsGrant(requestBody)
            GrantType.TOKEN_EXCHANGE -> parseTokenExchangeGrant(requestBody)
            else -> return Err(
                AuthorizationServerError.UnsupportedGrantType(
                    grantType = grantTypeString
                )
            )
        }

        val finalClientId = resolvedClientId ?: clientId
        if (finalClientId == null && clientAuthentication !is ClientAuthenticationConfig.Anonymous) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: client_id",
                    exception = null
                )
            )
        }

        return Ok(
            TokenRequestData(
                grantType = grantType,
                clientId = finalClientId ?: "",
                clientAuthentication = clientAuthentication,
                grantParameters = grantParameters,
                dpopProof = dpopProof,
                httpMethod = "POST",
                httpUrl = httpUrl
            )
        )
    }

    /**
     * Parse authorization_code grant request (RFC 6749 Section 4.1.3)
     */
    private fun parseAuthorizationCodeGrant(
        requestBody: Map<String, List<String>>
    ): GrantParameters {
        val code = requestBody["code"]?.firstOrNull() ?: ""
        val redirectUri = requestBody["redirect_uri"]?.firstOrNull() ?: ""
        val codeVerifier = requestBody["code_verifier"]?.firstOrNull()

        return GrantParameters.AuthorizationCode(
            code = code,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier
        )
    }

    /**
     * Parse refresh_token grant request (RFC 6749 Section 6)
     */
    private fun parseRefreshTokenGrant(
        requestBody: Map<String, List<String>>
    ): GrantParameters {
        val refreshToken = requestBody["refresh_token"]?.firstOrNull() ?: ""
        val scope = requestBody["scope"]?.firstOrNull()

        return GrantParameters.RefreshToken(
            refreshToken = refreshToken,
            scope = scope
        )
    }

    /**
     * Parse client_credentials grant request (RFC 6749 Section 4.4)
     */
    private fun parseClientCredentialsGrant(
        requestBody: Map<String, List<String>>
    ): GrantParameters {
        val scope = requestBody["scope"]?.firstOrNull()

        return GrantParameters.ClientCredentials(
            scope = scope
        )
    }

    /**
     * Parse token exchange grant request (RFC 8693)
     */
    private fun parseTokenExchangeGrant(
        requestBody: Map<String, List<String>>
    ): GrantParameters {
        val subjectToken = requestBody["subject_token"]?.firstOrNull() ?: ""
        val subjectTokenType = requestBody["subject_token_type"]?.firstOrNull() ?: ""
        val actorToken = requestBody["actor_token"]?.firstOrNull()
        val actorTokenType = requestBody["actor_token_type"]?.firstOrNull()
        val scope = requestBody["scope"]?.firstOrNull()
        val requestedTokenType = requestBody["requested_token_type"]?.firstOrNull()
        // Multi-value parameters
        val resources = requestBody["resource"] ?: emptyList()
        val audiences = requestBody["audience"] ?: emptyList()

        return GrantParameters.TokenExchange(
            subjectToken = subjectToken,
            subjectTokenType = subjectTokenType,
            actorToken = actorToken,
            actorTokenType = actorTokenType,
            resources = resources,
            audiences = audiences,
            scope = scope,
            requestedTokenType = requestedTokenType
        )
    }

    /**
     * Parse HTTP Basic Authentication header
     *
     * Returns (clientId, clientSecret) or (null, null) if not present or invalid
     */
    private fun parseBasicAuth(authHeader: String?): Pair<String?, String?> {
        if (authHeader == null) return Pair(null, null)

        // Format: "Basic base64(client_id:client_secret)"
        val parts = authHeader.trim().split(" ", limit = 2)
        if (parts.size != 2 || !parts[0].equals("Basic", ignoreCase = true)) {
            return Pair(null, null)
        }

        return try {
            // Decode base64
            val decoded = parts[1].decodeBase64ToString()
            val credentials = decoded.split(":", limit = 2)
            if (credentials.size == 2) {
                Pair(credentials[0], credentials[1])
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Decode base64 string to UTF-8 string
     */
    private fun String.decodeBase64ToString(): String {
        return decodeFromBase64().decodeToString()
    }

    /**
     * Extract client_id from attestation JWT sub claim (lightweight decode, no verification).
     */
    private fun extractClientIdFromAttestationJwt(attestationJwt: String?): String? {
        if (attestationJwt == null) return null
        val claims = JwtClaimsParser.parseClaimsOrNull(attestationJwt) ?: return null
        return claims["sub"]?.jsonPrimitive?.content
    }
}

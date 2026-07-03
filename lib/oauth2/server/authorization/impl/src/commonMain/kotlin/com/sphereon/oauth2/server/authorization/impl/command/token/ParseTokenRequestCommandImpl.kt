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
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.jsonPrimitive
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
 * - password (RFC 6749 Section 4.3)
 * - token-exchange (RFC 8693)
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseTokenRequestCommandImpl", exact = true)
class ParseTokenRequestCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ParseTokenRequestArgs, TokenRequestData, IdkError>(
        commandId = ParseTokenRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseTokenRequestArgs>(),
        outputTypeToken = typeToken<TokenRequestData>(),
    ),
    ParseTokenRequestCommand {
    override val commandId: String get() = ParseTokenRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseTokenRequestArgs

    override suspend fun doExecute(
        args: ParseTokenRequestArgs,
        applyDuring: (ParseTokenRequestArgs) -> ParseTokenRequestArgs,
    ): IdkResult<TokenRequestData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.requestBody, applied.requestHeaders, applied.httpUrl, applied.clientCertificateDer)
            .mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        requestBody: Map<String, List<String>>,
        requestHeaders: Map<String, String>,
        httpUrl: String,
        clientCertificateDer: ByteArray?,
    ): IdkResult<TokenRequestData, AuthorizationServerError> {
        // Extract grant_type (REQUIRED)
        val grantTypeString = requestBody["grant_type"]?.firstOrNull()
        if (grantTypeString.isNullOrBlank()) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: grant_type",
                    exception = null,
                ),
            )
        }

        // Parse grant type to enum
        val grantType =
            when (grantTypeString) {
                "authorization_code" -> GrantType.AUTHORIZATION_CODE

                "refresh_token" -> GrantType.REFRESH_TOKEN

                "client_credentials" -> GrantType.CLIENT_CREDENTIALS

                "password" -> GrantType.PASSWORD

                "urn:ietf:params:oauth:grant-type:pre-authorized_code" -> GrantType.PRE_AUTHORIZED_CODE

                "urn:ietf:params:oauth:grant-type:token-exchange" -> GrantType.TOKEN_EXCHANGE

                "urn:ietf:params:oauth:grant-type:device_code" -> GrantType.DEVICE_CODE

                else -> return Err(
                    AuthorizationServerError.UnsupportedGrantType(
                        grantType = grantTypeString,
                    ),
                )
            }

        // Shared client-auth extraction  — identical for /token, /introspect, /revoke.
        // Rejects multiple-method requests per OIDC Core §9 / RFC 6749 §2.3 .
        val extracted =
            com.sphereon.oauth2.server.authorization.impl.command
                .extractClientAuthentication(requestBody, requestHeaders, clientCertificateDer)
                .getOrElse { return Err(it) }
        val clientAuthentication = extracted.clientAuthentication
        val resolvedClientId = extracted.clientId

        // RFC 9110 §5.1: HTTP header field names are case-insensitive. Different hops in the
        // deployment topology may canonicalise differently before reaching the AS, so look the
        // header up case-insensitively rather than relying on a specific wire casing.
        val dpopProof = requestHeaders.entries.firstOrNull { it.key.equals("DPoP", ignoreCase = true) }?.value

        // Parse based on grant type
        val grantParameters =
            when (grantType) {
                GrantType.AUTHORIZATION_CODE -> parseAuthorizationCodeGrant(requestBody)

                GrantType.REFRESH_TOKEN -> parseRefreshTokenGrant(requestBody)

                GrantType.CLIENT_CREDENTIALS -> parseClientCredentialsGrant(requestBody)

                GrantType.PASSWORD -> parsePasswordGrant(requestBody)

                GrantType.PRE_AUTHORIZED_CODE -> parsePreAuthorizedCodeGrant(requestBody)

                GrantType.TOKEN_EXCHANGE -> parseTokenExchangeGrant(requestBody)

                GrantType.DEVICE_CODE -> parseDeviceCodeGrant(requestBody)

                else -> return Err(
                    AuthorizationServerError.UnsupportedGrantType(
                        grantType = grantTypeString,
                    ),
                )
            }

        val finalClientId = resolvedClientId
        if (finalClientId == null && clientAuthentication !is ClientAuthenticationConfig.Anonymous) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Missing required parameter: client_id",
                    exception = null,
                ),
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
                httpUrl = httpUrl,
            ),
        )
    }

    /**
     * Parse authorization_code grant request (RFC 6749 Section 4.1.3)
     */
    private fun parseAuthorizationCodeGrant(requestBody: Map<String, List<String>>): GrantParameters {
        val code = requestBody["code"]?.firstOrNull() ?: ""
        val redirectUri = requestBody["redirect_uri"]?.firstOrNull() ?: ""
        val codeVerifier = requestBody["code_verifier"]?.firstOrNull()

        return GrantParameters.AuthorizationCode(
            code = code,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
        )
    }

    /**
     * Parse refresh_token grant request (RFC 6749 Section 6)
     */
    private fun parseRefreshTokenGrant(requestBody: Map<String, List<String>>): GrantParameters {
        val refreshToken = requestBody["refresh_token"]?.firstOrNull() ?: ""
        val scope = requestBody["scope"]?.firstOrNull()

        return GrantParameters.RefreshToken(
            refreshToken = refreshToken,
            scope = scope,
        )
    }

    /**
     * Parse client_credentials grant request (RFC 6749 Section 4.4)
     */
    private fun parseClientCredentialsGrant(requestBody: Map<String, List<String>>): GrantParameters {
        val scope = requestBody["scope"]?.firstOrNull()
        val audiences = requestBody["audience"] ?: emptyList()

        return GrantParameters.ClientCredentials(
            scope = scope,
            audiences = audiences,
        )
    }

    /**
     * Parse resource owner password credentials grant request (RFC 6749 Section 4.3.2)
     */
    private fun parsePasswordGrant(requestBody: Map<String, List<String>>): GrantParameters {
        val username = requestBody["username"]?.firstOrNull() ?: ""
        val password = requestBody["password"]?.firstOrNull() ?: ""
        val scope = requestBody["scope"]?.firstOrNull()

        return GrantParameters.Password(
            username = username,
            password = password,
            scope = scope,
        )
    }

    /**
     * Parse pre-authorized code grant request (OID4VCI 1.0 Section 4.1.1)
     */
    private fun parsePreAuthorizedCodeGrant(requestBody: Map<String, List<String>>): GrantParameters {
        val preAuthorizedCode = requestBody["pre-authorized_code"]?.firstOrNull() ?: ""
        val txCode = requestBody["tx_code"]?.firstOrNull()

        return GrantParameters.PreAuthorizedCode(
            preAuthorizedCode = preAuthorizedCode,
            txCode = txCode,
        )
    }

    /**
     * Parse device authorization grant request (RFC 8628 §3.4).
     *
     * Mirrors the other grant parsers: extracts `device_code` and `client_id` from the form body
     * and defers the "value present" check to the verify-side command (Phase B). Defaulting to
     * the empty string keeps the grant-specific verifier as the single source of truth for
     * `invalid_grant` / `invalid_request` shaping.
     */
    private fun parseDeviceCodeGrant(requestBody: Map<String, List<String>>): GrantParameters {
        val deviceCode = requestBody["device_code"]?.firstOrNull() ?: ""
        val clientId = requestBody["client_id"]?.firstOrNull()

        return GrantParameters.DeviceCode(
            deviceCode = deviceCode,
            clientId = clientId,
        )
    }

    /**
     * Parse token exchange grant request (RFC 8693)
     */
    private fun parseTokenExchangeGrant(requestBody: Map<String, List<String>>): GrantParameters {
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
            requestedTokenType = requestedTokenType,
        )
    }
}

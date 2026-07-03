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

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * RFC 6749 §4.3 resource-owner password-credentials grant handler.
 *
 * The common OAuth2 model and user-auth provider SPI already expose password credentials; this
 * handler wires that SPI into the token endpoint. Client registration still controls whether a
 * caller may use the grant, and scope validation follows the same allow-list rules as the
 * client-credentials verifier.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<GrantHandler>())
class PasswordGrantHandlerImpl(
    private val clientRegistry: ClientRegistry,
    private val userAuthenticationProvider: UserAuthenticationProvider,
) : GrantHandler {
    override val grantType: String = GrantType.PASSWORD.value

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.Password

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> {
        val passwordParams = params as GrantParameters.Password
        val tokenRequest = context.tokenRequest
        val applied = context.applied
        val commands = context.commands
        val proofJkt = context.proofJkt
        val certThumbprint = context.certThumbprintS256

        if (passwordParams.username.isBlank() || passwordParams.password.isBlank()) {
            return errOf(AuthorizationServerError.InvalidRequest(details = "Missing required parameter: username or password"))
        }
        if (tokenRequest.clientId.isBlank()) {
            return errOf(AuthorizationServerError.InvalidClient(details = "Missing required parameter: client_id"))
        }

        val client =
            clientRegistry.getClient(tokenRequest.clientId).getOrElse { error ->
                return Err(IdkError.fromDTO(error))
            } ?: return errOf(AuthorizationServerError.InvalidClient(details = "Client not found"))

        if (GrantType.PASSWORD !in client.grantTypes) {
            return errOf(AuthorizationServerError.UnauthorizedClient(clientId = tokenRequest.clientId))
        }

        val grantedScope = validateRequestedScope(passwordParams.scope, client.allowedScopes).getOrElse { error -> return Err(IdkError.fromDTO(error)) }

        val methodAvailable =
            userAuthenticationProvider
                .isAuthenticationMethodAvailable(AuthenticationMethod.PASSWORD)
                .getOrElse { error ->
                    return errOf(
                        AuthorizationServerError.ServerError(
                            details = "Failed to check password authentication availability: ${error.message.defaultMessage}",
                        ),
                    )
                }
        if (!methodAvailable) {
            return errOf(AuthorizationServerError.InvalidGrant(details = "Password authentication is not available"))
        }

        val subject =
            userAuthenticationProvider
                .authenticateWithCredentials(
                    UserCredentials.UsernamePassword(
                        username = passwordParams.username,
                        password = passwordParams.password,
                    ),
                ).getOrElse {
                    return errOf(AuthorizationServerError.InvalidGrant(details = INVALID_RESOURCE_OWNER_CREDENTIALS))
                }
                ?: return errOf(AuthorizationServerError.InvalidGrant(details = INVALID_RESOURCE_OWNER_CREDENTIALS))

        val accessToken =
            commands.createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = subject,
                        clientId = tokenRequest.clientId,
                        scope = grantedScope,
                        dpopJkt = proofJkt,
                        certificateThumbprintS256 = certThumbprint,
                        baseUrlOverride = applied.baseUrlOverride,
                    ),
                ).getOrElse { error -> return Err(error) }

        val refreshToken =
            if (GrantType.REFRESH_TOKEN in client.grantTypes) {
                commands.createRefreshToken
                    .execute(
                        CreateRefreshTokenArgs(
                            subject = subject,
                            clientId = tokenRequest.clientId,
                            scope = grantedScope,
                            dpopJkt = proofJkt,
                            authTime = Clock.System.now().epochSeconds,
                            amr = listOf(PASSWORD_AMR),
                        ),
                    ).getOrElse { error -> return Err(error) }
                    .value
            } else {
                null
            }

        val grantedScopes = grantedScope?.split(" ")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val idToken =
            if (context.serverConfig.oidc.isEnabled && "openid" in grantedScopes) {
                val userInfo =
                    userAuthenticationProvider
                        .getUserInfo(subject)
                        .getOrElse { error ->
                            return errOf(
                                AuthorizationServerError.ServerError(
                                    details = "Failed to retrieve authenticated user claims: ${error.message.defaultMessage}",
                                ),
                            )
                        }

                commands.createIdToken
                    .execute(
                        CreateIdTokenArgs(
                            subject = subject,
                            clientId = tokenRequest.clientId,
                            authTime = Clock.System.now().epochSeconds,
                            amr = listOf(PASSWORD_AMR),
                            accessToken = accessToken.value,
                            userClaims = userInfo.toClaimsMap(),
                            baseUrlOverride = applied.baseUrlOverride,
                        ),
                    ).getOrElse { error -> return Err(error) }
                    .value
            } else {
                null
            }

        return commands.createTokenResponse.execute(
            CreateTokenResponseArgs(
                accessToken = accessToken.value,
                tokenType = tokenTypeFor(proofJkt),
                refreshToken = refreshToken,
                scope = grantedScope,
                idToken = idToken,
            ),
        )
    }

    private fun validateRequestedScope(
        requestedScope: String?,
        allowedScopes: List<String>?,
    ): IdkResult<String?, AuthorizationServerError> {
        if (requestedScope.isNullOrBlank()) {
            return com.sphereon.core.api.Ok(requestedScope)
        }
        if (allowedScopes == null) {
            return com.sphereon.core.api.Ok(requestedScope)
        }

        val requestedScopes = requestedScope.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        val disallowed = requestedScopes.filter { it !in allowedScopes }
        if (disallowed.isNotEmpty()) {
            return com.sphereon.core.api.Err(
                AuthorizationServerError.InvalidScope(
                    scope = disallowed.joinToString(" "),
                    allowedScopes = allowedScopes,
                ),
            )
        }
        return com.sphereon.core.api.Ok(requestedScope)
    }

    private companion object {
        private const val INVALID_RESOURCE_OWNER_CREDENTIALS = "Invalid resource owner credentials"
        private const val PASSWORD_AMR = "pwd"
    }
}

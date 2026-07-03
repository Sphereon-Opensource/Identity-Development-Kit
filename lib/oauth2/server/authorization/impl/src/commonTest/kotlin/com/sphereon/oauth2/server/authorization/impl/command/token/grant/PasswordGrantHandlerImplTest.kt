/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.command.token.ParseTokenRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasswordGrantHandlerImplTest {
    private val ctx = OAuth2ServerTestContext("password-grant-handler-test", this)
    private val execution = ctx.execution

    @Test
    fun parsesPasswordGrantParameters() =
        runTest {
            val command = ParseTokenRequestCommandImpl(execution)
            val result =
                command.execute(
                    ParseTokenRequestArgs(
                        requestBody =
                            mapOf(
                                "grant_type" to listOf("password"),
                                "client_id" to listOf("public-client"),
                                "username" to listOf("alice"),
                                "password" to listOf("alice-secret"),
                                "scope" to listOf("read"),
                            ),
                        httpUrl = "https://as.example.com/token",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(GrantType.PASSWORD, result.value.grantType)
            val params = assertIs<GrantParameters.Password>(result.value.grantParameters)
            assertEquals("alice", params.username)
            assertEquals("alice-secret", params.password)
            assertEquals("read", params.scope)
        }

    @Test
    fun authenticatesResourceOwnerAndMintsAccessAndRefreshTokens() =
        runTest {
            val storage = InMemoryOAuth2BackingStorageImpl()
            val clientRegistry = InMemoryClientRegistryImpl(storage)
            val client =
                TestFixtures.confidentialClient.copy(
                    grantTypes = listOf(GrantType.PASSWORD, GrantType.REFRESH_TOKEN),
                    allowedScopes = listOf("read"),
                )
            assertTrue(clientRegistry.registerClient(client).isOk)

            val handler =
                PasswordGrantHandlerImpl(
                    clientRegistry = clientRegistry,
                    userAuthenticationProvider =
                        object : TestUserAuthenticationProvider() {
                            override suspend fun authenticateWithCredentials(
                                credentials: UserCredentials,
                                context: AuthenticationContext?,
                            ): IdkResult<String?, AuthenticationError> {
                                val usernamePassword = credentials as UserCredentials.UsernamePassword
                                return if (usernamePassword.username == "alice" && usernamePassword.password == "alice-secret") {
                                    Ok("user-alice")
                                } else {
                                    Ok(null)
                                }
                            }
                        },
                )
            val commands = CapturingPasswordGrantCommands()
            val context = passwordGrantContext(client.clientId, commands)

            val result = handler.handle(context.tokenRequest.grantParameters, context)

            assertTrue(result.isOk, "password grant should succeed, got ${if (!result.isOk) result.error else "ok"}")
            assertEquals("AT-PASSWORD", result.value.accessToken)
            assertEquals("RT-PASSWORD", result.value.refreshToken)
            assertEquals("read", result.value.scope)

            val accessTokenArgs = commands.capturedAccessTokenArgs
            assertNotNull(accessTokenArgs)
            assertEquals("user-alice", accessTokenArgs.subject)
            assertEquals(client.clientId, accessTokenArgs.clientId)
            assertEquals("read", accessTokenArgs.scope)

            val refreshTokenArgs = commands.capturedRefreshTokenArgs
            assertNotNull(refreshTokenArgs)
            assertEquals("user-alice", refreshTokenArgs.subject)
            assertEquals(client.clientId, refreshTokenArgs.clientId)
            assertEquals("read", refreshTokenArgs.scope)
        }

    private fun passwordGrantContext(
        clientId: String,
        commands: AuthorizationServerService.Commands,
    ): GrantContext {
        val grantParameters = GrantParameters.Password(username = "alice", password = "alice-secret", scope = "read")
        val tokenRequest =
            TokenRequestData(
                grantType = GrantType.PASSWORD,
                clientId = clientId,
                clientAuthentication = ClientAuthenticationConfig.Basic(ClientCredentials(clientId, "secret123")),
                grantParameters = grantParameters,
                httpUrl = "https://as.example.com/token",
            )
        return GrantContext(
            tokenRequest = tokenRequest,
            resolvedClientId = clientId,
            proofJkt = null,
            certThumbprintS256 = null,
            applied =
                HandleTokenRequestArgs(
                    requestBody = mapOf("grant_type" to listOf("password")),
                    requestHeaders = emptyMap(),
                    httpUrl = "https://as.example.com/token",
                ),
            commands = commands,
            serverConfig = OAuth2ServerInstanceConfig(issuer = "https://as.example.com"),
        )
    }

    private class CapturingPasswordGrantCommands : AuthorizationServerService.Commands {
        var capturedAccessTokenArgs: CreateAccessTokenArgs? = null
        var capturedRefreshTokenArgs: CreateRefreshTokenArgs? = null

        override val createAccessToken: CreateAccessTokenCommand =
            object : CreateAccessTokenCommand {
                override val inputTypeToken = typeToken<CreateAccessTokenArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError> {
                    capturedAccessTokenArgs = args
                    return Ok(StringResult(value = "AT-PASSWORD"))
                }
            }

        override val createRefreshToken: CreateRefreshTokenCommand =
            object : CreateRefreshTokenCommand {
                override val inputTypeToken = typeToken<CreateRefreshTokenArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateRefreshTokenArgs): IdkResult<StringResult, IdkError> {
                    capturedRefreshTokenArgs = args
                    return Ok(StringResult(value = "RT-PASSWORD"))
                }
            }

        override val createTokenResponse: CreateTokenResponseCommand =
            object : CreateTokenResponseCommand {
                override val inputTypeToken = typeToken<CreateTokenResponseArgs>()
                override val outputTypeToken = typeToken<TokenResponse>()
                override val isEnabled = true

                override suspend fun execute(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError> =
                    Ok(
                        TokenResponse(
                            accessToken = args.accessToken,
                            tokenType = args.tokenType,
                            refreshToken = args.refreshToken,
                            scope = args.scope,
                            idToken = args.idToken,
                        ),
                    )
            }

        override val parseTokenRequest get(): com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand = throw NotImplementedError()
        override val verifyAuthorizationCodeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand = throw NotImplementedError()
        override val verifyRefreshTokenGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand = throw NotImplementedError()
        override val verifyClientCredentialsGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand = throw NotImplementedError()
        override val verifyTokenExchangeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand = throw NotImplementedError()
        override val verifyPreAuthorizedCodeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand = throw NotImplementedError()
        override val parseAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand = throw NotImplementedError()
        override val verifyAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand = throw NotImplementedError()
        override val createAuthorizationSession get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand = throw NotImplementedError()
        override val createAuthorizationCode get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand = throw NotImplementedError()
        override val createAuthorizationResponse get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand = throw NotImplementedError()
        override val createAuthorizationErrorResponse get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand = throw NotImplementedError()
        override val parsePushedAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand = throw NotImplementedError()
        override val verifyPushedAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand = throw NotImplementedError()
        override val createRequestUri get(): com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand = throw NotImplementedError()
        override val createPushedAuthorizationResponse get(): com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand = throw NotImplementedError()
        override val retrieveAuthorizationRequestByUri get(): com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand = throw NotImplementedError()
        override val parseIntrospectionRequest get(): com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand = throw NotImplementedError()
        override val introspectToken get(): com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand = throw NotImplementedError()
        override val parseRevocationRequest get(): com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand = throw NotImplementedError()
        override val revokeToken get(): com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand = throw NotImplementedError()
        override val buildServerMetadata get(): com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand = throw NotImplementedError()
        override val verifyClientAuthentication get(): com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand = throw NotImplementedError()
        override val createAttestationChallenge get(): com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand = throw NotImplementedError()
        override val createIdToken get(): com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand = throw NotImplementedError()
        override val getUserInfo get(): com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand = throw NotImplementedError()
        override val getJwks get(): com.sphereon.oauth2.server.authorization.command.GetJwksCommand = throw NotImplementedError()
    }
}

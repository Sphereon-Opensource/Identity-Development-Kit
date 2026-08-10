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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedRefreshTokenGrant
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAuthorizationCodeStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.model.AuthorizationCodeData
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationEvidence
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceAttestationTokenClaims
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceClientStatusEvidence
import com.sphereon.oauth2.server.authorization.wallet.WalletInstanceTrustEvidence
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * RFC 9068 §2.2.3.1: the auth-code grant handler embeds the authenticated user's `roles`
 * (a registered RFC 9068 authorization claim) into [CreateAccessTokenArgs.additionalClaims]
 * so it lands in the minted at+jwt payload. `CreateAccessTokenCommandImpl` only filters
 * `oidc.*`-namespaced keys out of the JWT body, so a `roles` entry placed here survives
 * verbatim into the access token JWT.
 *
 * The handler sources `roles` from `verified.userClaims`, which carries the local user
 * provider's `UserInfo.attributes` (a `List<String>`) or a federated provider's
 * `JsonArray` of string primitives. Identity claims stay excluded — only `roles` crosses.
 */
class AuthorizationCodeGrantRolesClaimTest {
    private fun codeData(subject: String = "operator-1"): AuthorizationCodeData {
        val now = Clock.System.now()
        return AuthorizationCodeData(
            code = "code-1",
            clientId = "client-1",
            subject = subject,
            redirectUri = "https://client.example.com/cb",
            issuedAt = now,
            expiresAt = now + 600.seconds,
        )
    }

    private fun verifyStub(verified: VerifiedAuthorizationCodeGrant): VerifyAuthorizationCodeGrantCommand =
        object : VerifyAuthorizationCodeGrantCommand {
            override val inputTypeToken = typeToken<VerifyAuthorizationCodeGrantArgs>()
            override val outputTypeToken = typeToken<VerifiedAuthorizationCodeGrant>()
            override val isEnabled = true

            override suspend fun execute(args: VerifyAuthorizationCodeGrantArgs): IdkResult<VerifiedAuthorizationCodeGrant, IdkError> = Ok(verified)
        }

    private class CapturingCommands(
        private val verifyStub: VerifyAuthorizationCodeGrantCommand,
        private val refreshVerifyStub: VerifyRefreshTokenGrantCommand? = null,
    ) : AuthorizationServerService.Commands {
        var capturedAccessTokenArgs: CreateAccessTokenArgs? = null
        var capturedRefreshTokenArgs: CreateRefreshTokenArgs? = null

        override val verifyAuthorizationCodeGrant get() = verifyStub

        override val createAccessToken: CreateAccessTokenCommand =
            object : CreateAccessTokenCommand {
                override val inputTypeToken = typeToken<CreateAccessTokenArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError> {
                    capturedAccessTokenArgs = args
                    return Ok(StringResult(value = "AT-ROLES"))
                }
            }

        override val createRefreshToken: CreateRefreshTokenCommand =
            object : CreateRefreshTokenCommand {
                override val inputTypeToken = typeToken<CreateRefreshTokenArgs>()
                override val outputTypeToken = typeToken<StringResult>()
                override val isEnabled = true

                override suspend fun execute(args: CreateRefreshTokenArgs): IdkResult<StringResult, IdkError> {
                    capturedRefreshTokenArgs = args
                    return Ok(StringResult(value = "RT-ROLES"))
                }
            }

        override val createTokenResponse: CreateTokenResponseCommand =
            object : CreateTokenResponseCommand {
                override val inputTypeToken = typeToken<CreateTokenResponseArgs>()
                override val outputTypeToken = typeToken<TokenResponse>()
                override val isEnabled = true

                override suspend fun execute(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError> =
                    Ok(TokenResponse(accessToken = args.accessToken, tokenType = args.tokenType, refreshToken = args.refreshToken, scope = args.scope))
            }

        override val parseTokenRequest get(): com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand = throw NotImplementedError()
        override val verifyRefreshTokenGrant get() = refreshVerifyStub ?: throw NotImplementedError()
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

    private fun newHandler(): AuthorizationCodeGrantHandlerImpl =
        AuthorizationCodeGrantHandlerImpl(
            authorizationCodeStorage = InMemoryAuthorizationCodeStorageImpl(InMemoryOAuth2BackingStorageImpl()),
            scopeClaimsMapper = null,
        )

    private fun walletInstanceAttestationEvidence(): WalletInstanceAttestationEvidence =
        WalletInstanceAttestationEvidence(
            evidenceId = "persisted-wia-1",
            profile = "TS03_JWT",
            format = "JWT",
            attestationExpiresAtEpochSeconds = Clock.System.now().epochSeconds + 600,
            clientStatus =
                WalletInstanceClientStatusEvidence(
                    statusListUri = "https://status.example.com/wia/status.jwt",
                    index = "42",
                    status = "VALID",
                ),
            trust =
                WalletInstanceTrustEvidence(
                    trusted = true,
                    decision = "TRUSTED",
                    expiresAtEpochSeconds = Clock.System.now().epochSeconds + 600,
                    signerCertificateProfile = "HARDWARE_SECURE",
                ),
            walletInstanceId = "wallet-instance-1",
            signerCertificateProfile = "HARDWARE_SECURE",
        )

    private fun grantContext(
        commands: AuthorizationServerService.Commands,
        walletInstanceAttestation: WalletInstanceAttestationEvidence? = null,
    ): GrantContext {
        val tokenRequest =
            TokenRequestData(
                grantType = GrantType.AUTHORIZATION_CODE,
                clientId = "client-1",
                clientAuthentication = ClientAuthenticationConfig.None(clientId = "client-1"),
                grantParameters = GrantParameters.AuthorizationCode(code = "code-1", redirectUri = "https://client.example.com/cb"),
                httpUrl = "https://as.example.com/token",
            )
        return GrantContext(
            tokenRequest = tokenRequest,
            resolvedClientId = "client-1",
            proofJkt = null,
            certThumbprintS256 = null,
            applied =
                HandleTokenRequestArgs(
                    requestBody = mapOf("grant_type" to listOf("authorization_code"), "code" to listOf("code-1")),
                    requestHeaders = emptyMap(),
                    httpUrl = "https://as.example.com/token",
                ),
            commands = commands,
            serverConfig = OAuth2ServerInstanceConfig(issuer = "https://as.example.com"),
            walletInstanceAttestation = walletInstanceAttestation,
        )
    }

    private fun refreshGrantContext(commands: AuthorizationServerService.Commands): GrantContext {
        val tokenRequest =
            TokenRequestData(
                grantType = GrantType.REFRESH_TOKEN,
                clientId = "client-1",
                clientAuthentication = ClientAuthenticationConfig.None(clientId = "client-1"),
                grantParameters = GrantParameters.RefreshToken(refreshToken = "refresh-1"),
                httpUrl = "https://as.example.com/token",
            )
        return GrantContext(
            tokenRequest = tokenRequest,
            resolvedClientId = "client-1",
            proofJkt = null,
            certThumbprintS256 = null,
            applied =
                HandleTokenRequestArgs(
                    requestBody = mapOf("grant_type" to listOf("refresh_token"), "refresh_token" to listOf("refresh-1")),
                    requestHeaders = emptyMap(),
                    httpUrl = "https://as.example.com/token",
                ),
            commands = commands,
            serverConfig = OAuth2ServerInstanceConfig(issuer = "https://as.example.com", refreshTokenRotation = false),
        )
    }

    private suspend fun mintWithUserClaims(
        userClaims: Map<String, Any>,
        codeData: AuthorizationCodeData = codeData(),
        resource: List<String> = emptyList(),
    ): CreateAccessTokenArgs {
        val commands =
            CapturingCommands(
                verifyStub(
                    VerifiedAuthorizationCodeGrant(
                        codeData = codeData,
                        subject = "operator-1",
                        clientId = "client-1",
                        scope = "openid",
                        resource = resource,
                        userClaims = userClaims,
                    ),
                ),
            )
        val handler = newHandler()
        val context = grantContext(commands)
        val result = handler.handle(context.tokenRequest.grantParameters, context)
        assertTrue(result.isOk, "auth-code grant must succeed, got ${if (!result.isOk) result.error else "ok"}")
        val args = commands.capturedAccessTokenArgs
        assertNotNull(args, "createAccessToken must be invoked")
        return args
    }

    @Test
    fun rolesListFromUserClaimsLandsInAccessTokenAdditionalClaims() =
        runTest {
            val args =
                mintWithUserClaims(
                    mapOf(
                        "roles" to listOf("tenant-admin", "platform-admin"),
                        "email" to "operator@acme.example",
                        "name" to "Platform Operator",
                    ),
                )
            assertEquals(
                listOf("tenant-admin", "platform-admin"),
                args.additionalClaims["roles"],
                "roles must reach the access-token mint as an RFC 9068 §2.2.3.1 authorization claim",
            )
            // Identity claims stay out of the access token: only the registered
            // authorization claim crosses from userClaims.
            assertFalse(args.additionalClaims.containsKey("email"), "identity claims must NOT leak into the access token")
            assertFalse(args.additionalClaims.containsKey("name"), "identity claims must NOT leak into the access token")
        }

    @Test
    fun authenticationContextFromCodeUsesTypedAccessTokenFields() =
        runTest {
            val authTime = 1_782_936_100L
            val args =
                mintWithUserClaims(
                    userClaims = mapOf("email" to "operator@acme.example"),
                    codeData =
                        codeData().copy(
                            authTime = authTime,
                            acr = "urn:nist:sp:800-63:aal1",
                            amr = listOf("pwd"),
                        ),
                )

            assertEquals(authTime, args.authTime, "auth_time must reach the typed access-token mint field")
            assertEquals("urn:nist:sp:800-63:aal1", args.acr, "acr must reach the typed access-token mint field")
            assertEquals(listOf("pwd"), args.amr, "amr must reach the typed access-token mint field")
            assertFalse(args.additionalClaims.containsKey("auth_time"), "reserved auth_time must not be an additional claim")
            assertFalse(args.additionalClaims.containsKey("acr"), "reserved acr must not be an additional claim")
            assertFalse(args.additionalClaims.containsKey("amr"), "reserved amr must not be an additional claim")
            assertFalse(args.additionalClaims.containsKey("email"), "identity claims must NOT leak into the access token")
        }

    @Test
    fun resourceBoundAuthorizationCodeMintsThatResourceAsAccessTokenAudience() =
        runTest {
            val resource = "https://issuer.example.test/api/developer/v1"
            val args = mintWithUserClaims(userClaims = emptyMap(), resource = listOf(resource))

            assertEquals(listOf(resource), args.audience)
        }

    @Test
    fun defaultClientAudienceIsUsedAndBoundWhenAuthorizationCodeHasNoResource() =
        runTest {
            val defaultAudience = "enterprise-issuer"
            val commands = CapturingCommands(
                verifyStub(
                    VerifiedAuthorizationCodeGrant(
                        codeData = codeData().copy(defaultAccessTokenAudience = defaultAudience),
                        subject = "operator-1",
                        clientId = "client-1",
                        scope = "openid",
                        defaultAccessTokenAudience = defaultAudience,
                    ),
                ),
            )
        val context = grantContext(commands)
        val result = newHandler().handle(context.tokenRequest.grantParameters, context)

            assertTrue(result.isOk)
            assertEquals(listOf(defaultAudience), commands.capturedAccessTokenArgs?.audience)
            assertEquals(defaultAudience, commands.capturedRefreshTokenArgs?.defaultAccessTokenAudience)
        }

    @Test
    fun refreshGrantRetainsDefaultClientAudienceWhenNoResourceWasBound() =
        runTest {
            val defaultAudience = "enterprise-issuer"
            val refreshVerifier =
                object : VerifyRefreshTokenGrantCommand {
                    override val inputTypeToken = typeToken<VerifyRefreshTokenGrantArgs>()
                    override val outputTypeToken = typeToken<VerifiedRefreshTokenGrant>()
                    override val isEnabled = true

                    override suspend fun execute(args: VerifyRefreshTokenGrantArgs): IdkResult<VerifiedRefreshTokenGrant, IdkError> =
                        Ok(
                            VerifiedRefreshTokenGrant(
                                subject = "operator-1",
                                clientId = "client-1",
                                scope = "openid",
                                defaultAccessTokenAudience = defaultAudience,
                                refreshTokenId = "refresh-1",
                            ),
                        )
                }
            val commands = CapturingCommands(verifyStub = verifyStub(VerifiedAuthorizationCodeGrant(codeData(), "operator-1", "client-1")), refreshVerifyStub = refreshVerifier)
            val context = refreshGrantContext(commands)

            val result =
                RefreshTokenGrantHandlerImpl(
                    tokenStorage = InMemoryTokenStorageImpl(InMemoryOAuth2BackingStorageImpl()),
                    auditEmitter = com.sphereon.oauth2.server.authorization.audit.NoOpOAuth2AuditEmitter,
                ).handle(context.tokenRequest.grantParameters, context)

            assertTrue(result.isOk)
            assertEquals(listOf(defaultAudience), commands.capturedAccessTokenArgs?.audience)
        }

    @Test
    fun rolesJsonArrayFromFederatedClaimsLandsInAccessTokenAdditionalClaims() =
        runTest {
            val args =
                mintWithUserClaims(
                    mapOf(
                        "roles" to JsonArray(listOf(JsonPrimitive("tenant-admin"), JsonPrimitive("auditor"))),
                    ),
                )
            assertEquals(
                listOf("tenant-admin", "auditor"),
                args.additionalClaims["roles"],
                "JsonArray-encoded roles (federated claim bags) must normalize to a string list",
            )
        }

    @Test
    fun absentOrEmptyRolesProduceNoRolesClaim() =
        runTest {
            val withoutRoles = mintWithUserClaims(mapOf("email" to "operator@acme.example"))
            assertFalse(withoutRoles.additionalClaims.containsKey("roles"), "no roles claim when the user carries none")

            val emptyRoles = mintWithUserClaims(mapOf("roles" to emptyList<String>()))
            assertFalse(emptyRoles.additionalClaims.containsKey("roles"), "empty roles collection must not mint an empty claim")
        }

    @Test
    fun walletInstanceAttestationEvidenceLandsInAccessTokenAdditionalClaims() =
        runTest {
            val commands =
                CapturingCommands(
                    verifyStub(
                        VerifiedAuthorizationCodeGrant(
                            codeData = codeData(),
                            subject = "operator-1",
                            clientId = "client-1",
                            scope = "openid",
                        ),
                    ),
                )
            val handler = newHandler()
            val context = grantContext(commands, walletInstanceAttestationEvidence())
            val result = handler.handle(context.tokenRequest.grantParameters, context)

            assertTrue(result.isOk, "auth-code grant must succeed, got ${if (!result.isOk) result.error else "ok"}")
            val args = commands.capturedAccessTokenArgs
            assertNotNull(args, "createAccessToken must be invoked")

            val clientStatus = args.additionalClaims[WalletInstanceAttestationTokenClaims.CLIENT_STATUS] as? JsonObject
            assertNotNull(clientStatus, "client_status must be available to the access-token mint")
            assertEquals("https://status.example.com/wia/status.jwt", clientStatus["status_list_uri"]?.jsonPrimitive?.contentOrNull)
            assertEquals("42", clientStatus["index"]?.jsonPrimitive?.contentOrNull)

            val attestation = args.additionalClaims[WalletInstanceAttestationTokenClaims.WALLET_INSTANCE_ATTESTATION] as? JsonObject
            assertNotNull(attestation, "wallet_instance_attestation must be available to the access-token mint")
            assertEquals("persisted-wia-1", attestation["evidence_id"]?.jsonPrimitive?.contentOrNull)
            assertEquals("TS03_JWT", attestation["profile"]?.jsonPrimitive?.contentOrNull)
        }
}

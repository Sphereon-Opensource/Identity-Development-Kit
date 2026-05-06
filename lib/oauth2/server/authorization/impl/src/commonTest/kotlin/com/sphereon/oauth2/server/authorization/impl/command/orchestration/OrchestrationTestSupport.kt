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

package com.sphereon.oauth2.server.authorization.impl.command.orchestration

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.AttestationChallengeResponse
import com.sphereon.oauth2.server.authorization.command.AuthorizationErrorResponseData
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.authorization.command.IntrospectionRequestData
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.command.RequestUriData
import com.sphereon.oauth2.server.authorization.command.RevocationRequestData
import com.sphereon.oauth2.server.authorization.command.RevokeTokenArgs
import com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.VerifiedClientCredentialsGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedPreAuthCodeGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedRefreshTokenGrant
import com.sphereon.oauth2.server.authorization.command.VerifiedTokenExchangeGrant
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService

/**
 * Test-only [AuthorizationServerService] used by the Phase 3c-1 orchestration tests. Properties
 * default to "throw NotImplementedError" so individual tests configure only the lower-level
 * commands they exercise via the helper factory methods on the companion.
 */
internal open class StubAuthorizationServerService(
    private val parseTokenRequestStub: com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand? = null,
    private val verifyClientAuthenticationStub: VerifyClientAuthenticationCommand? = null,
    private val parseIntrospectionRequestStub: ParseIntrospectionRequestCommand? = null,
    private val introspectTokenStub: IntrospectTokenCommand? = null,
    private val parseRevocationRequestStub: ParseRevocationRequestCommand? = null,
    private val revokeTokenStub: RevokeTokenCommand? = null,
    private val parsePushedAuthorizationRequestStub: ParsePushedAuthorizationRequestCommand? = null,
    private val verifyPushedAuthorizationRequestStub: VerifyPushedAuthorizationRequestCommand? = null,
    private val createRequestUriStub: CreateRequestUriCommand? = null,
    private val createPushedAuthorizationResponseStub: CreatePushedAuthorizationResponseCommand? = null,
    private val buildServerMetadataStub: BuildServerMetadataCommand? = null,
    private val getUserInfoStub: GetUserInfoCommand? = null,
    private val getJwksStub: GetJwksCommand? = null,
) : AuthorizationServerService {
    override suspend fun parseTokenRequest(args: ParseTokenRequestArgs): IdkResult<TokenRequestData, IdkError> = unused()

    override suspend fun verifyAuthorizationCodeGrant(args: VerifyAuthorizationCodeGrantArgs): IdkResult<VerifiedAuthorizationCodeGrant, IdkError> = unused()

    override suspend fun verifyRefreshTokenGrant(args: VerifyRefreshTokenGrantArgs): IdkResult<VerifiedRefreshTokenGrant, IdkError> = unused()

    override suspend fun verifyClientCredentialsGrant(args: VerifyClientCredentialsGrantArgs): IdkResult<VerifiedClientCredentialsGrant, IdkError> = unused()

    override suspend fun verifyTokenExchangeGrant(args: VerifyTokenExchangeGrantArgs): IdkResult<VerifiedTokenExchangeGrant, IdkError> = unused()

    override suspend fun verifyPreAuthorizedCodeGrant(args: VerifyPreAuthCodeArgs): IdkResult<VerifiedPreAuthCodeGrant, IdkError> = unused()

    override suspend fun createAccessToken(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError> = unused()

    override suspend fun createRefreshToken(args: CreateRefreshTokenArgs): IdkResult<StringResult, IdkError> = unused()

    override suspend fun createTokenResponse(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError> = unused()

    override suspend fun parseAuthorizationRequest(args: ParseAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError> = unused()

    override suspend fun verifyAuthorizationRequest(args: AuthorizationRequestData): IdkResult<VerifiedAuthorizationRequest, IdkError> = unused()

    override suspend fun createAuthorizationSession(args: VerifiedAuthorizationRequest): IdkResult<AuthorizationSession, IdkError> = unused()

    override suspend fun createAuthorizationCode(args: CreateAuthorizationCodeArgs): IdkResult<StringResult, IdkError> = unused()

    override suspend fun createAuthorizationResponse(args: CreateAuthorizationResponseArgs): IdkResult<AuthorizationResponseData, IdkError> = unused()

    override suspend fun createAuthorizationErrorResponse(args: CreateAuthorizationErrorResponseArgs): IdkResult<AuthorizationErrorResponseData, IdkError> = unused()

    override suspend fun parsePushedAuthorizationRequest(args: ParsePushedAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError> = unused()

    override suspend fun verifyPushedAuthorizationRequest(args: VerifyPushedAuthorizationRequestArgs): IdkResult<VerifiedAuthorizationRequest, IdkError> = unused()

    override suspend fun createRequestUri(args: VerifiedAuthorizationRequest): IdkResult<RequestUriData, IdkError> = unused()

    override suspend fun createPushedAuthorizationResponse(args: CreatePushedAuthorizationResponseArgs): IdkResult<PushedAuthorizationResponse, IdkError> = unused()

    override suspend fun retrieveAuthorizationRequestByUri(requestUri: String): IdkResult<VerifiedAuthorizationRequest, IdkError> = unused()

    override suspend fun parseIntrospectionRequest(args: ParseIntrospectionRequestArgs): IdkResult<IntrospectionRequestData, IdkError> = unused()

    override suspend fun introspectToken(args: IntrospectTokenArgs): IdkResult<TokenIntrospectionResponse, IdkError> = unused()

    override suspend fun parseRevocationRequest(args: ParseRevocationRequestArgs): IdkResult<RevocationRequestData, IdkError> = unused()

    override suspend fun revokeToken(args: RevokeTokenArgs): IdkResult<Unit, IdkError> = unused()

    override suspend fun buildServerMetadata(args: BuildServerMetadataArgs): IdkResult<AuthorizationServerMetadata, IdkError> = unused()

    override suspend fun verifyClientAuthentication(args: VerifyClientAuthenticationArgs): IdkResult<VerifiedClientAuthentication, IdkError> = unused()

    override suspend fun createAttestationChallenge(args: CreateAttestationChallengeArgs): IdkResult<AttestationChallengeResponse, IdkError> = unused()

    override suspend fun createIdToken(args: CreateIdTokenArgs): IdkResult<StringResult, IdkError> = unused()

    override suspend fun getUserInfo(args: GetUserInfoArgs): IdkResult<UserInfoResponse, IdkError> = unused()

    override suspend fun getJwks(args: GetJwksArgs): IdkResult<JwksResult, IdkError> = unused()

    override val commands: AuthorizationServerService.Commands =
        object : AuthorizationServerService.Commands {
            override val parseTokenRequest get() = parseTokenRequestStub ?: missing("parseTokenRequest")
            override val verifyAuthorizationCodeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand = missing("verifyAuthorizationCodeGrant")
            override val verifyRefreshTokenGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand = missing("verifyRefreshTokenGrant")
            override val verifyClientCredentialsGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand = missing("verifyClientCredentialsGrant")
            override val verifyTokenExchangeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand = missing("verifyTokenExchangeGrant")
            override val verifyPreAuthorizedCodeGrant get(): com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand = missing("verifyPreAuthorizedCodeGrant")
            override val createAccessToken get(): com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand = missing("createAccessToken")
            override val createRefreshToken get(): com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand = missing("createRefreshToken")
            override val createTokenResponse get(): com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand = missing("createTokenResponse")
            override val parseAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand = missing("parseAuthorizationRequest")
            override val verifyAuthorizationRequest get(): com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand = missing("verifyAuthorizationRequest")
            override val createAuthorizationSession get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand = missing("createAuthorizationSession")
            override val createAuthorizationCode get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand = missing("createAuthorizationCode")
            override val createAuthorizationResponse get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand = missing("createAuthorizationResponse")
            override val createAuthorizationErrorResponse get(): com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand = missing("createAuthorizationErrorResponse")
            override val parsePushedAuthorizationRequest get() = parsePushedAuthorizationRequestStub ?: missing("parsePushedAuthorizationRequest")
            override val verifyPushedAuthorizationRequest get() = verifyPushedAuthorizationRequestStub ?: missing("verifyPushedAuthorizationRequest")
            override val createRequestUri get() = createRequestUriStub ?: missing("createRequestUri")
            override val createPushedAuthorizationResponse get() = createPushedAuthorizationResponseStub ?: missing("createPushedAuthorizationResponse")
            override val retrieveAuthorizationRequestByUri get(): com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand =
                missing(
                    "retrieveAuthorizationRequestByUri"
                )
            override val parseIntrospectionRequest get() = parseIntrospectionRequestStub ?: missing("parseIntrospectionRequest")
            override val introspectToken get() = introspectTokenStub ?: missing("introspectToken")
            override val parseRevocationRequest get() = parseRevocationRequestStub ?: missing("parseRevocationRequest")
            override val revokeToken get() = revokeTokenStub ?: missing("revokeToken")
            override val buildServerMetadata get() = buildServerMetadataStub ?: missing("buildServerMetadata")
            override val verifyClientAuthentication get() = verifyClientAuthenticationStub ?: missing("verifyClientAuthentication")
            override val createAttestationChallenge get(): com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand = missing("createAttestationChallenge")
            override val createIdToken get(): com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand = missing("createIdToken")
            override val getUserInfo get() = getUserInfoStub ?: missing("getUserInfo")
            override val getJwks get() = getJwksStub ?: missing("getJwks")
        }
}

private fun missing(name: String): Nothing = throw NotImplementedError("Stub commands.$name was not configured for this test")

private fun <T> unused(): T = throw NotImplementedError("Service-method facade is not exercised by orchestration tests; use commands.* instead")

/** Lightweight stub for [GetJwksCommand] used by orchestration tests. */
internal fun stubGetJwks(handler: suspend (GetJwksArgs) -> IdkResult<JwksResult, IdkError>): GetJwksCommand =
    object : GetJwksCommand {
        override val inputTypeToken = typeToken<GetJwksArgs>()
        override val outputTypeToken = typeToken<JwksResult>()
        override val isEnabled = true

        override suspend fun execute(args: GetJwksArgs) = handler(args)
    }

/** Lightweight stub for [GetUserInfoCommand]. */
internal fun stubGetUserInfo(handler: suspend (GetUserInfoArgs) -> IdkResult<UserInfoResponse, IdkError>): GetUserInfoCommand =
    object : GetUserInfoCommand {
        override val inputTypeToken = typeToken<GetUserInfoArgs>()
        override val outputTypeToken = typeToken<UserInfoResponse>()
        override val isEnabled = true

        override suspend fun execute(args: GetUserInfoArgs) = handler(args)
    }

/** Lightweight stub for [BuildServerMetadataCommand]. */
internal fun stubBuildServerMetadata(handler: suspend (BuildServerMetadataArgs) -> IdkResult<AuthorizationServerMetadata, IdkError>): BuildServerMetadataCommand =
    object : BuildServerMetadataCommand {
        override val inputTypeToken = typeToken<BuildServerMetadataArgs>()
        override val outputTypeToken = typeToken<AuthorizationServerMetadata>()
        override val isEnabled = true

        override suspend fun execute(args: BuildServerMetadataArgs) = handler(args)
    }

/** Lightweight stub for [VerifyClientAuthenticationCommand]. */
internal fun stubVerifyClientAuthentication(handler: suspend (VerifyClientAuthenticationArgs) -> IdkResult<VerifiedClientAuthentication, IdkError>,): VerifyClientAuthenticationCommand =
    object : VerifyClientAuthenticationCommand {
        override val inputTypeToken = typeToken<VerifyClientAuthenticationArgs>()
        override val outputTypeToken = typeToken<VerifiedClientAuthentication>()
        override val isEnabled = true

        override suspend fun execute(args: VerifyClientAuthenticationArgs) = handler(args)
    }

/** Lightweight stub for [ParseIntrospectionRequestCommand]. */
internal fun stubParseIntrospectionRequest(handler: suspend (ParseIntrospectionRequestArgs) -> IdkResult<IntrospectionRequestData, IdkError>,): ParseIntrospectionRequestCommand =
    object : ParseIntrospectionRequestCommand {
        override val inputTypeToken = typeToken<ParseIntrospectionRequestArgs>()
        override val outputTypeToken = typeToken<IntrospectionRequestData>()
        override val isEnabled = true

        override suspend fun execute(args: ParseIntrospectionRequestArgs) = handler(args)
    }

/** Lightweight stub for [IntrospectTokenCommand]. */
internal fun stubIntrospectToken(handler: suspend (IntrospectTokenArgs) -> IdkResult<TokenIntrospectionResponse, IdkError>): IntrospectTokenCommand =
    object : IntrospectTokenCommand {
        override val inputTypeToken = typeToken<IntrospectTokenArgs>()
        override val outputTypeToken = typeToken<TokenIntrospectionResponse>()
        override val isEnabled = true

        override suspend fun execute(args: IntrospectTokenArgs) = handler(args)
    }

/** Lightweight stub for [ParseRevocationRequestCommand]. */
internal fun stubParseRevocationRequest(handler: suspend (ParseRevocationRequestArgs) -> IdkResult<RevocationRequestData, IdkError>,): ParseRevocationRequestCommand =
    object : ParseRevocationRequestCommand {
        override val inputTypeToken = typeToken<ParseRevocationRequestArgs>()
        override val outputTypeToken = typeToken<RevocationRequestData>()
        override val isEnabled = true

        override suspend fun execute(args: ParseRevocationRequestArgs) = handler(args)
    }

/** Lightweight stub for [RevokeTokenCommand]. */
internal fun stubRevokeToken(handler: suspend (RevokeTokenArgs) -> IdkResult<Unit, IdkError>): RevokeTokenCommand =
    object : RevokeTokenCommand {
        override val inputTypeToken = typeToken<RevokeTokenArgs>()
        override val outputTypeToken = typeToken<Unit>()
        override val isEnabled = true

        override suspend fun execute(args: RevokeTokenArgs) = handler(args)
    }

/** Lightweight stub for [ParsePushedAuthorizationRequestCommand]. */
internal fun stubParsePushedAuthorizationRequest(handler: suspend (ParsePushedAuthorizationRequestArgs) -> IdkResult<AuthorizationRequestData, IdkError>,): ParsePushedAuthorizationRequestCommand =
    object : ParsePushedAuthorizationRequestCommand {
        override val inputTypeToken = typeToken<ParsePushedAuthorizationRequestArgs>()
        override val outputTypeToken = typeToken<AuthorizationRequestData>()
        override val isEnabled = true

        override suspend fun execute(args: ParsePushedAuthorizationRequestArgs) = handler(args)
    }

/** Lightweight stub for [VerifyPushedAuthorizationRequestCommand]. */
internal fun stubVerifyPushedAuthorizationRequest(
    handler: suspend (VerifyPushedAuthorizationRequestArgs) -> IdkResult<VerifiedAuthorizationRequest, IdkError>,
): VerifyPushedAuthorizationRequestCommand =
    object : VerifyPushedAuthorizationRequestCommand {
        override val inputTypeToken = typeToken<VerifyPushedAuthorizationRequestArgs>()
        override val outputTypeToken = typeToken<VerifiedAuthorizationRequest>()
        override val isEnabled = true

        override suspend fun execute(args: VerifyPushedAuthorizationRequestArgs) = handler(args)
    }

/** Lightweight stub for [CreateRequestUriCommand]. */
internal fun stubCreateRequestUri(handler: suspend (VerifiedAuthorizationRequest) -> IdkResult<RequestUriData, IdkError>): CreateRequestUriCommand =
    object : CreateRequestUriCommand {
        override val inputTypeToken = typeToken<VerifiedAuthorizationRequest>()
        override val outputTypeToken = typeToken<RequestUriData>()
        override val isEnabled = true

        override suspend fun execute(args: VerifiedAuthorizationRequest) = handler(args)
    }

/** Lightweight stub for [CreatePushedAuthorizationResponseCommand]. */
internal fun stubCreatePushedAuthorizationResponse(
    handler: suspend (CreatePushedAuthorizationResponseArgs) -> IdkResult<PushedAuthorizationResponse, IdkError>,
): CreatePushedAuthorizationResponseCommand =
    object : CreatePushedAuthorizationResponseCommand {
        override val inputTypeToken = typeToken<CreatePushedAuthorizationResponseArgs>()
        override val outputTypeToken = typeToken<PushedAuthorizationResponse>()
        override val isEnabled = true

        override suspend fun execute(args: CreatePushedAuthorizationResponseArgs) = handler(args)
    }

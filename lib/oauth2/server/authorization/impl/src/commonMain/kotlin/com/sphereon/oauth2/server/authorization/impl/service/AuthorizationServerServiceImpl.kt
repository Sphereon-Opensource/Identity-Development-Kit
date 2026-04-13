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

package com.sphereon.oauth2.server.authorization.impl.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.*
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AuthorizationServerService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationServerServiceImpl", exact = true)
class AuthorizationServerServiceImpl(
    private val parseTokenRequestCommand: ParseTokenRequestCommand,
    private val verifyAuthorizationCodeGrantCommand: VerifyAuthorizationCodeGrantCommand,
    private val verifyRefreshTokenGrantCommand: VerifyRefreshTokenGrantCommand,
    private val verifyClientCredentialsGrantCommand: VerifyClientCredentialsGrantCommand,
    private val verifyTokenExchangeGrantCommand: VerifyTokenExchangeGrantCommand,
    private val createAccessTokenCommand: CreateAccessTokenCommand,
    private val createRefreshTokenCommand: CreateRefreshTokenCommand,
    private val createTokenResponseCommand: CreateTokenResponseCommand,
    private val parseAuthorizationRequestCommand: ParseAuthorizationRequestCommand,
    private val verifyAuthorizationRequestCommand: VerifyAuthorizationRequestCommand,
    private val createAuthorizationSessionCommand: CreateAuthorizationSessionCommand,
    private val createAuthorizationCodeCommand: CreateAuthorizationCodeCommand,
    private val createAuthorizationResponseCommand: CreateAuthorizationResponseCommand,
    private val createAuthorizationErrorResponseCommand: CreateAuthorizationErrorResponseCommand,
    private val parsePushedAuthorizationRequestCommand: ParsePushedAuthorizationRequestCommand,
    private val verifyPushedAuthorizationRequestCommand: VerifyPushedAuthorizationRequestCommand,
    private val createRequestUriCommand: CreateRequestUriCommand,
    private val createPushedAuthorizationResponseCommand: CreatePushedAuthorizationResponseCommand,
    private val retrieveAuthorizationRequestByUriCommand: RetrieveAuthorizationRequestByUriCommand,
    private val parseIntrospectionRequestCommand: ParseIntrospectionRequestCommand,
    private val introspectTokenCommand: IntrospectTokenCommand,
    private val parseRevocationRequestCommand: ParseRevocationRequestCommand,
    private val revokeTokenCommand: RevokeTokenCommand,
    private val buildServerMetadataCommand: BuildServerMetadataCommand,
    private val verifyClientAuthenticationCommand: VerifyClientAuthenticationCommand,
    private val createAttestationChallengeCommand: CreateAttestationChallengeCommand,
    private val createIdTokenCommand: CreateIdTokenCommand,
    private val getUserInfoCommand: GetUserInfoCommand,
    private val getJwksCommand: GetJwksCommand
) : AuthorizationServerService {

    @ContributesTo(scope = SessionScope::class)
    interface Component {
        val authorizationServerService: AuthorizationServerService
    }

    inner class CommandsImpl : AuthorizationServerService.Commands {
        override val parseTokenRequest = this@AuthorizationServerServiceImpl.parseTokenRequestCommand
        override val verifyAuthorizationCodeGrant = this@AuthorizationServerServiceImpl.verifyAuthorizationCodeGrantCommand
        override val verifyRefreshTokenGrant = this@AuthorizationServerServiceImpl.verifyRefreshTokenGrantCommand
        override val verifyClientCredentialsGrant = this@AuthorizationServerServiceImpl.verifyClientCredentialsGrantCommand
        override val verifyTokenExchangeGrant = this@AuthorizationServerServiceImpl.verifyTokenExchangeGrantCommand
        override val createAccessToken = this@AuthorizationServerServiceImpl.createAccessTokenCommand
        override val createRefreshToken = this@AuthorizationServerServiceImpl.createRefreshTokenCommand
        override val createTokenResponse = this@AuthorizationServerServiceImpl.createTokenResponseCommand
        override val parseAuthorizationRequest = this@AuthorizationServerServiceImpl.parseAuthorizationRequestCommand
        override val verifyAuthorizationRequest = this@AuthorizationServerServiceImpl.verifyAuthorizationRequestCommand
        override val createAuthorizationSession = this@AuthorizationServerServiceImpl.createAuthorizationSessionCommand
        override val createAuthorizationCode = this@AuthorizationServerServiceImpl.createAuthorizationCodeCommand
        override val createAuthorizationResponse = this@AuthorizationServerServiceImpl.createAuthorizationResponseCommand
        override val createAuthorizationErrorResponse = this@AuthorizationServerServiceImpl.createAuthorizationErrorResponseCommand
        override val parsePushedAuthorizationRequest = this@AuthorizationServerServiceImpl.parsePushedAuthorizationRequestCommand
        override val verifyPushedAuthorizationRequest = this@AuthorizationServerServiceImpl.verifyPushedAuthorizationRequestCommand
        override val createRequestUri = this@AuthorizationServerServiceImpl.createRequestUriCommand
        override val createPushedAuthorizationResponse = this@AuthorizationServerServiceImpl.createPushedAuthorizationResponseCommand
        override val retrieveAuthorizationRequestByUri = this@AuthorizationServerServiceImpl.retrieveAuthorizationRequestByUriCommand
        override val parseIntrospectionRequest = this@AuthorizationServerServiceImpl.parseIntrospectionRequestCommand
        override val introspectToken = this@AuthorizationServerServiceImpl.introspectTokenCommand
        override val parseRevocationRequest = this@AuthorizationServerServiceImpl.parseRevocationRequestCommand
        override val revokeToken = this@AuthorizationServerServiceImpl.revokeTokenCommand
        override val buildServerMetadata = this@AuthorizationServerServiceImpl.buildServerMetadataCommand
        override val verifyClientAuthentication = this@AuthorizationServerServiceImpl.verifyClientAuthenticationCommand
        override val createAttestationChallenge = this@AuthorizationServerServiceImpl.createAttestationChallengeCommand
        override val createIdToken = this@AuthorizationServerServiceImpl.createIdTokenCommand
        override val getUserInfo = this@AuthorizationServerServiceImpl.getUserInfoCommand
        override val getJwks = this@AuthorizationServerServiceImpl.getJwksCommand
    }

    override val commands: AuthorizationServerService.Commands = CommandsImpl()

    // Authorization Endpoint delegations
    override suspend fun parseAuthorizationRequest(
        args: ParseAuthorizationRequestArgs
    ): IdkResult<AuthorizationRequestData, IdkError> = parseAuthorizationRequestCommand.execute(args)

    override suspend fun verifyAuthorizationRequest(
        args: AuthorizationRequestData
    ): IdkResult<VerifiedAuthorizationRequest, IdkError> = verifyAuthorizationRequestCommand.execute(args)

    override suspend fun createAuthorizationSession(
        args: VerifiedAuthorizationRequest
    ): IdkResult<AuthorizationSession, IdkError> = createAuthorizationSessionCommand.execute(args)

    override suspend fun createAuthorizationCode(
        args: CreateAuthorizationCodeArgs
    ): IdkResult<StringResult, IdkError> = createAuthorizationCodeCommand.execute(args)

    override suspend fun createAuthorizationResponse(
        args: CreateAuthorizationResponseArgs
    ): IdkResult<AuthorizationResponseData, IdkError> = createAuthorizationResponseCommand.execute(args)

    override suspend fun createAuthorizationErrorResponse(
        args: CreateAuthorizationErrorResponseArgs
    ): IdkResult<AuthorizationErrorResponseData, IdkError> = createAuthorizationErrorResponseCommand.execute(args)

    // Token Endpoint delegations
    override suspend fun parseTokenRequest(
        args: ParseTokenRequestArgs
    ): IdkResult<TokenRequestData, IdkError> = parseTokenRequestCommand.execute(args)

    override suspend fun verifyAuthorizationCodeGrant(
        args: VerifyAuthorizationCodeGrantArgs
    ): IdkResult<VerifiedAuthorizationCodeGrant, IdkError> = verifyAuthorizationCodeGrantCommand.execute(args)

    override suspend fun verifyRefreshTokenGrant(
        args: VerifyRefreshTokenGrantArgs
    ): IdkResult<VerifiedRefreshTokenGrant, IdkError> = verifyRefreshTokenGrantCommand.execute(args)

    override suspend fun verifyClientCredentialsGrant(
        args: VerifyClientCredentialsGrantArgs
    ): IdkResult<VerifiedClientCredentialsGrant, IdkError> = verifyClientCredentialsGrantCommand.execute(args)

    override suspend fun verifyTokenExchangeGrant(
        args: VerifyTokenExchangeGrantArgs
    ): IdkResult<VerifiedTokenExchangeGrant, IdkError> = verifyTokenExchangeGrantCommand.execute(args)

    override suspend fun createAccessToken(
        args: CreateAccessTokenArgs
    ): IdkResult<StringResult, IdkError> = createAccessTokenCommand.execute(args)

    override suspend fun createRefreshToken(
        args: CreateRefreshTokenArgs
    ): IdkResult<StringResult, IdkError> = createRefreshTokenCommand.execute(args)

    override suspend fun createTokenResponse(
        args: CreateTokenResponseArgs
    ): IdkResult<TokenResponse, IdkError> = createTokenResponseCommand.execute(args)

    // PAR Endpoint delegations
    override suspend fun parsePushedAuthorizationRequest(
        args: ParsePushedAuthorizationRequestArgs
    ): IdkResult<AuthorizationRequestData, IdkError> = parsePushedAuthorizationRequestCommand.execute(args)

    override suspend fun verifyPushedAuthorizationRequest(
        args: VerifyPushedAuthorizationRequestArgs
    ): IdkResult<VerifiedAuthorizationRequest, IdkError> = verifyPushedAuthorizationRequestCommand.execute(args)

    override suspend fun createRequestUri(
        args: VerifiedAuthorizationRequest
    ): IdkResult<RequestUriData, IdkError> = createRequestUriCommand.execute(args)

    override suspend fun createPushedAuthorizationResponse(
        args: CreatePushedAuthorizationResponseArgs
    ): IdkResult<PushedAuthorizationResponse, IdkError> = createPushedAuthorizationResponseCommand.execute(args)

    override suspend fun retrieveAuthorizationRequestByUri(
        requestUri: String
    ): IdkResult<VerifiedAuthorizationRequest, IdkError> = retrieveAuthorizationRequestByUriCommand.execute(RetrieveByRequestUriArgs(requestUri))

    // Introspection Endpoint delegations
    override suspend fun parseIntrospectionRequest(
        args: ParseIntrospectionRequestArgs
    ): IdkResult<IntrospectionRequestData, IdkError> = parseIntrospectionRequestCommand.execute(args)

    override suspend fun introspectToken(
        args: IntrospectTokenArgs
    ): IdkResult<TokenIntrospectionResponse, IdkError> = introspectTokenCommand.execute(args)

    // Revocation Endpoint delegations
    override suspend fun parseRevocationRequest(
        args: ParseRevocationRequestArgs
    ): IdkResult<RevocationRequestData, IdkError> = parseRevocationRequestCommand.execute(args)

    override suspend fun revokeToken(
        args: RevokeTokenArgs
    ): IdkResult<Unit, IdkError> = revokeTokenCommand.execute(args)

    // Discovery Endpoint delegations
    override suspend fun buildServerMetadata(
        args: BuildServerMetadataArgs
    ): IdkResult<AuthorizationServerMetadata, IdkError> = buildServerMetadataCommand.execute(args)

    // Client Authentication delegations
    override suspend fun verifyClientAuthentication(
        args: VerifyClientAuthenticationArgs
    ): IdkResult<VerifiedClientAuthentication, IdkError> = verifyClientAuthenticationCommand.execute(args)

    // Attestation Challenge delegations
    override suspend fun createAttestationChallenge(
        args: CreateAttestationChallengeArgs
    ): IdkResult<AttestationChallengeResponse, IdkError> = createAttestationChallengeCommand.execute(args)

    // OIDC delegations
    override suspend fun createIdToken(
        args: CreateIdTokenArgs
    ): IdkResult<StringResult, IdkError> = createIdTokenCommand.execute(args)

    override suspend fun getUserInfo(
        args: GetUserInfoArgs
    ): IdkResult<UserInfoResponse, IdkError> = getUserInfoCommand.execute(args)

    override suspend fun getJwks(
        args: GetJwksArgs
    ): IdkResult<JwksResult, IdkError> = getJwksCommand.execute(args)
}

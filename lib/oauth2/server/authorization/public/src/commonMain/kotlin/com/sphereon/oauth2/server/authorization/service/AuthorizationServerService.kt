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

package com.sphereon.oauth2.server.authorization.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.crypto.core.jose.Jwk
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
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.authorization.command.IntrospectionRequestData
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.command.RequestUriData
import com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand
import com.sphereon.oauth2.server.authorization.command.RetrieveByRequestUriArgs
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
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantArgs
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Authorization Server service
 *
 * Main service interface for OAuth 2.0 Authorization Server operations.
 * Provides access to all authorization server commands through the commands property.
 *
 * This service follows the Command/Service pattern used throughout IDK:
 * - Service interface declares convenience methods matching command signatures
 * - Service exposes `commands` property with all command interfaces
 * - Allows three usage modes:
 *   1. Service convenience: `service.handleTokenRequest(...)`
 *   2. Command via service: `service.commands.parseTokenRequest.execute(...)`
 *   3. Direct injection: `@Inject parseTokenRequestCommand: ParseTokenRequestCommand`
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AuthorizationServerService", exact = true)
interface AuthorizationServerService {
    // Token Endpoint
    suspend fun parseTokenRequest(args: ParseTokenRequestArgs): IdkResult<TokenRequestData, IdkError>

    suspend fun verifyAuthorizationCodeGrant(args: VerifyAuthorizationCodeGrantArgs): IdkResult<VerifiedAuthorizationCodeGrant, IdkError>

    suspend fun verifyRefreshTokenGrant(args: VerifyRefreshTokenGrantArgs): IdkResult<VerifiedRefreshTokenGrant, IdkError>

    suspend fun verifyClientCredentialsGrant(args: VerifyClientCredentialsGrantArgs): IdkResult<VerifiedClientCredentialsGrant, IdkError>

    suspend fun verifyTokenExchangeGrant(args: VerifyTokenExchangeGrantArgs): IdkResult<VerifiedTokenExchangeGrant, IdkError>

    suspend fun verifyPreAuthorizedCodeGrant(args: VerifyPreAuthCodeArgs): IdkResult<VerifiedPreAuthCodeGrant, IdkError>

    suspend fun createAccessToken(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError>

    suspend fun createRefreshToken(args: CreateRefreshTokenArgs): IdkResult<StringResult, IdkError>

    suspend fun createTokenResponse(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError>

    // Authorization Endpoint
    suspend fun parseAuthorizationRequest(args: ParseAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError>

    suspend fun verifyAuthorizationRequest(args: AuthorizationRequestData): IdkResult<VerifiedAuthorizationRequest, IdkError>

    suspend fun createAuthorizationSession(args: VerifiedAuthorizationRequest): IdkResult<AuthorizationSession, IdkError>

    suspend fun createAuthorizationCode(args: CreateAuthorizationCodeArgs): IdkResult<StringResult, IdkError>

    suspend fun createAuthorizationResponse(args: CreateAuthorizationResponseArgs): IdkResult<AuthorizationResponseData, IdkError>

    suspend fun createAuthorizationErrorResponse(args: CreateAuthorizationErrorResponseArgs): IdkResult<AuthorizationErrorResponseData, IdkError>

    // PAR Endpoint
    suspend fun parsePushedAuthorizationRequest(args: ParsePushedAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError>

    suspend fun verifyPushedAuthorizationRequest(args: VerifyPushedAuthorizationRequestArgs): IdkResult<VerifiedAuthorizationRequest, IdkError>

    suspend fun createRequestUri(args: VerifiedAuthorizationRequest): IdkResult<RequestUriData, IdkError>

    suspend fun createPushedAuthorizationResponse(args: CreatePushedAuthorizationResponseArgs): IdkResult<PushedAuthorizationResponse, IdkError>

    suspend fun retrieveAuthorizationRequestByUri(requestUri: String): IdkResult<VerifiedAuthorizationRequest, IdkError>

    // Introspection Endpoint
    suspend fun parseIntrospectionRequest(args: ParseIntrospectionRequestArgs): IdkResult<IntrospectionRequestData, IdkError>

    suspend fun introspectToken(args: IntrospectTokenArgs): IdkResult<TokenIntrospectionResponse, IdkError>

    // Revocation Endpoint (RFC 7009)
    suspend fun parseRevocationRequest(args: ParseRevocationRequestArgs): IdkResult<RevocationRequestData, IdkError>

    suspend fun revokeToken(args: RevokeTokenArgs): IdkResult<Unit, IdkError>

    // Discovery Endpoint (RFC 8414)
    suspend fun buildServerMetadata(args: BuildServerMetadataArgs): IdkResult<AuthorizationServerMetadata, IdkError>

    // Client Authentication (draft-ietf-oauth-attestation-based-client-auth)
    suspend fun verifyClientAuthentication(args: VerifyClientAuthenticationArgs): IdkResult<VerifiedClientAuthentication, IdkError>

    // Attestation Challenge
    suspend fun createAttestationChallenge(args: CreateAttestationChallengeArgs): IdkResult<AttestationChallengeResponse, IdkError>

    // OIDC Endpoints
    suspend fun createIdToken(args: CreateIdTokenArgs): IdkResult<StringResult, IdkError>

    suspend fun getUserInfo(args: GetUserInfoArgs): IdkResult<UserInfoResponse, IdkError>

    suspend fun getJwks(args: GetJwksArgs): IdkResult<JwksResult, IdkError>

    /**
     * Access to all authorization server commands
     */
    val commands: Commands

    /**
     * All authorization server commands
     */
    interface Commands {
        // Token Endpoint Commands
        val parseTokenRequest: ParseTokenRequestCommand
        val verifyAuthorizationCodeGrant: VerifyAuthorizationCodeGrantCommand
        val verifyRefreshTokenGrant: VerifyRefreshTokenGrantCommand
        val verifyClientCredentialsGrant: VerifyClientCredentialsGrantCommand
        val verifyTokenExchangeGrant: VerifyTokenExchangeGrantCommand
        val verifyPreAuthorizedCodeGrant: VerifyPreAuthorizedCodeGrantCommand
        val createAccessToken: CreateAccessTokenCommand
        val createRefreshToken: CreateRefreshTokenCommand
        val createTokenResponse: CreateTokenResponseCommand

        // Authorization Endpoint Commands
        val parseAuthorizationRequest: ParseAuthorizationRequestCommand
        val verifyAuthorizationRequest: VerifyAuthorizationRequestCommand
        val createAuthorizationSession: CreateAuthorizationSessionCommand
        val createAuthorizationCode: CreateAuthorizationCodeCommand
        val createAuthorizationResponse: CreateAuthorizationResponseCommand
        val createAuthorizationErrorResponse: CreateAuthorizationErrorResponseCommand

        // PAR Endpoint Commands
        val parsePushedAuthorizationRequest: ParsePushedAuthorizationRequestCommand
        val verifyPushedAuthorizationRequest: VerifyPushedAuthorizationRequestCommand
        val createRequestUri: CreateRequestUriCommand
        val createPushedAuthorizationResponse: CreatePushedAuthorizationResponseCommand
        val retrieveAuthorizationRequestByUri: RetrieveAuthorizationRequestByUriCommand

        // Introspection Endpoint Commands
        val parseIntrospectionRequest: ParseIntrospectionRequestCommand
        val introspectToken: IntrospectTokenCommand

        // Revocation Endpoint Commands
        val parseRevocationRequest: ParseRevocationRequestCommand
        val revokeToken: RevokeTokenCommand

        // Discovery Endpoint Commands
        val buildServerMetadata: BuildServerMetadataCommand

        // Client Authentication Commands
        val verifyClientAuthentication: VerifyClientAuthenticationCommand

        // Attestation Challenge Commands
        val createAttestationChallenge: CreateAttestationChallengeCommand

        // OIDC Commands
        val createIdToken: CreateIdTokenCommand
        val getUserInfo: GetUserInfoCommand
        val getJwks: GetJwksCommand
    }
}

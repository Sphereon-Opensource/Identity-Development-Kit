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

package com.sphereon.oauth2.server.authorization.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.model.*
import com.sphereon.oauth2.server.authorization.command.*
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService

/** Any access to the AS command set means the handler looked up a token before checking auth. */
internal object NoCredentialsAuthorizationServerService : AuthorizationServerService {
    override suspend fun parseTokenRequest(args: ParseTokenRequestArgs): IdkResult<TokenRequestData, IdkError> = unexpected()
    override suspend fun verifyAuthorizationCodeGrant(args: VerifyAuthorizationCodeGrantArgs): IdkResult<VerifiedAuthorizationCodeGrant, IdkError> = unexpected()
    override suspend fun verifyRefreshTokenGrant(args: VerifyRefreshTokenGrantArgs): IdkResult<VerifiedRefreshTokenGrant, IdkError> = unexpected()
    override suspend fun verifyClientCredentialsGrant(args: VerifyClientCredentialsGrantArgs): IdkResult<VerifiedClientCredentialsGrant, IdkError> = unexpected()
    override suspend fun verifyPreAuthorizedCodeGrant(args: VerifyPreAuthCodeArgs): IdkResult<VerifiedPreAuthCodeGrant, IdkError> = unexpected()
    override suspend fun createAccessToken(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError> = unexpected()
    override suspend fun createRefreshToken(args: CreateRefreshTokenArgs): IdkResult<StringResult, IdkError> = unexpected()
    override suspend fun createTokenResponse(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError> = unexpected()
    override suspend fun parseAuthorizationRequest(args: ParseAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError> = unexpected()
    override suspend fun verifyAuthorizationRequest(args: AuthorizationRequestData): IdkResult<VerifiedAuthorizationRequest, IdkError> = unexpected()
    override suspend fun createAuthorizationSession(args: VerifiedAuthorizationRequest): IdkResult<AuthorizationSession, IdkError> = unexpected()
    override suspend fun createAuthorizationCode(args: CreateAuthorizationCodeArgs): IdkResult<StringResult, IdkError> = unexpected()
    override suspend fun createAuthorizationResponse(args: CreateAuthorizationResponseArgs): IdkResult<AuthorizationResponseData, IdkError> = unexpected()
    override suspend fun createAuthorizationErrorResponse(args: CreateAuthorizationErrorResponseArgs): IdkResult<AuthorizationErrorResponseData, IdkError> = unexpected()
    override suspend fun parsePushedAuthorizationRequest(args: ParsePushedAuthorizationRequestArgs): IdkResult<AuthorizationRequestData, IdkError> = unexpected()
    override suspend fun verifyPushedAuthorizationRequest(args: VerifyPushedAuthorizationRequestArgs): IdkResult<VerifiedAuthorizationRequest, IdkError> = unexpected()
    override suspend fun createRequestUri(args: VerifiedAuthorizationRequest): IdkResult<RequestUriData, IdkError> = unexpected()
    override suspend fun createPushedAuthorizationResponse(args: CreatePushedAuthorizationResponseArgs): IdkResult<PushedAuthorizationResponse, IdkError> = unexpected()
    override suspend fun retrieveAuthorizationRequestByUri(requestUri: String): IdkResult<VerifiedAuthorizationRequest, IdkError> = unexpected()
    override suspend fun parseIntrospectionRequest(args: ParseIntrospectionRequestArgs): IdkResult<IntrospectionRequestData, IdkError> = unexpected()
    override suspend fun introspectToken(args: IntrospectTokenArgs): IdkResult<TokenIntrospectionResponse, IdkError> = unexpected()
    override suspend fun parseRevocationRequest(args: ParseRevocationRequestArgs): IdkResult<RevocationRequestData, IdkError> = unexpected()
    override suspend fun revokeToken(args: RevokeTokenArgs): IdkResult<Unit, IdkError> = unexpected()
    override suspend fun buildServerMetadata(args: BuildServerMetadataArgs): IdkResult<AuthorizationServerMetadata, IdkError> = unexpected()
    override suspend fun verifyClientAuthentication(args: VerifyClientAuthenticationArgs): IdkResult<VerifiedClientAuthentication, IdkError> = unexpected()
    override suspend fun createAttestationChallenge(args: CreateAttestationChallengeArgs): IdkResult<AttestationChallengeResponse, IdkError> = unexpected()
    override suspend fun createIdToken(args: CreateIdTokenArgs): IdkResult<StringResult, IdkError> = unexpected()
    override suspend fun getUserInfo(args: GetUserInfoArgs): IdkResult<UserInfoResponse, IdkError> = unexpected()
    override suspend fun getJwks(args: GetJwksArgs): IdkResult<JwksResult, IdkError> = unexpected()

    override val commands: AuthorizationServerService.Commands
        get() = error("The authorization command set must not be accessed before client authentication")

    private fun <T> unexpected(): T = error("An authorization service operation was unexpected for an unauthenticated request")
}

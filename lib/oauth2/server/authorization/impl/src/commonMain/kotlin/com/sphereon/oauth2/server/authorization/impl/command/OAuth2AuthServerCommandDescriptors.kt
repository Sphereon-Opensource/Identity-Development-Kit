/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand
import com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.impl.command.attestation.CreateAttestationChallengeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationCodeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationErrorResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationSessionCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.ParseAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.VerifyAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.clientauth.VerifyClientAuthenticationCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.discovery.BuildServerMetadataCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.iae.HandleIaeFollowUpCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.iae.HandleIaeInitialRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.introspection.AuthServerIntrospectTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.introspection.ParseIntrospectionRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.oidc.CreateIdTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.oidc.GetJwksCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.oidc.GetUserInfoCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.CreatePushedAuthorizationResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.CreateRequestUriCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.ParsePushedAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.RetrieveAuthorizationRequestByUriCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.VerifyPushedAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.revocation.ParseRevocationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.revocation.RevokeTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateAccessTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateRefreshTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateTokenResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.ParseTokenRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyAuthorizationCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyClientCredentialsGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyPreAuthorizedCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyRefreshTokenGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyTokenExchangeGrantCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface OAuth2AuthServerCommandDescriptors {
    // PAR commands
    @Provides @IntoMap
    @StringKey(RetrieveAuthorizationRequestByUriCommand.COMMAND_ID)
    fun retrieveAuthorizationRequestByUri(impl: RetrieveAuthorizationRequestByUriCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreatePushedAuthorizationResponseCommand.COMMAND_ID)
    fun createPushedAuthorizationResponse(impl: CreatePushedAuthorizationResponseCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateRequestUriCommand.COMMAND_ID)
    fun createRequestUri(impl: CreateRequestUriCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyPushedAuthorizationRequestCommand.COMMAND_ID)
    fun verifyPushedAuthorizationRequest(impl: VerifyPushedAuthorizationRequestCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ParsePushedAuthorizationRequestCommand.COMMAND_ID)
    fun parsePushedAuthorizationRequest(impl: ParsePushedAuthorizationRequestCommandImpl): ServiceCommand<*, *> = impl

    // Introspection commands
    @Provides @IntoMap
    @StringKey(IntrospectTokenCommand.COMMAND_ID)
    fun authServerIntrospectToken(impl: AuthServerIntrospectTokenCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ParseIntrospectionRequestCommand.COMMAND_ID)
    fun parseIntrospectionRequest(impl: ParseIntrospectionRequestCommandImpl): ServiceCommand<*, *> = impl

    // Token commands
    @Provides @IntoMap
    @StringKey(CreateRefreshTokenCommand.COMMAND_ID)
    fun createRefreshToken(impl: CreateRefreshTokenCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAccessTokenCommand.COMMAND_ID)
    fun createAccessToken(impl: CreateAccessTokenCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateTokenResponseCommand.COMMAND_ID)
    fun createTokenResponse(impl: CreateTokenResponseCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyClientCredentialsGrantCommand.COMMAND_ID)
    fun verifyClientCredentialsGrant(impl: VerifyClientCredentialsGrantCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyTokenExchangeGrantCommand.COMMAND_ID)
    fun verifyTokenExchangeGrant(impl: VerifyTokenExchangeGrantCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyRefreshTokenGrantCommand.COMMAND_ID)
    fun verifyRefreshTokenGrant(impl: VerifyRefreshTokenGrantCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyAuthorizationCodeGrantCommand.COMMAND_ID)
    fun verifyAuthorizationCodeGrant(impl: VerifyAuthorizationCodeGrantCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyPreAuthorizedCodeGrantCommand.COMMAND_ID)
    fun verifyPreAuthorizedCodeGrant(impl: VerifyPreAuthorizedCodeGrantCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ParseTokenRequestCommand.COMMAND_ID)
    fun parseTokenRequest(impl: ParseTokenRequestCommandImpl): ServiceCommand<*, *> = impl

    // Authorization commands
    @Provides @IntoMap
    @StringKey(CreateAuthorizationCodeCommand.COMMAND_ID)
    fun createAuthorizationCode(impl: CreateAuthorizationCodeCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationErrorResponseCommand.COMMAND_ID)
    fun createAuthorizationErrorResponse(impl: CreateAuthorizationErrorResponseCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationResponseCommand.COMMAND_ID)
    fun createAuthorizationResponse(impl: CreateAuthorizationResponseCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationSessionCommand.COMMAND_ID)
    fun createAuthorizationSession(impl: CreateAuthorizationSessionCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyAuthorizationRequestCommand.COMMAND_ID)
    fun verifyAuthorizationRequest(impl: VerifyAuthorizationRequestCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ParseAuthorizationRequestCommand.COMMAND_ID)
    fun parseAuthorizationRequest(impl: ParseAuthorizationRequestCommandImpl): ServiceCommand<*, *> = impl

    // Revocation commands
    @Provides @IntoMap
    @StringKey(ParseRevocationRequestCommand.COMMAND_ID)
    fun parseRevocationRequest(impl: ParseRevocationRequestCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(RevokeTokenCommand.COMMAND_ID)
    fun revokeToken(impl: RevokeTokenCommandImpl): ServiceCommand<*, *> = impl

    // Discovery commands
    @Provides @IntoMap
    @StringKey(BuildServerMetadataCommand.COMMAND_ID)
    fun buildServerMetadata(impl: BuildServerMetadataCommandImpl): ServiceCommand<*, *> = impl

    // Client authentication commands
    @Provides @IntoMap
    @StringKey(VerifyClientAuthenticationCommand.COMMAND_ID)
    fun verifyClientAuthentication(impl: VerifyClientAuthenticationCommandImpl): ServiceCommand<*, *> = impl

    // Attestation challenge commands
    @Provides @IntoMap
    @StringKey(CreateAttestationChallengeCommand.COMMAND_ID)
    fun createAttestationChallenge(impl: CreateAttestationChallengeCommandImpl): ServiceCommand<*, *> = impl

    // OIDC commands
    @Provides @IntoMap
    @StringKey(CreateIdTokenCommand.COMMAND_ID)
    fun createIdToken(impl: CreateIdTokenCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetUserInfoCommand.COMMAND_ID)
    fun getUserInfo(impl: GetUserInfoCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetJwksCommand.COMMAND_ID)
    fun getJwks(impl: GetJwksCommandImpl): ServiceCommand<*, *> = impl

    // IAE (Interactive Authorization Endpoint) commands
    @Provides @IntoMap
    @StringKey(HandleIaeInitialRequestCommand.COMMAND_ID)
    fun handleIaeInitialRequest(impl: HandleIaeInitialRequestCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(HandleIaeFollowUpCommand.COMMAND_ID)
    fun handleIaeFollowUp(impl: HandleIaeFollowUpCommandImpl): ServiceCommand<*, *> = impl
}

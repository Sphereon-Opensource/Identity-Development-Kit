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

import com.sphereon.core.api.error.IdkError
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
import com.sphereon.oauth2.server.authorization.command.RotateSigningKeyCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthorizedCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.command.clientauth.VerifyAttestationClientAuthCommand
import com.sphereon.oauth2.server.authorization.command.device.IssueDeviceAuthorizationCommand
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestCommand
import com.sphereon.oauth2.server.authorization.command.federation.ExchangeCodeAndExtractClaimsCommand
import com.sphereon.oauth2.server.authorization.command.federation.GetAuthenticatedUserCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationCallbackCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleFederationOutcomeCommand
import com.sphereon.oauth2.server.authorization.command.federation.HandleReconciliationOutcomeCommand
import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestCommand
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.revocation.HandleRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestCommand
import com.sphereon.oauth2.server.authorization.impl.command.admin.RotateSigningKeyCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.attestation.CreateAttestationChallengeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationCodeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationErrorResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationSessionCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.ParseAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.VerifyAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.clientauth.VerifyAttestationClientAuthCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.clientauth.VerifyClientAuthenticationCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.device.IssueDeviceAuthorizationCommandImpl
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
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyDeviceCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyPreAuthorizedCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyRefreshTokenGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyTokenExchangeGrantCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey
import com.sphereon.oauth2.server.authorization.command.federation.GetUserInfoCommand as FederationGetUserInfoCommand

@ContributesTo(SessionScope::class)
interface OAuth2AuthServerCommandDescriptors {
    // PAR commands
    @Provides @IntoMap
    @StringKey(RetrieveAuthorizationRequestByUriCommand.COMMAND_ID)
    fun retrieveAuthorizationRequestByUri(impl: RetrieveAuthorizationRequestByUriCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreatePushedAuthorizationResponseCommand.COMMAND_ID)
    fun createPushedAuthorizationResponse(impl: CreatePushedAuthorizationResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateRequestUriCommand.COMMAND_ID)
    fun createRequestUri(impl: CreateRequestUriCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyPushedAuthorizationRequestCommand.COMMAND_ID)
    fun verifyPushedAuthorizationRequest(impl: VerifyPushedAuthorizationRequestCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParsePushedAuthorizationRequestCommand.COMMAND_ID)
    fun parsePushedAuthorizationRequest(impl: ParsePushedAuthorizationRequestCommandImpl): ServiceCommand<*, *, *> = impl

    // Introspection commands
    @Provides @IntoMap
    @StringKey(IntrospectTokenCommand.COMMAND_ID)
    fun authServerIntrospectToken(impl: AuthServerIntrospectTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParseIntrospectionRequestCommand.COMMAND_ID)
    fun parseIntrospectionRequest(impl: ParseIntrospectionRequestCommandImpl): ServiceCommand<*, *, *> = impl

    // Token commands
    @Provides @IntoMap
    @StringKey(CreateRefreshTokenCommand.COMMAND_ID)
    fun createRefreshToken(impl: CreateRefreshTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAccessTokenCommand.COMMAND_ID)
    fun createAccessToken(impl: CreateAccessTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateTokenResponseCommand.COMMAND_ID)
    fun createTokenResponse(impl: CreateTokenResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyClientCredentialsGrantCommand.COMMAND_ID)
    fun verifyClientCredentialsGrant(impl: VerifyClientCredentialsGrantCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyTokenExchangeGrantCommand.COMMAND_ID)
    fun verifyTokenExchangeGrant(impl: VerifyTokenExchangeGrantCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyRefreshTokenGrantCommand.COMMAND_ID)
    fun verifyRefreshTokenGrant(impl: VerifyRefreshTokenGrantCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyAuthorizationCodeGrantCommand.COMMAND_ID)
    fun verifyAuthorizationCodeGrant(impl: VerifyAuthorizationCodeGrantCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyPreAuthorizedCodeGrantCommand.COMMAND_ID)
    fun verifyPreAuthorizedCodeGrant(impl: VerifyPreAuthorizedCodeGrantCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyDeviceCodeGrantCommand.COMMAND_ID)
    fun verifyDeviceCodeGrant(impl: VerifyDeviceCodeGrantCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(IssueDeviceAuthorizationCommand.COMMAND_ID)
    fun issueDeviceAuthorization(impl: IssueDeviceAuthorizationCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParseTokenRequestCommand.COMMAND_ID)
    fun parseTokenRequest(impl: ParseTokenRequestCommandImpl): ServiceCommand<*, *, *> = impl

    // Authorization commands
    @Provides @IntoMap
    @StringKey(CreateAuthorizationCodeCommand.COMMAND_ID)
    fun createAuthorizationCode(impl: CreateAuthorizationCodeCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationErrorResponseCommand.COMMAND_ID)
    fun createAuthorizationErrorResponse(impl: CreateAuthorizationErrorResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationResponseCommand.COMMAND_ID)
    fun createAuthorizationResponse(impl: CreateAuthorizationResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationSessionCommand.COMMAND_ID)
    fun createAuthorizationSession(impl: CreateAuthorizationSessionCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyAuthorizationRequestCommand.COMMAND_ID)
    fun verifyAuthorizationRequest(impl: VerifyAuthorizationRequestCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParseAuthorizationRequestCommand.COMMAND_ID)
    fun parseAuthorizationRequest(impl: ParseAuthorizationRequestCommandImpl): ServiceCommand<*, *, *> = impl

    // Revocation commands
    @Provides @IntoMap
    @StringKey(ParseRevocationRequestCommand.COMMAND_ID)
    fun parseRevocationRequest(impl: ParseRevocationRequestCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RevokeTokenCommand.COMMAND_ID)
    fun revokeToken(impl: RevokeTokenCommandImpl): ServiceCommand<*, *, *> = impl

    // Discovery commands
    @Provides @IntoMap
    @StringKey(BuildServerMetadataCommand.COMMAND_ID)
    fun buildServerMetadata(impl: BuildServerMetadataCommandImpl): ServiceCommand<*, *, *> = impl

    // Client authentication commands
    @Provides @IntoMap
    @StringKey(VerifyClientAuthenticationCommand.COMMAND_ID)
    fun verifyClientAuthentication(impl: VerifyClientAuthenticationCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyAttestationClientAuthCommand.COMMAND_ID)
    fun verifyAttestationClientAuth(impl: VerifyAttestationClientAuthCommandImpl): ServiceCommand<*, *, *> = impl

    // Attestation challenge commands
    @Provides @IntoMap
    @StringKey(CreateAttestationChallengeCommand.COMMAND_ID)
    fun createAttestationChallenge(impl: CreateAttestationChallengeCommandImpl): ServiceCommand<*, *, *> = impl

    // OIDC commands
    @Provides @IntoMap
    @StringKey(CreateIdTokenCommand.COMMAND_ID)
    fun createIdToken(impl: CreateIdTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetUserInfoCommand.COMMAND_ID)
    fun getUserInfo(impl: GetUserInfoCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetJwksCommand.COMMAND_ID)
    fun getJwks(impl: GetJwksCommandImpl): ServiceCommand<*, *, *> = impl

    // IAE (Interactive Authorization Endpoint) commands
    @Provides @IntoMap
    @StringKey(HandleIaeInitialRequestCommand.COMMAND_ID)
    fun handleIaeInitialRequest(impl: HandleIaeInitialRequestCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleIaeFollowUpCommand.COMMAND_ID)
    fun handleIaeFollowUp(impl: HandleIaeFollowUpCommandImpl): ServiceCommand<*, *, *> = impl

    // Federated authentication commands. Bound through the interface (not impl) so test
    // doubles contributed via @ContributesBinding(replaces = [...Impl::class]) also win the
    // registry lookup, per feedback_tests_must_compose_real_di_graph.md.
    @Provides @IntoMap
    @StringKey(InitiateProviderAuthenticationCommand.COMMAND_ID)
    fun initiateProviderAuthentication(impl: InitiateProviderAuthenticationCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleFederationCallbackCommand.COMMAND_ID)
    fun handleFederationCallback(impl: HandleFederationCallbackCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ExchangeCodeAndExtractClaimsCommand.COMMAND_ID)
    fun exchangeCodeAndExtractClaims(impl: ExchangeCodeAndExtractClaimsCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleFederationOutcomeCommand.COMMAND_ID)
    fun handleFederationOutcome(impl: HandleFederationOutcomeCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleReconciliationOutcomeCommand.COMMAND_ID)
    fun handleReconciliationOutcome(impl: HandleReconciliationOutcomeCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetAuthenticatedUserCommand.COMMAND_ID)
    fun getAuthenticatedUser(impl: GetAuthenticatedUserCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FederationGetUserInfoCommand.COMMAND_ID)
    fun getFederationUserInfo(impl: FederationGetUserInfoCommand): ServiceCommand<*, *, *> = impl

    // OAuth2 endpoint orchestration commands (Phase 3c-1 facade decomposition). Bound through
    // the interface so test doubles contributed via @ContributesBinding(replaces = [...Impl::class])
    // also win the registry lookup, matching the federation-command pattern above.
    @Provides @IntoMap
    @StringKey(HandleTokenRequestCommand.COMMAND_ID)
    fun handleTokenRequest(impl: HandleTokenRequestCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandlePushedAuthorizationRequestCommand.COMMAND_ID)
    fun handlePushedAuthorizationRequest(impl: HandlePushedAuthorizationRequestCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleIntrospectionRequestCommand.COMMAND_ID)
    fun handleIntrospectionRequest(impl: HandleIntrospectionRequestCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleRevocationRequestCommand.COMMAND_ID)
    fun handleRevocationRequest(impl: HandleRevocationRequestCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleDiscoveryRequestCommand.COMMAND_ID)
    fun handleDiscoveryRequest(impl: HandleDiscoveryRequestCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleUserInfoRequestCommand.COMMAND_ID)
    fun handleUserInfoRequest(impl: HandleUserInfoRequestCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleJwksRequestCommand.COMMAND_ID)
    fun handleJwksRequest(impl: HandleJwksRequestCommand): ServiceCommand<*, *, *> = impl

    // Admin commands — first-boot signing-key provisioning + operator-triggered rotation.
    // Without this descriptor, AppCommandInvoker.resolve(RotateSigningKeyCommand.COMMAND_ID)
    // returns null even though the typed binding exists; the bridge's signing-key bootstrap
    // (and any future admin-rotation endpoint) cannot find the command at runtime.
    @Provides @IntoMap
    @StringKey(RotateSigningKeyCommand.COMMAND_ID)
    fun rotateSigningKey(impl: RotateSigningKeyCommandImpl): ServiceCommand<*, *, *> = impl
}

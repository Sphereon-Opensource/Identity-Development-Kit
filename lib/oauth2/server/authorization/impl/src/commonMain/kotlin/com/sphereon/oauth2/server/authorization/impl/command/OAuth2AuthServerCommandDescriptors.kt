package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationCodeCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationSessionCommand
import com.sphereon.oauth2.server.authorization.command.CreatePushedAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.command.CreateRequestUriCommand
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand
import com.sphereon.oauth2.server.authorization.command.ParseAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestCommand
import com.sphereon.oauth2.server.authorization.command.RevokeTokenCommand
import com.sphereon.oauth2.server.authorization.command.ParseIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParsePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand
import com.sphereon.oauth2.server.authorization.command.RetrieveAuthorizationRequestByUriCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyPushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantCommand
import com.sphereon.oauth2.server.authorization.command.VerifyTokenExchangeGrantCommand
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.command.GetUserInfoCommand
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationCodeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationErrorResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.CreateAuthorizationSessionCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.ParseAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.authorization.VerifyAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.attestation.CreateAttestationChallengeCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.clientauth.VerifyClientAuthenticationCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.discovery.BuildServerMetadataCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.introspection.AuthServerIntrospectTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.introspection.ParseIntrospectionRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.revocation.ParseRevocationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.revocation.RevokeTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.CreatePushedAuthorizationResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.CreateRequestUriCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.ParsePushedAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.RetrieveAuthorizationRequestByUriCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.par.VerifyPushedAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateAccessTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateRefreshTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.CreateTokenResponseCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.ParseTokenRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyAuthorizationCodeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyClientCredentialsGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyRefreshTokenGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyTokenExchangeGrantCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.oidc.CreateIdTokenCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.oidc.GetUserInfoCommandImpl
import com.sphereon.oauth2.server.authorization.impl.command.oidc.GetJwksCommandImpl
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface OAuth2AuthServerCommandDescriptors {

    // PAR commands
    @Provides @IntoSet
    fun retrieveAuthorizationRequestByUri(impl: Lazy<RetrieveAuthorizationRequestByUriCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(RetrieveAuthorizationRequestByUriCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createPushedAuthorizationResponse(impl: Lazy<CreatePushedAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreatePushedAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createRequestUri(impl: Lazy<CreateRequestUriCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateRequestUriCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyPushedAuthorizationRequest(impl: Lazy<VerifyPushedAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyPushedAuthorizationRequestCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun parsePushedAuthorizationRequest(impl: Lazy<ParsePushedAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParsePushedAuthorizationRequestCommand.COMMAND_ID) { impl.value }

    // Introspection commands
    @Provides @IntoSet
    fun authServerIntrospectToken(impl: Lazy<AuthServerIntrospectTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(IntrospectTokenCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun parseIntrospectionRequest(impl: Lazy<ParseIntrospectionRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseIntrospectionRequestCommand.COMMAND_ID) { impl.value }

    // Token commands
    @Provides @IntoSet
    fun createRefreshToken(impl: Lazy<CreateRefreshTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateRefreshTokenCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createAccessToken(impl: Lazy<CreateAccessTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAccessTokenCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createTokenResponse(impl: Lazy<CreateTokenResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateTokenResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyClientCredentialsGrant(impl: Lazy<VerifyClientCredentialsGrantCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyClientCredentialsGrantCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyTokenExchangeGrant(impl: Lazy<VerifyTokenExchangeGrantCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyTokenExchangeGrantCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyRefreshTokenGrant(impl: Lazy<VerifyRefreshTokenGrantCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyRefreshTokenGrantCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyAuthorizationCodeGrant(impl: Lazy<VerifyAuthorizationCodeGrantCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyAuthorizationCodeGrantCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun parseTokenRequest(impl: Lazy<ParseTokenRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseTokenRequestCommand.COMMAND_ID) { impl.value }

    // Authorization commands
    @Provides @IntoSet
    fun createAuthorizationCode(impl: Lazy<CreateAuthorizationCodeCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthorizationCodeCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createAuthorizationErrorResponse(impl: Lazy<CreateAuthorizationErrorResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthorizationErrorResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createAuthorizationResponse(impl: Lazy<CreateAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createAuthorizationSession(impl: Lazy<CreateAuthorizationSessionCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthorizationSessionCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyAuthorizationRequest(impl: Lazy<VerifyAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyAuthorizationRequestCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun parseAuthorizationRequest(impl: Lazy<ParseAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseAuthorizationRequestCommand.COMMAND_ID) { impl.value }

    // Revocation commands
    @Provides @IntoSet
    fun parseRevocationRequest(impl: Lazy<ParseRevocationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseRevocationRequestCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun revokeToken(impl: Lazy<RevokeTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(RevokeTokenCommand.COMMAND_ID) { impl.value }

    // Discovery commands
    @Provides @IntoSet
    fun buildServerMetadata(impl: Lazy<BuildServerMetadataCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BuildServerMetadataCommand.COMMAND_ID) { impl.value }

    // Client authentication commands
    @Provides @IntoSet
    fun verifyClientAuthentication(impl: Lazy<VerifyClientAuthenticationCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyClientAuthenticationCommand.COMMAND_ID) { impl.value }

    // Attestation challenge commands
    @Provides @IntoSet
    fun createAttestationChallenge(impl: Lazy<CreateAttestationChallengeCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAttestationChallengeCommand.COMMAND_ID) { impl.value }

    // OIDC commands
    @Provides @IntoSet
    fun createIdToken(impl: Lazy<CreateIdTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateIdTokenCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun getUserInfo(impl: Lazy<GetUserInfoCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GetUserInfoCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun getJwks(impl: Lazy<GetJwksCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GetJwksCommand.COMMAND_ID) { impl.value }
}

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface OAuth2AuthServerCommandBindings {

    // Authorization endpoint commands
    @Provides
    fun parseAuthorizationRequest(registry: SessionScopedCommandRegistry): ParseAuthorizationRequestCommand =
        registry.get(ParseAuthorizationRequestCommand.COMMAND_ID) as? ParseAuthorizationRequestCommand
            ?: error("No binding for ${ParseAuthorizationRequestCommand.COMMAND_ID}")

    @Provides
    fun verifyAuthorizationRequest(registry: SessionScopedCommandRegistry): VerifyAuthorizationRequestCommand =
        registry.get(VerifyAuthorizationRequestCommand.COMMAND_ID) as? VerifyAuthorizationRequestCommand
            ?: error("No binding for ${VerifyAuthorizationRequestCommand.COMMAND_ID}")

    @Provides
    fun createAuthorizationSession(registry: SessionScopedCommandRegistry): CreateAuthorizationSessionCommand =
        registry.get(CreateAuthorizationSessionCommand.COMMAND_ID) as? CreateAuthorizationSessionCommand
            ?: error("No binding for ${CreateAuthorizationSessionCommand.COMMAND_ID}")

    @Provides
    fun createAuthorizationCode(registry: SessionScopedCommandRegistry): CreateAuthorizationCodeCommand =
        registry.get(CreateAuthorizationCodeCommand.COMMAND_ID) as? CreateAuthorizationCodeCommand
            ?: error("No binding for ${CreateAuthorizationCodeCommand.COMMAND_ID}")

    @Provides
    fun createAuthorizationResponse(registry: SessionScopedCommandRegistry): CreateAuthorizationResponseCommand =
        registry.get(CreateAuthorizationResponseCommand.COMMAND_ID) as? CreateAuthorizationResponseCommand
            ?: error("No binding for ${CreateAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun createAuthorizationErrorResponse(registry: SessionScopedCommandRegistry): CreateAuthorizationErrorResponseCommand =
        registry.get(CreateAuthorizationErrorResponseCommand.COMMAND_ID) as? CreateAuthorizationErrorResponseCommand
            ?: error("No binding for ${CreateAuthorizationErrorResponseCommand.COMMAND_ID}")

    // Token endpoint commands
    @Provides
    fun parseTokenRequest(registry: SessionScopedCommandRegistry): ParseTokenRequestCommand =
        registry.get(ParseTokenRequestCommand.COMMAND_ID) as? ParseTokenRequestCommand
            ?: error("No binding for ${ParseTokenRequestCommand.COMMAND_ID}")

    @Provides
    fun verifyAuthorizationCodeGrant(registry: SessionScopedCommandRegistry): VerifyAuthorizationCodeGrantCommand =
        registry.get(VerifyAuthorizationCodeGrantCommand.COMMAND_ID) as? VerifyAuthorizationCodeGrantCommand
            ?: error("No binding for ${VerifyAuthorizationCodeGrantCommand.COMMAND_ID}")

    @Provides
    fun verifyRefreshTokenGrant(registry: SessionScopedCommandRegistry): VerifyRefreshTokenGrantCommand =
        registry.get(VerifyRefreshTokenGrantCommand.COMMAND_ID) as? VerifyRefreshTokenGrantCommand
            ?: error("No binding for ${VerifyRefreshTokenGrantCommand.COMMAND_ID}")

    @Provides
    fun verifyClientCredentialsGrant(registry: SessionScopedCommandRegistry): VerifyClientCredentialsGrantCommand =
        registry.get(VerifyClientCredentialsGrantCommand.COMMAND_ID) as? VerifyClientCredentialsGrantCommand
            ?: error("No binding for ${VerifyClientCredentialsGrantCommand.COMMAND_ID}")

    @Provides
    fun verifyTokenExchangeGrant(registry: SessionScopedCommandRegistry): VerifyTokenExchangeGrantCommand =
        registry.get(VerifyTokenExchangeGrantCommand.COMMAND_ID) as? VerifyTokenExchangeGrantCommand
            ?: error("No binding for ${VerifyTokenExchangeGrantCommand.COMMAND_ID}")

    @Provides
    fun createAccessToken(registry: SessionScopedCommandRegistry): CreateAccessTokenCommand =
        registry.get(CreateAccessTokenCommand.COMMAND_ID) as? CreateAccessTokenCommand
            ?: error("No binding for ${CreateAccessTokenCommand.COMMAND_ID}")

    @Provides
    fun createRefreshToken(registry: SessionScopedCommandRegistry): CreateRefreshTokenCommand =
        registry.get(CreateRefreshTokenCommand.COMMAND_ID) as? CreateRefreshTokenCommand
            ?: error("No binding for ${CreateRefreshTokenCommand.COMMAND_ID}")

    @Provides
    fun createTokenResponse(registry: SessionScopedCommandRegistry): CreateTokenResponseCommand =
        registry.get(CreateTokenResponseCommand.COMMAND_ID) as? CreateTokenResponseCommand
            ?: error("No binding for ${CreateTokenResponseCommand.COMMAND_ID}")

    // Introspection endpoint commands
    @Provides
    fun parseIntrospectionRequest(registry: SessionScopedCommandRegistry): ParseIntrospectionRequestCommand =
        registry.get(ParseIntrospectionRequestCommand.COMMAND_ID) as? ParseIntrospectionRequestCommand
            ?: error("No binding for ${ParseIntrospectionRequestCommand.COMMAND_ID}")

    @Provides
    fun authServerIntrospectToken(registry: SessionScopedCommandRegistry): IntrospectTokenCommand =
        registry.get(IntrospectTokenCommand.COMMAND_ID) as? IntrospectTokenCommand
            ?: error("No binding for ${IntrospectTokenCommand.COMMAND_ID}")

    // PAR endpoint commands
    @Provides
    fun parsePushedAuthorizationRequest(registry: SessionScopedCommandRegistry): ParsePushedAuthorizationRequestCommand =
        registry.get(ParsePushedAuthorizationRequestCommand.COMMAND_ID) as? ParsePushedAuthorizationRequestCommand
            ?: error("No binding for ${ParsePushedAuthorizationRequestCommand.COMMAND_ID}")

    @Provides
    fun verifyPushedAuthorizationRequest(registry: SessionScopedCommandRegistry): VerifyPushedAuthorizationRequestCommand =
        registry.get(VerifyPushedAuthorizationRequestCommand.COMMAND_ID) as? VerifyPushedAuthorizationRequestCommand
            ?: error("No binding for ${VerifyPushedAuthorizationRequestCommand.COMMAND_ID}")

    @Provides
    fun createRequestUri(registry: SessionScopedCommandRegistry): CreateRequestUriCommand =
        registry.get(CreateRequestUriCommand.COMMAND_ID) as? CreateRequestUriCommand
            ?: error("No binding for ${CreateRequestUriCommand.COMMAND_ID}")

    @Provides
    fun createPushedAuthorizationResponse(registry: SessionScopedCommandRegistry): CreatePushedAuthorizationResponseCommand =
        registry.get(CreatePushedAuthorizationResponseCommand.COMMAND_ID) as? CreatePushedAuthorizationResponseCommand
            ?: error("No binding for ${CreatePushedAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun retrieveAuthorizationRequestByUri(registry: SessionScopedCommandRegistry): RetrieveAuthorizationRequestByUriCommand =
        registry.get(RetrieveAuthorizationRequestByUriCommand.COMMAND_ID) as? RetrieveAuthorizationRequestByUriCommand
            ?: error("No binding for ${RetrieveAuthorizationRequestByUriCommand.COMMAND_ID}")

    // Revocation endpoint commands
    @Provides
    fun parseRevocationRequest(registry: SessionScopedCommandRegistry): ParseRevocationRequestCommand =
        registry.get(ParseRevocationRequestCommand.COMMAND_ID) as? ParseRevocationRequestCommand
            ?: error("No binding for ${ParseRevocationRequestCommand.COMMAND_ID}")

    @Provides
    fun revokeToken(registry: SessionScopedCommandRegistry): RevokeTokenCommand =
        registry.get(RevokeTokenCommand.COMMAND_ID) as? RevokeTokenCommand
            ?: error("No binding for ${RevokeTokenCommand.COMMAND_ID}")

    // Discovery endpoint commands
    @Provides
    fun buildServerMetadata(registry: SessionScopedCommandRegistry): BuildServerMetadataCommand =
        registry.get(BuildServerMetadataCommand.COMMAND_ID) as? BuildServerMetadataCommand
            ?: error("No binding for ${BuildServerMetadataCommand.COMMAND_ID}")

    // Client authentication commands
    @Provides
    fun verifyClientAuthentication(registry: SessionScopedCommandRegistry): VerifyClientAuthenticationCommand =
        registry.get(VerifyClientAuthenticationCommand.COMMAND_ID) as? VerifyClientAuthenticationCommand
            ?: error("No binding for ${VerifyClientAuthenticationCommand.COMMAND_ID}")

    // Attestation challenge commands
    @Provides
    fun createAttestationChallenge(registry: SessionScopedCommandRegistry): CreateAttestationChallengeCommand =
        registry.get(CreateAttestationChallengeCommand.COMMAND_ID) as? CreateAttestationChallengeCommand
            ?: error("No binding for ${CreateAttestationChallengeCommand.COMMAND_ID}")

    // OIDC commands
    @Provides
    fun createIdToken(registry: SessionScopedCommandRegistry): CreateIdTokenCommand =
        registry.get(CreateIdTokenCommand.COMMAND_ID) as? CreateIdTokenCommand
            ?: error("No binding for ${CreateIdTokenCommand.COMMAND_ID}")

    @Provides
    fun getUserInfo(registry: SessionScopedCommandRegistry): GetUserInfoCommand =
        registry.get(GetUserInfoCommand.COMMAND_ID) as? GetUserInfoCommand
            ?: error("No binding for ${GetUserInfoCommand.COMMAND_ID}")

    @Provides
    fun getJwks(registry: SessionScopedCommandRegistry): GetJwksCommand =
        registry.get(GetJwksCommand.COMMAND_ID) as? GetJwksCommand
            ?: error("No binding for ${GetJwksCommand.COMMAND_ID}")
}

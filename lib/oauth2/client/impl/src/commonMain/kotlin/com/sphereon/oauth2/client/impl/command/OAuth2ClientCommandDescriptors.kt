package com.sphereon.oauth2.client.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateEncryptedJarCommand
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchJwksCommand
import com.sphereon.oauth2.client.command.MergeRequestObjectCommand
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParseJarCommand
import com.sphereon.oauth2.client.command.VerifyPkceCommand
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.command.CreateDpopProofCommand
import com.sphereon.oauth2.common.command.ClientRevokeTokenCommand
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.client.impl.authorization.CreateAuthorizationRequestUrlCommandImpl
import com.sphereon.oauth2.client.impl.authorization.ParseAuthorizationResponseCommandImpl
import com.sphereon.oauth2.client.impl.clientauth.ApplyClientAuthenticationCommandImpl
import com.sphereon.oauth2.client.command.FetchUserInfoCommand
import com.sphereon.oauth2.client.impl.dpop.CreateDpopProofCommandImpl
import com.sphereon.oauth2.client.impl.dpop.ClientVerifyDpopProofCommandImpl
import com.sphereon.oauth2.client.impl.oidc.FetchUserInfoCommandImpl
import com.sphereon.oauth2.client.impl.introspection.ClientIntrospectTokenCommandImpl
import com.sphereon.oauth2.client.impl.revocation.ClientRevokeTokenCommandImpl
import com.sphereon.oauth2.client.impl.jar.CreateEncryptedJarCommandImpl
import com.sphereon.oauth2.client.impl.jar.CreateSignedJarCommandImpl
import com.sphereon.oauth2.client.impl.jar.MergeRequestObjectCommandImpl
import com.sphereon.oauth2.client.impl.jar.ParseJarCommandImpl
import com.sphereon.oauth2.client.impl.metadata.FetchAuthorizationServerMetadataCommandImpl
import com.sphereon.oauth2.client.impl.metadata.FetchJwksCommandImpl
import com.sphereon.oauth2.client.impl.pkce.CreatePkceCommandImpl
import com.sphereon.oauth2.client.impl.pkce.VerifyPkceCommandImpl
import com.sphereon.oauth2.client.impl.token.ExchangeTokenCommandImpl
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface OAuth2ClientCommandDescriptors {

    // PKCE commands
    @Provides @IntoSet
    fun verifyPkce(impl: Lazy<VerifyPkceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyPkceCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createPkce(impl: Lazy<CreatePkceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreatePkceCommand.COMMAND_ID) { impl.value }

    // JAR commands
    @Provides @IntoSet
    fun createEncryptedJar(impl: Lazy<CreateEncryptedJarCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateEncryptedJarCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createSignedJar(impl: Lazy<CreateSignedJarCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateSignedJarCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun mergeRequestObject(impl: Lazy<MergeRequestObjectCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(MergeRequestObjectCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun parseJar(impl: Lazy<ParseJarCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseJarCommand.COMMAND_ID) { impl.value }

    // Authorization commands
    @Provides @IntoSet
    fun parseAuthorizationResponse(impl: Lazy<ParseAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createAuthorizationRequestUrl(impl: Lazy<CreateAuthorizationRequestUrlCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthorizationRequestUrlCommand.COMMAND_ID) { impl.value }

    // Metadata commands
    @Provides @IntoSet
    fun fetchJwks(impl: Lazy<FetchJwksCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(FetchJwksCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun fetchAuthorizationServerMetadata(impl: Lazy<FetchAuthorizationServerMetadataCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(FetchAuthorizationServerMetadataCommand.COMMAND_ID) { impl.value }

    // Introspection commands
    @Provides @IntoSet
    fun clientIntrospectToken(impl: Lazy<ClientIntrospectTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(IntrospectTokenCommand.COMMAND_ID) { impl.value }

    // Revocation commands
    @Provides @IntoSet
    fun clientRevokeToken(impl: Lazy<ClientRevokeTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ClientRevokeTokenCommand.COMMAND_ID) { impl.value }

    // Token commands
    @Provides @IntoSet
    fun exchangeToken(impl: Lazy<ExchangeTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ExchangeTokenCommand.COMMAND_ID) { impl.value }

    // Client auth commands
    @Provides @IntoSet
    fun applyClientAuthentication(impl: Lazy<ApplyClientAuthenticationCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ApplyClientAuthenticationCommand.COMMAND_ID) { impl.value }

    // DPoP commands
    @Provides @IntoSet
    fun createDpopProof(impl: Lazy<CreateDpopProofCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateDpopProofCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun clientVerifyDpopProof(impl: Lazy<ClientVerifyDpopProofCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyDpopProofCommand.COMMAND_ID) { impl.value }

    // OIDC commands
    @Provides @IntoSet
    fun fetchUserInfo(impl: Lazy<FetchUserInfoCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(FetchUserInfoCommand.COMMAND_ID) { impl.value }
}

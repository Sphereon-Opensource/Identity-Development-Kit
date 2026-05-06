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

package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.crypto.HashedIdentifier
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import com.sphereon.identity.reconciliation.api.OidcConnectionResolver
import com.sphereon.identity.reconciliation.api.ResolvedOidcConnection
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreateEncryptedJarCommand
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchJwksCommand
import com.sphereon.oauth2.client.command.FetchUserInfoArgs
import com.sphereon.oauth2.client.command.FetchUserInfoCommand
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import com.sphereon.oauth2.client.command.MergeRequestObjectCommand
import com.sphereon.oauth2.client.command.OAuth2ClientCommandBindings
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.command.ParseJarCommand
import com.sphereon.oauth2.client.command.VerifyPkceCommand
import com.sphereon.oauth2.client.impl.command.OAuth2ClientCommandDescriptors
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.command.ClientRevokeTokenCommand
import com.sphereon.oauth2.common.command.CreateDpopProofCommand
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.TokenResponse
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import kotlinx.serialization.json.JsonPrimitive

/**
 * Test ExchangeTokenCommand that returns a fake token response with a minimal ID token JWT.
 * Registered via DI so the command registry resolves it instead of the real HTTP-based impl.
 */
@Inject
@SingleIn(SessionScope::class)
class TestExchangeTokenCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ExchangeTokenArgs, TokenResponse, IdkError>(
        commandId = ExchangeTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ExchangeTokenArgs>(),
        outputTypeToken = typeToken<TokenResponse>(),
    ),
    ExchangeTokenCommand {
    override val commandId: String get() = ExchangeTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ExchangeTokenArgs

    override suspend fun doExecute(
        args: ExchangeTokenArgs,
        applyDuring: (ExchangeTokenArgs) -> ExchangeTokenArgs,
    ): IdkResult<TokenResponse, IdkError> {
        val claims =
            mapOf(
                "iss" to "https://idp.example.com",
                "sub" to "external-user-123",
                "aud" to "test-client-id",
                "email" to "user@example.com",
                "name" to "Test User",
            )
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val payload =
            buildString {
                append("{")
                append(claims.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" })
                append("}")
            }
        val headerB64 = header.encodeToByteArray().encodeTo(Encoding.BASE64URL)
        val payloadB64 = payload.encodeToByteArray().encodeTo(Encoding.BASE64URL)
        val idToken = "$headerB64.$payloadB64.fake-signature"

        return Ok(
            TokenResponse(
                accessToken = "stub-access-token",
                tokenType = "Bearer",
                expiresIn = 3600,
                idToken = idToken,
            ),
        )
    }
}

/**
 * Test FetchUserInfoCommand that returns fake userinfo claims.
 * Registered via DI so the command registry resolves it instead of the real HTTP-based impl.
 */
@Inject
@SingleIn(SessionScope::class)
class TestFetchUserInfoCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<FetchUserInfoArgs, FetchUserInfoResult, IdkError>(
        commandId = FetchUserInfoCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FetchUserInfoArgs>(),
        outputTypeToken = typeToken<FetchUserInfoResult>(),
    ),
    FetchUserInfoCommand {
    override val commandId: String get() = FetchUserInfoCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is FetchUserInfoArgs

    override suspend fun doExecute(
        args: FetchUserInfoArgs,
        applyDuring: (FetchUserInfoArgs) -> FetchUserInfoArgs,
    ): IdkResult<FetchUserInfoResult, IdkError> =
        Ok(
            FetchUserInfoResult(
                sub = "external-user-123",
                claims =
                    mapOf(
                        "email" to JsonPrimitive("user@example.com"),
                        "name" to JsonPrimitive("Test User"),
                        "eduid" to JsonPrimitive("urn:mace:eduid.nl:1.0:d57b4355-c7c6-4924-869f-0e3229e"),
                        "schac_home_organization" to JsonPrimitive("example-university.nl"),
                    ),
            ),
        )
}

/**
 * Test command descriptor that registers the test ExchangeTokenCommand impl in the command registry.
 */
@ContributesTo(SessionScope::class, replaces = [OAuth2ClientCommandDescriptors::class])
interface TestReconciliationCommandDescriptors {
    // Test overrides for reconciliation testing
    @Provides @IntoMap
    @StringKey(ExchangeTokenCommand.COMMAND_ID)
    fun testExchangeToken(impl: TestExchangeTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FetchUserInfoCommand.COMMAND_ID)
    fun testFetchUserInfo(impl: TestFetchUserInfoCommandImpl): ServiceCommand<*, *, *> = impl

    // Re-register non-overridden OAuth2 commands from OAuth2ClientCommandDescriptors
    @Provides @IntoMap
    @StringKey(VerifyPkceCommand.COMMAND_ID)
    fun verifyPkce(impl: com.sphereon.oauth2.client.impl.pkce.VerifyPkceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreatePkceCommand.COMMAND_ID)
    fun createPkce(impl: com.sphereon.oauth2.client.impl.pkce.CreatePkceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateEncryptedJarCommand.COMMAND_ID)
    fun createEncryptedJar(impl: com.sphereon.oauth2.client.impl.jar.CreateEncryptedJarCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateSignedJarCommand.COMMAND_ID)
    fun createSignedJar(impl: com.sphereon.oauth2.client.impl.jar.CreateSignedJarCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(MergeRequestObjectCommand.COMMAND_ID)
    fun mergeRequestObject(impl: com.sphereon.oauth2.client.impl.jar.MergeRequestObjectCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParseJarCommand.COMMAND_ID)
    fun parseJar(impl: com.sphereon.oauth2.client.impl.jar.ParseJarCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParseAuthorizationResponseCommand.COMMAND_ID)
    fun parseAuthorizationResponse(impl: com.sphereon.oauth2.client.impl.authorization.ParseAuthorizationResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationRequestUrlCommand.COMMAND_ID)
    fun createAuthorizationRequestUrl(impl: com.sphereon.oauth2.client.impl.authorization.CreateAuthorizationRequestUrlCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FetchJwksCommand.COMMAND_ID)
    fun fetchJwks(impl: com.sphereon.oauth2.client.impl.metadata.FetchJwksCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FetchAuthorizationServerMetadataCommand.COMMAND_ID)
    fun fetchAuthorizationServerMetadata(impl: com.sphereon.oauth2.client.impl.metadata.FetchAuthorizationServerMetadataCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(IntrospectTokenCommand.COMMAND_ID)
    fun clientIntrospectToken(impl: com.sphereon.oauth2.client.impl.introspection.ClientIntrospectTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ClientRevokeTokenCommand.COMMAND_ID)
    fun clientRevokeToken(impl: com.sphereon.oauth2.client.impl.revocation.ClientRevokeTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ApplyClientAuthenticationCommand.COMMAND_ID)
    fun applyClientAuthentication(impl: com.sphereon.oauth2.client.impl.clientauth.ApplyClientAuthenticationCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateDpopProofCommand.COMMAND_ID)
    fun createDpopProof(impl: com.sphereon.oauth2.client.impl.dpop.CreateDpopProofCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyDpopProofCommand.COMMAND_ID)
    fun clientVerifyDpopProof(impl: com.sphereon.oauth2.client.impl.dpop.ClientVerifyDpopProofCommandImpl): ServiceCommand<*, *, *> = impl
}

/**
 * Replaces [OAuth2ClientCommandBindings] in tests to provide the test [ExchangeTokenCommand]
 * directly, bypassing the command registry's non-deterministic resolution when duplicate
 * command IDs exist (real + test descriptors both register the same ID).
 *
 * All other OAuth2 commands are still resolved from the registry as normal.
 */
@ContributesTo(SessionScope::class, replaces = [OAuth2ClientCommandBindings::class])
interface TestOAuth2ClientCommandBindings {
    @Provides
    fun exchangeToken(impl: TestExchangeTokenCommandImpl): ExchangeTokenCommand = impl

    @Provides
    fun fetchUserInfo(impl: TestFetchUserInfoCommandImpl): FetchUserInfoCommand = impl

    @Provides
    fun parseAuthorizationResponse(registry: SessionScopedCommandRegistry): ParseAuthorizationResponseCommand =
        registry.get(ParseAuthorizationResponseCommand.COMMAND_ID) as? ParseAuthorizationResponseCommand
            ?: error("No binding for ${ParseAuthorizationResponseCommand.COMMAND_ID}")

    @Provides
    fun createAuthorizationRequestUrl(registry: SessionScopedCommandRegistry): CreateAuthorizationRequestUrlCommand =
        registry.get(CreateAuthorizationRequestUrlCommand.COMMAND_ID) as? CreateAuthorizationRequestUrlCommand
            ?: error("No binding for ${CreateAuthorizationRequestUrlCommand.COMMAND_ID}")

    @Provides
    fun createSignedJar(registry: SessionScopedCommandRegistry): CreateSignedJarCommand =
        registry.get(CreateSignedJarCommand.COMMAND_ID) as? CreateSignedJarCommand
            ?: error("No binding for ${CreateSignedJarCommand.COMMAND_ID}")

    @Provides
    fun createEncryptedJar(registry: SessionScopedCommandRegistry): CreateEncryptedJarCommand =
        registry.get(CreateEncryptedJarCommand.COMMAND_ID) as? CreateEncryptedJarCommand
            ?: error("No binding for ${CreateEncryptedJarCommand.COMMAND_ID}")

    @Provides
    fun parseJar(registry: SessionScopedCommandRegistry): ParseJarCommand =
        registry.get(ParseJarCommand.COMMAND_ID) as? ParseJarCommand
            ?: error("No binding for ${ParseJarCommand.COMMAND_ID}")

    @Provides
    fun createPkce(registry: SessionScopedCommandRegistry): CreatePkceCommand =
        registry.get(CreatePkceCommand.COMMAND_ID) as? CreatePkceCommand
            ?: error("No binding for ${CreatePkceCommand.COMMAND_ID}")

    @Provides
    fun verifyPkce(registry: SessionScopedCommandRegistry): VerifyPkceCommand =
        registry.get(VerifyPkceCommand.COMMAND_ID) as? VerifyPkceCommand
            ?: error("No binding for ${VerifyPkceCommand.COMMAND_ID}")

    @Provides
    fun mergeRequestObject(registry: SessionScopedCommandRegistry): MergeRequestObjectCommand =
        registry.get(MergeRequestObjectCommand.COMMAND_ID) as? MergeRequestObjectCommand
            ?: error("No binding for ${MergeRequestObjectCommand.COMMAND_ID}")

    @Provides
    fun fetchAuthorizationServerMetadata(registry: SessionScopedCommandRegistry): FetchAuthorizationServerMetadataCommand =
        registry.get(FetchAuthorizationServerMetadataCommand.COMMAND_ID) as? FetchAuthorizationServerMetadataCommand
            ?: error("No binding for ${FetchAuthorizationServerMetadataCommand.COMMAND_ID}")

    @Provides
    fun fetchJwks(registry: SessionScopedCommandRegistry): FetchJwksCommand =
        registry.get(FetchJwksCommand.COMMAND_ID) as? FetchJwksCommand
            ?: error("No binding for ${FetchJwksCommand.COMMAND_ID}")

    @Provides
    fun completeOidcLogin(registry: SessionScopedCommandRegistry): com.sphereon.oauth2.client.command.CompleteOidcLoginCommand =
        registry.get(com.sphereon.oauth2.client.command.CompleteOidcLoginCommand.COMMAND_ID) as? com.sphereon.oauth2.client.command.CompleteOidcLoginCommand
            ?: error("No binding for ${com.sphereon.oauth2.client.command.CompleteOidcLoginCommand.COMMAND_ID}")
}

@ContributesTo(SessionScope::class)
interface TestOidcConnectionResolverModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideOidcConnectionResolver(): OidcConnectionResolver =
        object : OidcConnectionResolver {
            override suspend fun resolve(oidcClientId: String): ResolvedOidcConnection =
                ResolvedOidcConnection(
                    discoveryUrl = "https://idp.example.com/.well-known/openid-configuration",
                    clientId = "test-client-id",
                    clientSecret = "test-client-secret",
                    scopes = listOf("openid", "profile", "email"),
                    userInfoEnabled = oidcClientId.contains("userinfo"),
                )
        }
}

@ContributesTo(SessionScope::class, replaces = [com.sphereon.identity.matching.impl.crypto.ReconciliationCryptoModule::class])
interface TestReconciliationCryptoModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideReconciliationCryptoService(): ReconciliationCryptoService =
        object : ReconciliationCryptoService {
            override suspend fun hashHolderKey(holderKey: String) = HashedIdentifier(hash = "holder:$holderKey", keyVersion = "v1")

            override suspend fun hashExternalIdentifier(identifier: String) = HashedIdentifier(hash = "ext:$identifier", keyVersion = "v1")

            override suspend fun encrypt(plaintext: String) = EncryptedPayload(ciphertext = plaintext, keyVersion = "v1")

            override suspend fun decrypt(payload: EncryptedPayload) = payload.ciphertext

            override suspend fun hashHolderKeyWithPrevious(holderKey: String): HashedIdentifier? = null

            override suspend fun hashExternalIdentifierWithPrevious(identifier: String): HashedIdentifier? = null
        }
}

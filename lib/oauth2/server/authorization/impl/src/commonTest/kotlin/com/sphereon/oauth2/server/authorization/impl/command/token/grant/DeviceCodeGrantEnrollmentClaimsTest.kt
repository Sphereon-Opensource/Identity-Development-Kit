/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.StringResult
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenCommand
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseCommand
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.TokenRequestData
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantArgs
import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantCommand
import com.sphereon.oauth2.server.authorization.command.token.VerifiedDeviceCodeGrant
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryDeviceAuthorizationStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Device-flow enrollment claim enrichment (phase-plan section SCREEN ENROLLMENT ON THE OWNING
 * AS): the RFC 8628 grant handler derives the access-token audience from the registered screen
 * client's default audience when the device request carried none, and lifts ONLY allowlisted
 * registration metadata keys into CreateAccessTokenArgs.additionalClaims. A screen client never
 * sends audience or claim parameters itself; its registration is the sole source, and a missing
 * handoff_role yields a token the resource side rejects (fail closed downstream by design).
 */
class DeviceCodeGrantEnrollmentClaimsTest {

    @Test
    fun `registered screen metadata lands as claims and default audience fills the gap`() =
        runTest {
            val captured = mutableListOf<CreateAccessTokenArgs>()
            val handler = handler(screenClient(), captured)
            val outcome =
                handler.handle(
                    GrantParameters.DeviceCode(deviceCode = DEVICE_CODE),
                    context(),
                )
            assertEquals(true, outcome.isOk)

            assertEquals(1, captured.size, "exactly one access-token mint per approved poll")
            val args = captured.single()
            assertEquals(
                listOf("enterprise-handoff"),
                args.audience,
                "the registered default audience fills the empty request audience",
            )
            assertEquals("display", args.additionalClaims["handoff_role"])
            assertEquals("loc-pos-42", args.additionalClaims["handoff_location"])
            assertEquals("pos-frontdesk-3", args.additionalClaims["device_ref"])
            assertEquals("Front desk", args.additionalClaims["label"])
            assertEquals(null, args.additionalClaims["internal_note"], "non-allowlisted metadata must not reach tokens")
        }

    @Test
    fun `explicit request audiences win over the registered default`() =
        runTest {
            val captured = mutableListOf<CreateAccessTokenArgs>()
            val verified =
                baseVerified().copy(audience = listOf("https://other.example/api"))
            val handler = handler(screenClient(), captured, verified)
            handler.handle(GrantParameters.DeviceCode(deviceCode = DEVICE_CODE), context())
            assertEquals(
                listOf("https://other.example/api"),
                captured.single().audience,
                "RFC 8707 explicit audiences take precedence over the registration default",
            )
        }

    private fun screenClient() =
        ClientRegistration(
            clientId = SCREEN_CLIENT_ID,
            clientType = com.sphereon.oauth2.server.authorization.model.ClientType.PUBLIC,
            grantTypes = listOf(GrantType.DEVICE_CODE),
            tokenEndpointAuthMethod = ClientAuthenticationMethod.NONE,
            defaultAccessTokenAudience = "enterprise-handoff",
            additionalMetadata =
                mapOf(
                    "handoff_role" to "display",
                    "handoff_location" to "loc-pos-42",
                    "device_ref" to "pos-frontdesk-3",
                    "label" to "Front desk",
                    "internal_note" to "must-not-mint",
                ),
        )

    private fun baseVerified() =
        VerifiedDeviceCodeGrant(
            deviceCode = DEVICE_CODE,
            clientId = SCREEN_CLIENT_ID,
            subject = SCREEN_ID,
            authTime = Instant.fromEpochSeconds(1_755_850_000),
            sessionId = null,
            grantedScope = "handoff",
            resource = null,
            audience = null,
        )

    private fun handler(
        client: ClientRegistration?,
        captured: MutableList<CreateAccessTokenArgs>,
        verified: VerifiedDeviceCodeGrant = baseVerified(),
    ): DeviceCodeGrantHandlerImpl =
        DeviceCodeGrantHandlerImpl(
            verifyDeviceCodeGrantCommand =
                object : VerifyDeviceCodeGrantCommand {
                    override val commandId: String get() = VerifyDeviceCodeGrantCommand.COMMAND_ID
                    override val inputTypeToken get() = typeToken<VerifyDeviceCodeGrantArgs>()
                    override val outputTypeToken get() = typeToken<VerifiedDeviceCodeGrant>()
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: VerifyDeviceCodeGrantArgs): IdkResult<VerifiedDeviceCodeGrant, IdkError> = Ok(verified)
                },
            deviceAuthorizationStorage =
                InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl()),
            clock = Clock.System,
            createAccessToken =
                object : CreateAccessTokenCommand {
                    override val commandId: String get() = CreateAccessTokenCommand.COMMAND_ID
                    override val inputTypeToken get() = typeToken<CreateAccessTokenArgs>()
                    override val outputTypeToken get() = typeToken<StringResult>()
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: CreateAccessTokenArgs): IdkResult<StringResult, IdkError> {
                        captured.add(args)
                        return Ok(StringResult(value = "test-access-token"))
                    }
                },
            createRefreshToken = lazy<CreateRefreshTokenCommand> { error("refresh token must not mint without offline_access") },
            createIdToken = lazy<CreateIdTokenCommand> { error("id token must not mint without openid") },
            createTokenResponse = stubTokenResponse,
            clientRegistry = singleClientRegistry(client),
        )

    private fun context() =
        GrantContext(
            tokenRequest =
                TokenRequestData(
                    grantType = GrantType.DEVICE_CODE,
                    clientId = SCREEN_CLIENT_ID,
                    clientAuthentication = ClientAuthenticationConfig.None(clientId = SCREEN_CLIENT_ID),
                    grantParameters = GrantParameters.DeviceCode(deviceCode = DEVICE_CODE),
                    httpUrl = "https://as.example.com/token",
                ),
            tenantId = "tenant-test",
            resolvedClientId = SCREEN_CLIENT_ID,
            proofJkt = null,
            certThumbprintS256 = null,
            applied =
                HandleTokenRequestArgs(
                    requestBody = emptyMap<String, List<String>>(),
                    requestHeaders = emptyMap<String, String>(),
                    httpUrl = "https://as.example.com/token",
                ),
            serverConfig = OAuth2ServerInstanceConfig(),
        )

    private val stubTokenResponse =
        object : CreateTokenResponseCommand {
            override val commandId: String get() = CreateTokenResponseCommand.COMMAND_ID
            override val inputTypeToken get() = typeToken<CreateTokenResponseArgs>()
            override val outputTypeToken get() = typeToken<TokenResponse>()
            override val isEnabled: Boolean = true

            override suspend fun execute(args: CreateTokenResponseArgs): IdkResult<TokenResponse, IdkError> =
                Ok(
                    TokenResponse(
                        accessToken = args.accessToken,
                        tokenType = args.tokenType,
                        refreshToken = args.refreshToken,
                        scope = args.scope,
                        idToken = args.idToken,
                    ),
                )
        }

    private fun singleClientRegistry(client: ClientRegistration?): ClientRegistry =
        object : ClientRegistry {
            override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> =
                Ok(if (clientId == SCREEN_CLIENT_ID) client else null)

            override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> =
                Err(AuthorizationServerError.StorageError(operation = "registerClient", details = "not supported in this test"))

            override suspend fun updateClient(
                clientId: String,
                registration: ClientRegistration,
            ): IdkResult<ClientRegistration, AuthorizationServerError> =
                Err(AuthorizationServerError.StorageError(operation = "updateClient", details = "not supported in this test"))

            override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> =
                Err(AuthorizationServerError.StorageError(operation = "deleteClient", details = "not supported in this test"))

            override suspend fun listClients(
                limit: Int,
                offset: Int,
            ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

            override suspend fun findClientsByName(name: String): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> = Ok(emptyList())

            override suspend fun clientExists(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(false)

            override suspend fun verifyClientCredentials(
                clientId: String,
                clientSecret: String,
            ): IdkResult<Boolean, AuthorizationServerError.StorageError> = Ok(false)
        }

    companion object {
        const val DEVICE_CODE = "dev-code-test"
        const val SCREEN_CLIENT_ID = "dyn-screen-client"
        const val SCREEN_ID = "scr-frontdesk-1"
    }
}

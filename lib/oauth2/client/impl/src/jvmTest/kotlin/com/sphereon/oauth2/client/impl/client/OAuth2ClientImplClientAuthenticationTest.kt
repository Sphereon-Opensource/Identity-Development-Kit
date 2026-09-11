package com.sphereon.oauth2.client.impl.client

import com.sphereon.core.api.Ok
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.oauth2.client.command.CompleteOidcLoginCommand
import com.sphereon.oauth2.client.command.CreateAuthorizationRequestUrlCommand
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchUserInfoCommand
import com.sphereon.oauth2.client.command.ParseAuthorizationResponseCommand
import com.sphereon.oauth2.client.service.DpopService
import com.sphereon.oauth2.client.transaction.OidcLoginTransactionStore
import com.sphereon.oauth2.common.command.IntrospectTokenCommand
import com.sphereon.oauth2.common.command.ValidateIdTokenCommand
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.common.model.TokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuth2ClientImplClientAuthenticationTest {
    @Test
    fun exchangeAuthorizationCode_preservesBasicTokenEndpointAuthentication() = runTest {
        val exchange = mockk<ExchangeTokenCommand>()
        val request = slot<ExchangeTokenArgs>()
        coEvery { exchange.execute(capture(request)) } returns Ok(TokenResponse("access", "Bearer"))
        val client = client(exchange)

        val result = client.exchangeAuthorizationCode(
            authorizationServerMetadata = metadata(),
            clientAuthentication = ClientAuthenticationConfig.Basic(ClientCredentials("client", "secret")),
            authorizationCode = "code",
            redirectUri = "https://wallet.example/callback",
            pkceData = null,
            resource = null,
            dpopContext = null,
            audience = null,
        )

        assertTrue(result.isOk)
        assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, request.captured.request.tokenEndpointAuthMethod)
        assertEquals("client", request.captured.request.clientId)
        assertEquals("secret", request.captured.request.clientSecret)
        coVerify(exactly = 1) { exchange.execute(any()) }
    }

    @Test
    fun refreshAccessToken_preservesConfiguredTokenEndpointAuthentication() = runTest {
        val exchange = mockk<ExchangeTokenCommand>()
        val request = slot<ExchangeTokenArgs>()
        coEvery { exchange.execute(capture(request)) } returns Ok(TokenResponse("access", "Bearer"))
        val client = client(exchange)

        val result = client.refreshAccessToken(
            authorizationServerMetadata = metadata(),
            clientAuthentication = ClientAuthenticationConfig.Basic(ClientCredentials("client", "secret")),
            refreshToken = "refresh",
            scope = null,
            resource = null,
            dpopContext = null,
            audience = null,
        )

        assertTrue(result.isOk)
        assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, request.captured.request.tokenEndpointAuthMethod)
        assertEquals("client", request.captured.request.clientId)
        assertEquals("secret", request.captured.request.clientSecret)
    }

    private fun client(exchange: ExchangeTokenCommand) = OAuth2ClientImpl(
        fetchMetadataCommand = mockk(relaxed = true),
        createPkceCommand = mockk(relaxed = true),
        createAuthorizationRequestUrlCommand = mockk(relaxed = true),
        parseAuthorizationResponseCommand = mockk(relaxed = true),
        exchangeTokenCommand = exchange,
        introspectTokenCommand = mockk(relaxed = true),
        dpopService = mockk(relaxed = true),
        validateIdTokenCommand = mockk(relaxed = true),
        fetchUserInfoCommand = mockk(relaxed = true),
        secureRandom = mockk<SecureRandom>(relaxed = true),
        oidcLoginTransactionStore = mockk(relaxed = true),
        completeOidcLoginCommand = mockk<CompleteOidcLoginCommand>(relaxed = true),
    )

    private fun metadata() = AuthorizationServerMetadata(
        issuer = "https://wallet.example",
        tokenEndpoint = "https://wallet.example/token",
    )
}

/* Copyright 2026 Sphereon International B.V. */
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
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class OAuth2ClientTokenAudiencePropagationTest {
    @Test
    fun absentResourceAndAudienceAreOmittedFromCodeAndRefreshRequests() = runTest {
        val capture = CapturingExchangeTokenCommand()
        val client = client(capture.command)
        val metadata = metadata()

        client.exchangeAuthorizationCode(
            authorizationServerMetadata = metadata,
            clientAuthentication = ClientAuthenticationConfig.None("selected-client"),
            authorizationCode = "code",
            redirectUri = "https://bff.example.com/callback",
            resource = null,
            audience = null,
        )
        client.refreshAccessToken(
            authorizationServerMetadata = metadata,
            clientAuthentication = ClientAuthenticationConfig.None("selected-client"),
            refreshToken = "refresh",
            resource = null,
            audience = null,
        )

        assertEquals(2, capture.requests.size)
        capture.requests.forEach { request ->
            assertEquals(emptyList(), request.resource)
            assertEquals(emptyList(), request.audience)
        }
    }

    @Test
    fun configuredResourcePropagatesItsExactAudienceSemantics() = runTest {
        val capture = CapturingExchangeTokenCommand()
        val client = client(capture.command)

        client.refreshAccessToken(
            authorizationServerMetadata = metadata(),
            clientAuthentication = ClientAuthenticationConfig.None("selected-client"),
            refreshToken = "refresh",
            resource = listOf("https://resource.example.com"),
            audience = listOf("selected-client-default-audience"),
        )

        assertEquals(listOf("https://resource.example.com"), capture.requests.single().resource)
        assertEquals(listOf("selected-client-default-audience"), capture.requests.single().audience)
    }

    private fun client(exchange: ExchangeTokenCommand) = OAuth2ClientImpl(
        fetchMetadataCommand = unused(),
        createPkceCommand = unused(),
        createAuthorizationRequestUrlCommand = unused(),
        parseAuthorizationResponseCommand = unused(),
        exchangeTokenCommand = exchange,
        introspectTokenCommand = unused(),
        dpopService = unused(),
        validateIdTokenCommand = unused(),
        fetchUserInfoCommand = unused(),
        secureRandom = unused(),
        oidcLoginTransactionStore = unused(),
        completeOidcLoginCommand = unused(),
    )

    private fun metadata() = AuthorizationServerMetadata(
        issuer = "https://as.example.com",
        tokenEndpoint = "https://as.example.com/token",
    )
}

private class CapturingExchangeTokenCommand {
    val requests = mutableListOf<TokenRequest>()
    val command: ExchangeTokenCommand = proxy { method, args ->
        when (method.name) {
            "execute" -> {
                requests += (args?.get(0) as ExchangeTokenArgs).request
                Ok(TokenResponse(accessToken = "access", tokenType = "Bearer", expiresIn = 300))
            }
            else -> error("Unexpected exchange command method ${method.name}")
        }
    }
}

private inline fun <reified T : Any> unused(): T = proxy { method, _ ->
    error("Unexpected ${T::class.simpleName} method ${method.name}")
}

private inline fun <reified T : Any> proxy(
    crossinline invocation: (java.lang.reflect.Method, Array<out Any?>?) -> Any?,
): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { _, method, args -> invocation(method, args) } as T

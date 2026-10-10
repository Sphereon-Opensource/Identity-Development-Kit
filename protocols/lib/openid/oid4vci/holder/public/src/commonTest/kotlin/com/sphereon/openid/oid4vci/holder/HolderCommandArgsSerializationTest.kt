package com.sphereon.openid.oid4vci.holder

import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class HolderCommandArgsSerializationTest {
    private val json = Json

    @Test
    fun offerResolutionAndNonceArgsRoundTrip() {
        val resolve = ResolveCredentialOfferArgs(CredentialOffer("https://issuer.example", listOf("pid")))
        assertEquals(resolve, json.decodeFromString(ResolveCredentialOfferArgs.serializer(), json.encodeToString(ResolveCredentialOfferArgs.serializer(), resolve)))

        val nonce = RequestNonceArgs("https://issuer.example/nonce")
        assertEquals(nonce, json.decodeFromString(RequestNonceArgs.serializer(), json.encodeToString(RequestNonceArgs.serializer(), nonce)))
    }

    @Test
    fun preAuthorizedExchangeRoundTripsWithoutClientAuthentication() {
        val args = ExchangePreAuthorizedCodeArgs(tokenEndpoint = "https://issuer.example/token", preAuthorizedCode = "code", txCode = "1234")
        val encoded = json.encodeToString(ExchangePreAuthorizedCodeArgs.serializer(), args)

        assertEquals(args, json.decodeFromString(ExchangePreAuthorizedCodeArgs.serializer(), encoded))
    }

    @Test
    fun clientAuthenticationMaterialIsNeverSerialized() {
        val args =
            ExchangePreAuthorizedCodeArgs(
                tokenEndpoint = "https://issuer.example/token",
                preAuthorizedCode = "code",
                clientAuthentication = ClientAuthenticationConfig.Basic(ClientCredentials("client", "secret")),
            )

        val failure = assertFailsWith<SerializationException> { json.encodeToString(ExchangePreAuthorizedCodeArgs.serializer(), args) }
        assertFalse(failure.message.orEmpty().contains("secret"))
        assertFailsWith<SerializationException> {
            json.decodeFromString(
                ExchangePreAuthorizedCodeArgs.serializer(),
                """{"tokenEndpoint":"https://issuer.example/token","preAuthorizedCode":"code","clientAuthentication":"basic"}""",
            )
        }
    }
}

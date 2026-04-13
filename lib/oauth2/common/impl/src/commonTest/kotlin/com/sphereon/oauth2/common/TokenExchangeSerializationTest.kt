package com.sphereon.oauth2.common

import com.sphereon.oauth2.common.model.ActorClaim
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.common.model.TokenTypeIdentifier
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TokenExchangeSerializationTest {

    @Test
    fun tokenRequestRoundTripWithStsFields() {
        val original = TokenRequest(
            grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
            subjectToken = "subject-jwt-token",
            subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
            actorToken = "actor-jwt-token",
            actorTokenType = TokenTypeIdentifier.JWT,
            requestedTokenType = TokenTypeIdentifier.ACCESS_TOKEN
        )

        val json = Json.encodeToString(TokenRequest.serializer(), original)
        val decoded = Json.decodeFromString(TokenRequest.serializer(), json)

        assertEquals(original.grantType, decoded.grantType)
        assertEquals(original.subjectToken, decoded.subjectToken)
        assertEquals(original.subjectTokenType, decoded.subjectTokenType)
        assertEquals(original.actorToken, decoded.actorToken)
        assertEquals(original.actorTokenType, decoded.actorTokenType)
        assertEquals(original.requestedTokenType, decoded.requestedTokenType)
    }

    @Test
    fun tokenRequestRoundTripWithMultiValueResourceAndAudience() {
        val original = TokenRequest(
            grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
            subjectToken = "subject-token",
            subjectTokenType = TokenTypeIdentifier.ACCESS_TOKEN,
            resource = listOf("https://api.example.com/v1", "https://api.example.com/v2"),
            audience = listOf("aud-1", "aud-2", "aud-3")
        )

        val json = Json.encodeToString(TokenRequest.serializer(), original)
        val decoded = Json.decodeFromString(TokenRequest.serializer(), json)

        assertEquals(original.grantType, decoded.grantType)
        assertEquals(2, decoded.resource.size)
        assertEquals("https://api.example.com/v1", decoded.resource[0])
        assertEquals("https://api.example.com/v2", decoded.resource[1])
        assertEquals(3, decoded.audience.size)
        assertEquals("aud-1", decoded.audience[0])
        assertEquals("aud-2", decoded.audience[1])
        assertEquals("aud-3", decoded.audience[2])
    }

    @Test
    fun tokenResponseRoundTripWithIssuedTokenType() {
        val original = TokenResponse(
            accessToken = "exchanged-access-token",
            tokenType = "Bearer",
            expiresIn = 3600,
            issuedTokenType = TokenTypeIdentifier.ACCESS_TOKEN
        )

        val json = Json.encodeToString(TokenResponse.serializer(), original)
        val decoded = Json.decodeFromString(TokenResponse.serializer(), json)

        assertEquals(original.accessToken, decoded.accessToken)
        assertEquals(original.tokenType, decoded.tokenType)
        assertEquals(original.expiresIn, decoded.expiresIn)
        assertEquals(original.issuedTokenType, decoded.issuedTokenType)
    }

    @Test
    fun actorClaimNestedDelegationChain() {
        val innerActor = ActorClaim(sub = "inner-service@example.com")
        val outerActor = ActorClaim(sub = "outer-service@example.com", act = innerActor)

        val json = Json.encodeToString(ActorClaim.serializer(), outerActor)
        val decoded = Json.decodeFromString(ActorClaim.serializer(), json)

        assertEquals("outer-service@example.com", decoded.sub)
        assertNotNull(decoded.act)
        assertEquals("inner-service@example.com", decoded.act!!.sub)
        assertEquals(null, decoded.act!!.act)
    }

    @Test
    fun tokenRequestDeserializationFromRawJsonWithTokenExchangeFields() {
        val rawJson = """
            {
                "grant_type": "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token": "eyJhbGciOiJSUzI1NiJ9.subject",
                "subject_token_type": "urn:ietf:params:oauth:token-type:access_token",
                "actor_token": "eyJhbGciOiJSUzI1NiJ9.actor",
                "actor_token_type": "urn:ietf:params:oauth:token-type:jwt",
                "requested_token_type": "urn:ietf:params:oauth:token-type:access_token",
                "resource": ["https://api.example.com/resource"],
                "audience": ["target-service"]
            }
        """.trimIndent()

        val request = Json.decodeFromString(TokenRequest.serializer(), rawJson)

        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", request.grantType)
        assertEquals("eyJhbGciOiJSUzI1NiJ9.subject", request.subjectToken)
        assertEquals(TokenTypeIdentifier.ACCESS_TOKEN, request.subjectTokenType)
        assertEquals("eyJhbGciOiJSUzI1NiJ9.actor", request.actorToken)
        assertEquals(TokenTypeIdentifier.JWT, request.actorTokenType)
        assertEquals(TokenTypeIdentifier.ACCESS_TOKEN, request.requestedTokenType)
        assertEquals(1, request.resource.size)
        assertEquals("https://api.example.com/resource", request.resource[0])
        assertEquals(1, request.audience.size)
        assertEquals("target-service", request.audience[0])
    }

    @Test
    fun tokenResponseDeserializationFromRawJsonWithIssuedTokenType() {
        val rawJson = """
            {
                "access_token": "new-exchanged-token",
                "token_type": "Bearer",
                "expires_in": 1800,
                "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
                "scope": "openid profile"
            }
        """.trimIndent()

        val response = Json.decodeFromString(TokenResponse.serializer(), rawJson)

        assertEquals("new-exchanged-token", response.accessToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(1800, response.expiresIn)
        assertEquals(TokenTypeIdentifier.ACCESS_TOKEN, response.issuedTokenType)
        assertEquals("openid profile", response.scope)
    }
}

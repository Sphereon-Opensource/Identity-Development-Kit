package com.sphereon.oauth2.jwt.validation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AccessTokenValidationOptionsTrustSerializationTest {
    @Test
    fun perCallTrustMaterialIsNeverSerializedOrDeserialized() {
        val options =
            AccessTokenValidationOptions(
                trustedIssuer = "https://trusted.example.com",
                trustedJwksUri = "https://trusted.example.com/jwks.json",
            )

        val encoded = Json.encodeToString(options)
        val decoded = Json.decodeFromString<AccessTokenValidationOptions>(encoded)

        assertFalse("trustedIssuer" in encoded)
        assertFalse("trustedJwksUri" in encoded)
        assertNull(decoded.trustedIssuer)
        assertNull(decoded.trustedJwksUri)

        val forgedInput =
            """{"trustedIssuer":"https://attacker.example/as","trustedJwksUri":"https://attacker.example/jwks.json"}"""
        assertFailsWith<SerializationException> {
            Json.decodeFromString<AccessTokenValidationOptions>(forgedInput)
        }

        val tolerantDecoded =
            Json {
                ignoreUnknownKeys = true
            }.decodeFromString<AccessTokenValidationOptions>(forgedInput)
        assertNull(tolerantDecoded.trustedIssuer)
        assertNull(tolerantDecoded.trustedJwksUri)
    }
}

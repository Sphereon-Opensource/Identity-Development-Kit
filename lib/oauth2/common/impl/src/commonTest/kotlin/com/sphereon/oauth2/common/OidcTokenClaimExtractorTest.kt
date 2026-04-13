package com.sphereon.oauth2.common

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.oauth2.common.token.DefaultOidcTokenClaimExtractor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OidcTokenClaimExtractorTest {

    private val extractor = DefaultOidcTokenClaimExtractor()

    private fun buildTestJwt(claims: Map<String, Any>): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val payload = buildString {
            append("{")
            append(claims.entries.joinToString(",") { (k, v) ->
                when (v) {
                    is String -> "\"$k\":\"$v\""
                    is Map<*, *> -> "\"$k\":{${v.entries.joinToString(",") { (nk, nv) -> "\"$nk\":\"$nv\"" }}}"
                    else -> "\"$k\":$v"
                }
            })
            append("}")
        }
        val headerB64 = header.encodeToByteArray().encodeTo(Encoding.BASE64URL)
        val payloadB64 = payload.encodeToByteArray().encodeTo(Encoding.BASE64URL)
        return "$headerB64.$payloadB64.fake-signature"
    }

    @Test
    fun extractAllClaimsFromValidJwt() {
        val jwt = buildTestJwt(mapOf(
            "iss" to "https://idp.example.com",
            "sub" to "user-123",
            "email" to "user@example.com"
        ))

        val result = extractor.extractAllClaims(jwt)
        assertTrue(result.isOk)

        val claims = result.value
        assertEquals(JsonPrimitive("https://idp.example.com"), claims["iss"])
        assertEquals(JsonPrimitive("user-123"), claims["sub"])
        assertEquals(JsonPrimitive("user@example.com"), claims["email"])
    }

    @Test
    fun extractSpecificClaim() {
        val jwt = buildTestJwt(mapOf(
            "sub" to "user-123",
            "email" to "user@example.com"
        ))

        val result = extractor.extractClaim(jwt, listOf("email"))
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("user@example.com"), result.value)
    }

    @Test
    fun extractNestedClaim() {
        val jwt = buildTestJwt(mapOf(
            "address" to mapOf("street" to "123 Main St", "city" to "Amsterdam")
        ))

        val result = extractor.extractClaim(jwt, listOf("address", "street"))
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("123 Main St"), result.value)
    }

    @Test
    fun extractMissingClaimReturnsNull() {
        val jwt = buildTestJwt(mapOf("sub" to "user-123"))

        val result = extractor.extractClaim(jwt, listOf("nonexistent"))
        assertTrue(result.isOk)
        assertNull(result.value)
    }

    @Test
    fun extractMissingNestedPathReturnsNull() {
        val jwt = buildTestJwt(mapOf("sub" to "user-123"))

        val result = extractor.extractClaim(jwt, listOf("address", "street"))
        assertTrue(result.isOk)
        assertNull(result.value)
    }

    @Test
    fun malformedJwtReturnsError() {
        val result = extractor.extractAllClaims("not-a-jwt")
        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage?.contains("Malformed JWT") == true)
    }

    @Test
    fun twoPartJwtReturnsError() {
        val result = extractor.extractAllClaims("part1.part2")
        assertTrue(result.isErr)
    }
}

package com.sphereon.oauth2.server.authorization.impl.command.revocation

import com.sphereon.oauth2.server.authorization.command.ParseRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseRevocationRequestCommandImplTest {

    private val ctx = OAuth2ServerTestContext("parse-revocation-test", this)
    private val command = ParseRevocationRequestCommandImpl(ctx.execution)

    @Test
    fun testValidRevocationRequest() = runTest {
        val requestBody = mapOf(
            "token" to listOf("some-access-token"),
            "client_id" to listOf("client123")
        )

        val result = command.execute(ParseRevocationRequestArgs(requestBody))

        assertTrue(result.isOk)
        val data = result.value
        assertEquals("some-access-token", data.token)
        assertEquals("client123", data.clientId)
        assertEquals(null, data.tokenTypeHint)
    }

    @Test
    fun testValidRequestWithTokenTypeHint() = runTest {
        val requestBody = mapOf(
            "token" to listOf("some-refresh-token"),
            "token_type_hint" to listOf("refresh_token"),
            "client_id" to listOf("client123")
        )

        val result = command.execute(ParseRevocationRequestArgs(requestBody))

        assertTrue(result.isOk)
        val data = result.value
        assertEquals("some-refresh-token", data.token)
        assertEquals("refresh_token", data.tokenTypeHint)
        assertEquals("client123", data.clientId)
    }

    @Test
    fun testAccessTokenTypeHint() = runTest {
        val requestBody = mapOf(
            "token" to listOf("some-access-token"),
            "token_type_hint" to listOf("access_token"),
            "client_id" to listOf("client123")
        )

        val result = command.execute(ParseRevocationRequestArgs(requestBody))

        assertTrue(result.isOk)
        assertEquals("access_token", result.value.tokenTypeHint)
    }

    @Test
    fun testMissingTokenReturnsError() = runTest {
        val requestBody = mapOf(
            "client_id" to listOf("client123")
        )

        val result = command.execute(ParseRevocationRequestArgs(requestBody))

        assertTrue(result.isErr)
    }

    @Test
    fun testBlankTokenReturnsError() = runTest {
        val requestBody = mapOf(
            "token" to listOf(""),
            "client_id" to listOf("client123")
        )

        val result = command.execute(ParseRevocationRequestArgs(requestBody))

        assertTrue(result.isErr)
    }

    @Test
    fun testMissingClientIdReturnsError() = runTest {
        val requestBody = mapOf(
            "token" to listOf("some-token")
        )

        val result = command.execute(ParseRevocationRequestArgs(requestBody))

        assertTrue(result.isErr)
    }

    @Test
    fun testEmptyRequestBodyReturnsError() = runTest {
        val result = command.execute(ParseRevocationRequestArgs(emptyMap()))

        assertTrue(result.isErr)
    }
}

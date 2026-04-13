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

package com.sphereon.openid.oid4vp.auth.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionArgs
import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionResult
import com.sphereon.openid.oid4vp.auth.bridge.Oid4vpAuthBridge
import com.sphereon.openid.oid4vp.auth.error.Oid4vpAuthErrors
import com.sphereon.openid.oid4vp.auth.http.model.Oid4vpAuthStatusResponse
import com.sphereon.openid.oid4vp.auth.impl.http.command.CreateOid4vpAuthSessionCommandImpl
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthResult
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests for OID4VP Authentication HTTP adapter routing.
 *
 * These tests verify that the HTTP endpoint commands correctly handle requests
 * using mock implementations.
 *
 * Note: Commands use relative paths (e.g., `/sessions`) without the adapter mount prefix.
 * The adapter adds the `/auth/oid4vp` prefix when routing.
 */
class Oid4vpAuthHttpAdapterTest {
    private lateinit var mockService: MockOid4vpAuthBridge
    private lateinit var execution: SessionExecution

    @BeforeTest
    fun setup() {
        mockService = MockOid4vpAuthBridge()
        execution = TestExecutionContext.createExecution()
    }

    // ================== Create Session Command Tests ==================

    @Test
    fun `create session command should handle POST sessions`() =
        runTest {
            val createCommand = CreateOid4vpAuthSessionCommandImpl(execution, mockService)

            val requestBody =
                """
                {
                    "queryId": "test-query",
                    "oauthSessionId": "oauth-123",
                    "returnUrl": "https://example.com/callback"
                }
                """.trimIndent()

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                    bodyContent = GenericHttpBody.Text(requestBody),
                )

            assertTrue(createCommand.supports(request))

            val result = createCommand.execute(request)
            assertTrue(result.isOk)

            val response = result.value
            assertEquals(201, response.statusCode)
            assertNotNull(response.body)
            assertTrue(response.body!!.contains("\"sessionId\""))
            assertTrue(response.body!!.contains("\"qrCodeDataUri\""))
        }

    @Test
    fun `create session command should work with empty body`() =
        runTest {
            val createCommand = CreateOid4vpAuthSessionCommandImpl(execution, mockService)

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                )

            assertTrue(createCommand.supports(request))

            val result = createCommand.execute(request)
            assertTrue(result.isOk)
            assertEquals(201, result.value.statusCode)
        }

    @Test
    fun `create session command should only match POST method`() =
        runTest {
            val createCommand = CreateOid4vpAuthSessionCommandImpl(execution, mockService)

            val postRequest = GenericHttpRequest(method = "POST", path = "/sessions")
            val getRequest = GenericHttpRequest(method = "GET", path = "/sessions")
            val putRequest = GenericHttpRequest(method = "PUT", path = "/sessions")

            assertTrue(createCommand.supports(postRequest))
            assertTrue(!createCommand.supports(getRequest))
            assertTrue(!createCommand.supports(putRequest))
        }

    @Test
    fun `create session response should have correct headers`() =
        runTest {
            val createCommand = CreateOid4vpAuthSessionCommandImpl(execution, mockService)

            val request =
                GenericHttpRequest(
                    method = "POST",
                    path = "/sessions",
                )

            val result = createCommand.execute(request)
            assertTrue(result.isOk)

            val response = result.value
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("no-store", response.headers["Cache-Control"])
        }

    // ================== Helper Functions ==================

    private fun createSampleSession(
        sessionId: String,
        correlationId: String = "corr-$sessionId",
        status: Oid4vpAuthSessionStatus = Oid4vpAuthSessionStatus.PENDING,
    ): Oid4vpAuthSession {
        val now = Clock.System.now()
        return Oid4vpAuthSession(
            sessionId = sessionId,
            correlationId = correlationId,
            oauthSessionId = "oauth-123",
            queryId = "test-query",
            status = status,
            verifiedData = null,
            resolvedUserId = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + 5.minutes,
        )
    }
}

/**
 * Mock implementation of [Oid4vpAuthBridge] for testing.
 */
class MockOid4vpAuthBridge : Oid4vpAuthBridge {
    val sessions = mutableMapOf<String, Oid4vpAuthSession>()

    override suspend fun createSession(args: CreateSessionArgs): IdkResult<CreateSessionResult, IdkError> {
        val now = Clock.System.now()
        val sessionId = "session-${System.currentTimeMillis()}"
        val correlationId = "corr-$sessionId"

        val session =
            Oid4vpAuthSession(
                sessionId = sessionId,
                correlationId = correlationId,
                oauthSessionId = args.oauthSessionId,
                queryId = args.queryId ?: "default-query",
                status = Oid4vpAuthSessionStatus.PENDING,
                verifiedData = null,
                resolvedUserId = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
                expiresAt = now + (args.ttlSeconds?.minutes ?: 5.minutes),
            )

        sessions[sessionId] = session

        return Ok(
            CreateSessionResult(
                session = session,
                qrCodeDataUri = "data:image/png;base64,mockQrCode",
                requestUri = "openid4vp://?request_uri=/oid4vp/request-uri/$correlationId",
                statusUri = "/auth/oid4vp/sessions/$sessionId/status",
                qrPageUri = "/auth/oid4vp/sessions/$sessionId/qr",
            ),
        )
    }

    override suspend fun getSessionStatus(sessionId: String): IdkResult<Oid4vpAuthStatusResponse, IdkError> {
        val session =
            sessions[sessionId]
                ?: return com.sphereon.core.api
                    .Err(Oid4vpAuthErrors.sessionNotFound(sessionId))

        return Ok(Oid4vpAuthStatusResponse.from(session))
    }

    override suspend fun completeAuthentication(sessionId: String): IdkResult<Oid4vpAuthResult, IdkError> {
        val session =
            sessions[sessionId]
                ?: return com.sphereon.core.api
                    .Err(Oid4vpAuthErrors.sessionNotFound(sessionId))

        if (session.status == Oid4vpAuthSessionStatus.COMPLETED) {
            return com.sphereon.core.api
                .Err(Oid4vpAuthErrors.sessionAlreadyCompleted(sessionId))
        }

        if (session.status != Oid4vpAuthSessionStatus.VERIFIED) {
            return com.sphereon.core.api
                .Err(Oid4vpAuthErrors.sessionNotVerified(sessionId))
        }

        // Mark session as completed
        val completedSession =
            session.copy(
                status = Oid4vpAuthSessionStatus.COMPLETED,
                updatedAt = Clock.System.now(),
            )
        sessions[sessionId] = completedSession

        return Ok(
            Oid4vpAuthResult(
                userId = "user-$sessionId",
                claims =
                    mapOf(
                        "sub" to kotlinx.serialization.json.JsonPrimitive("user-$sessionId"),
                        "name" to kotlinx.serialization.json.JsonPrimitive("Test User"),
                        "email" to kotlinx.serialization.json.JsonPrimitive("test@example.com"),
                    ),
                jwtClaims = null,
                isNewUser = false,
                authenticatedAt = Clock.System.now(),
                acr = Oid4vpAuthResult.DEFAULT_ACR,
                amr = Oid4vpAuthResult.DEFAULT_AMR,
            ),
        )
    }
}

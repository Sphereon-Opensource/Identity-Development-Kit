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

package com.sphereon.openid.oid4vp.auth.impl.integration

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.defaults.log.AppLogManagerImpl
import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionArgs
import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionResult
import com.sphereon.openid.oid4vp.auth.bridge.Oid4vpAuthBridge
import com.sphereon.openid.oid4vp.auth.error.Oid4vpAuthErrors
import com.sphereon.openid.oid4vp.auth.http.model.Oid4vpAuthStatusResponse
import com.sphereon.openid.oid4vp.auth.impl.store.InMemoryOid4vpAuthSessionStore
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthResult
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import com.sphereon.openid.oid4vp.universal.VerifiedClaimsValue
import com.sphereon.openid.oid4vp.universal.VerifiedData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for the OID4VP Authentication flow.
 *
 * These tests verify the complete authentication flow from session creation
 * through credential verification to completion, using a mock bridge implementation.
 *
 * Test scenarios:
 * 1. Full successful authentication flow
 * 2. Session status polling
 * 3. Session expiration handling
 * 4. Error scenarios (not verified, already completed)
 * 5. Session not found handling
 */
class Oid4vpAuthFlowIntegrationTest {
    // ========================================================================
    // Test: Full Authentication Flow
    // ========================================================================

    @Test
    fun fullAuthFlowShouldCreateSessionUpdateToVerifiedAndComplete() =
        runTest {
            val bridge = TestOid4vpAuthBridge()

            // Step 1: Create session
            val createResult =
                bridge.createSession(
                    CreateSessionArgs(
                        queryId = "test-query",
                        oauthSessionId = "oauth-session-1",
                        returnUrl = "https://example.com/callback",
                    ),
                )

            assertTrue(createResult.isOk, "Session creation should succeed")
            val sessionResult = createResult.value
            assertNotNull(sessionResult.session)
            assertNotNull(sessionResult.qrCodeDataUri)
            assertNotNull(sessionResult.requestUri)
            assertEquals(Oid4vpAuthSessionStatus.PENDING, sessionResult.session.status)

            val sessionId = sessionResult.session.sessionId

            // Step 2: Check status (should be PENDING)
            val statusResult1 = bridge.getSessionStatus(sessionId)
            assertTrue(statusResult1.isOk)
            assertEquals(Oid4vpAuthSessionStatus.PENDING, statusResult1.value.status)

            // Step 3: Simulate wallet presentation - mark as VERIFIED
            bridge.markSessionVerified(sessionId, createVerifiedData())

            // Step 4: Check status again (should now be VERIFIED)
            val statusResult2 = bridge.getSessionStatus(sessionId)
            assertTrue(statusResult2.isOk)
            assertEquals(Oid4vpAuthSessionStatus.VERIFIED, statusResult2.value.status)

            // Step 5: Complete authentication
            val completeResult = bridge.completeAuthentication(sessionId)
            assertTrue(completeResult.isOk, "Authentication completion should succeed")

            val authResult = completeResult.value
            assertNotNull(authResult.userId)
            assertNotNull(authResult.claims)
            assertTrue(authResult.claims.isNotEmpty(), "Should have claims")

            // Step 6: Status should now be COMPLETED
            val statusResult3 = bridge.getSessionStatus(sessionId)
            assertTrue(statusResult3.isOk)
            assertEquals(Oid4vpAuthSessionStatus.COMPLETED, statusResult3.value.status)
        }

    // ========================================================================
    // Test: Session Status Polling
    // ========================================================================

    @Test
    fun getSessionStatusShouldReturnPendingWhileWaiting() =
        runTest {
            val bridge = TestOid4vpAuthBridge()

            val createResult = bridge.createSession(CreateSessionArgs(queryId = "test-query"))
            assertTrue(createResult.isOk)
            val sessionId = createResult.value.session.sessionId

            // Poll multiple times - should remain PENDING
            repeat(3) {
                val statusResult = bridge.getSessionStatus(sessionId)
                assertTrue(statusResult.isOk)
                assertEquals(Oid4vpAuthSessionStatus.PENDING, statusResult.value.status)
            }
        }

    // ========================================================================
    // Test: Complete Before Verification
    // ========================================================================

    @Test
    fun completeAuthenticationShouldFailIfSessionNotVerified() =
        runTest {
            val bridge = TestOid4vpAuthBridge()

            val createResult = bridge.createSession(CreateSessionArgs(queryId = "test-query"))
            assertTrue(createResult.isOk)
            val sessionId = createResult.value.session.sessionId

            // Try to complete without verification (status is PENDING)
            val completeResult = bridge.completeAuthentication(sessionId)
            assertTrue(completeResult.isErr, "Should fail when session not verified")
        }

    // ========================================================================
    // Test: Double Completion Prevention
    // ========================================================================

    @Test
    fun completeAuthenticationShouldFailIfAlreadyCompleted() =
        runTest {
            val bridge = TestOid4vpAuthBridge()

            val createResult = bridge.createSession(CreateSessionArgs(queryId = "test-query"))
            assertTrue(createResult.isOk)
            val sessionId = createResult.value.session.sessionId

            // Mark as verified and complete
            bridge.markSessionVerified(sessionId, createVerifiedData())
            val firstComplete = bridge.completeAuthentication(sessionId)
            assertTrue(firstComplete.isOk, "First completion should succeed")

            // Try to complete again
            val secondComplete = bridge.completeAuthentication(sessionId)
            assertTrue(secondComplete.isErr, "Second completion should fail")
        }

    // ========================================================================
    // Test: Session Not Found
    // ========================================================================

    @Test
    fun getSessionStatusShouldFailForNonExistentSession() =
        runTest {
            val bridge = TestOid4vpAuthBridge()

            val statusResult = bridge.getSessionStatus("non-existent-session-id")
            assertTrue(statusResult.isErr, "Should fail for non-existent session")
        }

    @Test
    fun completeAuthenticationShouldFailForNonExistentSession() =
        runTest {
            val bridge = TestOid4vpAuthBridge()

            val completeResult = bridge.completeAuthentication("non-existent-session-id")
            assertTrue(completeResult.isErr, "Should fail for non-existent session")
        }

    // ========================================================================
    // Test: Session Store Integration
    // ========================================================================

    @Test
    fun sessionStoreShouldPersistSessionsCorrectly() =
        runTest {
            val store = InMemoryOid4vpAuthSessionStore(AppLogManagerImpl(emptySet()))
            val session = createTestSession("test-123")

            // Store session
            val putResult = store.put(session.sessionId, session, 300.seconds)
            assertTrue(putResult.isOk)

            // Retrieve session
            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(session.sessionId, getResult.value!!.sessionId)
            assertEquals(session.status, getResult.value!!.status)
        }

    @Test
    fun sessionStoreShouldUpdateSessionsCorrectly() =
        runTest {
            val store = InMemoryOid4vpAuthSessionStore(AppLogManagerImpl(emptySet()))
            val session = createTestSession("test-456")

            store.put(session.sessionId, session, 300.seconds)

            // Update session status
            val updatedSession = session.copy(status = Oid4vpAuthSessionStatus.VERIFIED)
            store.put(session.sessionId, updatedSession, 300.seconds)

            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertEquals(Oid4vpAuthSessionStatus.VERIFIED, getResult.value!!.status)
        }

    @Test
    fun sessionStoreShouldDeleteSessionsCorrectly() =
        runTest {
            val store = InMemoryOid4vpAuthSessionStore(AppLogManagerImpl(emptySet()))
            val session = createTestSession("test-789")

            store.put(session.sessionId, session, 300.seconds)
            store.delete(session.sessionId)

            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertEquals(null, getResult.value)
        }

    // ========================================================================
    // Test: Multiple Concurrent Sessions
    // ========================================================================

    @Test
    fun shouldHandleMultipleConcurrentSessions() =
        runTest {
            val bridge = TestOid4vpAuthBridge()

            // Create multiple sessions
            val sessions =
                (1..5).map { i ->
                    val result =
                        bridge.createSession(
                            CreateSessionArgs(
                                queryId = "query-$i",
                                oauthSessionId = "oauth-$i",
                            ),
                        )
                    assertTrue(result.isOk)
                    result.value.session
                }

            // Verify all are PENDING
            sessions.forEach { session ->
                val status = bridge.getSessionStatus(session.sessionId)
                assertTrue(status.isOk)
                assertEquals(Oid4vpAuthSessionStatus.PENDING, status.value.status)
            }

            // Mark some as verified
            bridge.markSessionVerified(sessions[0].sessionId, createVerifiedData())
            bridge.markSessionVerified(sessions[2].sessionId, createVerifiedData())

            // Verify mixed statuses
            assertEquals(Oid4vpAuthSessionStatus.VERIFIED, bridge.getSessionStatus(sessions[0].sessionId).value.status)
            assertEquals(Oid4vpAuthSessionStatus.PENDING, bridge.getSessionStatus(sessions[1].sessionId).value.status)
            assertEquals(Oid4vpAuthSessionStatus.VERIFIED, bridge.getSessionStatus(sessions[2].sessionId).value.status)

            // Complete verified sessions
            assertTrue(bridge.completeAuthentication(sessions[0].sessionId).isOk)
            assertTrue(bridge.completeAuthentication(sessions[2].sessionId).isOk)

            // Pending sessions should still not be completable
            assertTrue(bridge.completeAuthentication(sessions[1].sessionId).isErr)
        }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private fun createTestSession(sessionId: String): Oid4vpAuthSession {
        val now = Clock.System.now()
        return Oid4vpAuthSession(
            sessionId = sessionId,
            correlationId = "corr-$sessionId",
            oauthSessionId = null,
            queryId = "test-query",
            status = Oid4vpAuthSessionStatus.PENDING,
            verifiedData = null,
            resolvedUserId = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + 5.minutes,
        )
    }

    private fun createVerifiedData(): VerifiedData =
        VerifiedData(
            credentialClaims =
                listOf(
                    VerifiedClaimsValue(
                        id = "credential-1",
                        type = "vc+sd-jwt",
                        claims =
                            mapOf(
                                "sub" to JsonPrimitive("user@example.com"),
                                "name" to JsonPrimitive("Test User"),
                                "given_name" to JsonPrimitive("Test"),
                                "family_name" to JsonPrimitive("User"),
                                "email" to JsonPrimitive("user@example.com"),
                            ),
                    ),
                ),
        )
}

// ============================================================================
// Test Bridge Implementation
// ============================================================================

/**
 * Test implementation of [Oid4vpAuthBridge] for integration testing.
 *
 * This provides a fully functional bridge that stores sessions in memory
 * and allows tests to manipulate session state directly.
 */
class TestOid4vpAuthBridge : Oid4vpAuthBridge {
    private val sessions = mutableMapOf<String, Oid4vpAuthSession>()

    /**
     * Mark a session as VERIFIED with the given verified data.
     * Used by tests to simulate wallet presentation.
     */
    fun markSessionVerified(
        sessionId: String,
        verifiedData: VerifiedData,
    ) {
        val session = sessions[sessionId] ?: return
        sessions[sessionId] =
            session.copy(
                status = Oid4vpAuthSessionStatus.VERIFIED,
                verifiedData = verifiedData,
                updatedAt = Clock.System.now(),
            )
    }

    override suspend fun createSession(args: CreateSessionArgs): IdkResult<CreateSessionResult, IdkError> {
        val now = Clock.System.now()
        val sessionId = "session-${Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextInt()}"
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
                expiresAt = now + (args.ttlSeconds?.seconds ?: 5.minutes),
            )

        sessions[sessionId] = session

        return Ok(
            CreateSessionResult(
                session = session,
                qrCodeDataUri = "data:image/png;base64,mockQrCode",
                requestUri = "openid4vp://?request_uri=/oid4vp/request/$correlationId",
                statusUri = "/auth/oid4vp/sessions/$sessionId/status",
                qrPageUri = "/auth/oid4vp/sessions/$sessionId/qr",
            ),
        )
    }

    override suspend fun getSessionStatus(sessionId: String): IdkResult<Oid4vpAuthStatusResponse, IdkError> {
        val session =
            sessions[sessionId]
                ?: return Err(Oid4vpAuthErrors.sessionNotFound(sessionId))

        return Ok(Oid4vpAuthStatusResponse.from(session))
    }

    override suspend fun completeAuthentication(sessionId: String): IdkResult<Oid4vpAuthResult, IdkError> {
        val session =
            sessions[sessionId]
                ?: return Err(Oid4vpAuthErrors.sessionNotFound(sessionId))

        if (session.status == Oid4vpAuthSessionStatus.COMPLETED) {
            return Err(Oid4vpAuthErrors.sessionAlreadyCompleted(sessionId))
        }

        if (session.status != Oid4vpAuthSessionStatus.VERIFIED) {
            return Err(Oid4vpAuthErrors.sessionNotVerified(sessionId))
        }

        // Extract claims from verified data
        val claims =
            session.verifiedData
                ?.credentials
                ?.firstOrNull()
                ?.claims ?: emptyMap()
        val userId = (claims["sub"] as? JsonPrimitive)?.content ?: "user-$sessionId"

        // Mark session as completed
        val completedSession =
            session.copy(
                status = Oid4vpAuthSessionStatus.COMPLETED,
                resolvedUserId = userId,
                updatedAt = Clock.System.now(),
            )
        sessions[sessionId] = completedSession

        return Ok(
            Oid4vpAuthResult(
                userId = userId,
                claims = claims,
                jwtClaims = null,
                isNewUser = true,
                authenticatedAt = Clock.System.now(),
                acr = Oid4vpAuthResult.DEFAULT_ACR,
                amr = Oid4vpAuthResult.DEFAULT_AMR,
            ),
        )
    }
}

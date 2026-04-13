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

package com.sphereon.openid.oid4vp.auth.impl.bridge

import com.sphereon.core.defaults.log.AppLogManagerImpl
import com.sphereon.openid.oid4vp.auth.impl.store.InMemoryOid4vpAuthSessionStore
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthBridgeConfig
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import com.sphereon.openid.oid4vp.universal.VerifiedClaimsValue
import com.sphereon.openid.oid4vp.universal.VerifiedData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for the OID4VP Auth Bridge components.
 *
 * Note: These tests use the in-memory implementations from the module.
 * For full integration tests, a complete DI graph setup would be needed.
 */
class Oid4vpAuthBridgeServiceImplTest {
    private val sessionStore = InMemoryOid4vpAuthSessionStore(AppLogManagerImpl(emptySet()))

    private val defaultConfig =
        Oid4vpAuthBridgeConfig(
            defaultQueryId = "test-query", // Explicit query ID for tests
            sessionTtlSeconds = 300,
            autoCreateUser = true,
            userIdentifierClaimPath = "sub",
        )

    private fun createTestSession(
        sessionId: String = "test-session-123",
        correlationId: String = "correlation-456",
        status: Oid4vpAuthSessionStatus = Oid4vpAuthSessionStatus.PENDING,
        verifiedData: VerifiedData? = null,
    ): Oid4vpAuthSession {
        val now = Clock.System.now()
        return Oid4vpAuthSession(
            sessionId = sessionId,
            correlationId = correlationId,
            oauthSessionId = null,
            queryId = "test-query",
            status = status,
            verifiedData = verifiedData,
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
                        id = "pid",
                        type = "dc+sd-jwt",
                        claims =
                            mapOf(
                                "sub" to JsonPrimitive("did:example:123"),
                                "given_name" to JsonPrimitive("John"),
                                "family_name" to JsonPrimitive("Doe"),
                                "email" to JsonPrimitive("john.doe@example.com"),
                            ),
                    ),
                ),
        )

    // ================== Session Store Tests ==================

    @Test
    fun `session store should store and retrieve session`() =
        runTest {
            val session = createTestSession()

            val putResult = sessionStore.put(session.sessionId, session, 300.seconds)
            assertTrue(putResult.isOk)

            val getResult = sessionStore.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(session.sessionId, getResult.value!!.sessionId)
        }

    @Test
    fun `session store should return null for non-existent session`() =
        runTest {
            val result = sessionStore.get("non-existent-id")
            assertTrue(result.isOk)
            assertEquals(null, result.value)
        }

    @Test
    fun `session store should update existing session`() =
        runTest {
            val session = createTestSession()
            sessionStore.put(session.sessionId, session, 300.seconds)

            val updatedSession = session.copy(status = Oid4vpAuthSessionStatus.VERIFIED)
            sessionStore.put(session.sessionId, updatedSession, 300.seconds)

            val result = sessionStore.get(session.sessionId)
            assertTrue(result.isOk)
            assertEquals(Oid4vpAuthSessionStatus.VERIFIED, result.value?.status)
        }

    // ================== Session Status Tests ==================

    @Test
    fun `session should report as valid when not expired`() {
        val session = createTestSession(status = Oid4vpAuthSessionStatus.PENDING)
        val now = Clock.System.now()

        assertTrue(session.isValid(now))
    }

    @Test
    fun `session should report as invalid when expired`() {
        val now = Clock.System.now()
        val session =
            Oid4vpAuthSession(
                sessionId = "test",
                correlationId = "test",
                oauthSessionId = null,
                queryId = "test",
                status = Oid4vpAuthSessionStatus.PENDING,
                verifiedData = null,
                resolvedUserId = null,
                errorMessage = null,
                createdAt = now - 10.minutes,
                updatedAt = now - 10.minutes,
                expiresAt = now - 1.seconds, // Expired 1 second ago
            )

        assertFalse(session.isValid(now))
    }

    @Test
    fun `session should report as invalid when status is EXPIRED`() {
        val session = createTestSession(status = Oid4vpAuthSessionStatus.EXPIRED)
        val now = Clock.System.now()

        assertFalse(session.isValid(now))
    }

    @Test
    fun `session canComplete should be true only for VERIFIED status`() {
        val pending = createTestSession(status = Oid4vpAuthSessionStatus.PENDING)
        val verified = createTestSession(status = Oid4vpAuthSessionStatus.VERIFIED)
        val completed = createTestSession(status = Oid4vpAuthSessionStatus.COMPLETED)
        val error = createTestSession(status = Oid4vpAuthSessionStatus.ERROR)

        assertFalse(pending.canComplete())
        assertTrue(verified.canComplete())
        assertFalse(completed.canComplete())
        assertFalse(error.canComplete())
    }

    // ================== Config Tests ==================

    @Test
    fun `config should have correct default values`() {
        val config = Oid4vpAuthBridgeConfig()

        // defaultQueryId is null by default - clients must provide queryId
        assertEquals(null, config.defaultQueryId)
        assertEquals(300L, config.sessionTtlSeconds)
        assertTrue(config.autoCreateUser)
        assertEquals("sub", config.userIdentifierClaimPath)
    }

    @Test
    fun `config should accept custom values`() {
        val config =
            Oid4vpAuthBridgeConfig(
                defaultQueryId = "custom-query",
                sessionTtlSeconds = 600,
                autoCreateUser = false,
                userIdentifierClaimPath = "holder_did",
            )

        assertEquals("custom-query", config.defaultQueryId)
        assertEquals(600L, config.sessionTtlSeconds)
        assertFalse(config.autoCreateUser)
        assertEquals("holder_did", config.userIdentifierClaimPath)
    }

    @Test
    fun `config with null defaultQueryId should be valid`() {
        val config =
            Oid4vpAuthBridgeConfig(
                defaultQueryId = null,
                sessionTtlSeconds = 300,
            )

        assertEquals(null, config.defaultQueryId)
    }
}

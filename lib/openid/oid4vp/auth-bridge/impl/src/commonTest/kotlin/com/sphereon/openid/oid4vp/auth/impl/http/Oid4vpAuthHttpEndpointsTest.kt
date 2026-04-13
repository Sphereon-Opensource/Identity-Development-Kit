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

import com.sphereon.core.api.http.HttpJson
import com.sphereon.core.defaults.log.AppLogManagerImpl
import com.sphereon.openid.oid4vp.auth.http.model.CreateOid4vpAuthSessionRequest
import com.sphereon.openid.oid4vp.auth.http.model.CreateOid4vpAuthSessionResponse
import com.sphereon.openid.oid4vp.auth.impl.store.InMemoryOid4vpAuthSessionStore
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for OID4VP Authentication Bridge HTTP endpoints.
 *
 * These tests verify the request/response serialization and
 * integration with the Oid4vpAuthSessionStore.
 */
class Oid4vpAuthHttpEndpointsTest {
    private val store = InMemoryOid4vpAuthSessionStore(AppLogManagerImpl(emptySet()))

    private fun createSampleSession(
        sessionId: String = "test-session",
        correlationId: String = "test-correlation",
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

    // ================== Request Serialization Tests ==================

    @Test
    fun `CreateOid4vpAuthSessionRequest should serialize correctly`() {
        val request =
            CreateOid4vpAuthSessionRequest(
                queryId = "test-query",
                oauthSessionId = "oauth-123",
                returnUrl = "https://example.com/callback",
                requestedProjection = "sts",
                ttlSeconds = 600,
            )

        val jsonString = HttpJson.restApi.encodeToString(CreateOid4vpAuthSessionRequest.serializer(), request)
        assertTrue(jsonString.contains("\"queryId\""))
        assertTrue(jsonString.contains("\"oauthSessionId\""))
        assertTrue(jsonString.contains("\"returnUrl\""))
        assertTrue(jsonString.contains("\"requestedProjection\""))
        assertTrue(jsonString.contains("\"ttlSeconds\""))

        val decoded = HttpJson.restApi.decodeFromString(CreateOid4vpAuthSessionRequest.serializer(), jsonString)
        assertEquals("test-query", decoded.queryId)
        assertEquals("oauth-123", decoded.oauthSessionId)
        assertEquals("https://example.com/callback", decoded.returnUrl)
        assertEquals("sts", decoded.requestedProjection)
        assertEquals(600, decoded.ttlSeconds)
    }

    @Test
    fun `CreateOid4vpAuthSessionRequest with null values`() {
        val request = CreateOid4vpAuthSessionRequest()

        val serialized = HttpJson.restApi.encodeToString(CreateOid4vpAuthSessionRequest.serializer(), request)
        val deserialized = HttpJson.restApi.decodeFromString(CreateOid4vpAuthSessionRequest.serializer(), serialized)

        assertNull(deserialized.queryId)
        assertNull(deserialized.oauthSessionId)
        assertNull(deserialized.returnUrl)
        assertNull(deserialized.requestedProjection)
        assertNull(deserialized.ttlSeconds)
    }

    // ================== Response Serialization Tests ==================

    @Test
    fun `CreateOid4vpAuthSessionResponse should serialize correctly`() {
        val response =
            CreateOid4vpAuthSessionResponse(
                sessionId = "session-123",
                status = Oid4vpAuthSessionStatus.PENDING,
                qrCodeDataUri = "data:image/png;base64,abc",
                requestUri = "openid4vp://request",
                statusUri = "/auth/oid4vp/sessions/123/status",
                qrPageUri = "/auth/oid4vp/sessions/123/qr",
                expiresAt = 1700000000000L,
            )

        val jsonString = HttpJson.restApi.encodeToString(CreateOid4vpAuthSessionResponse.serializer(), response)
        assertTrue(jsonString.contains("\"sessionId\""))
        assertTrue(jsonString.contains("\"qrCodeDataUri\""))
        assertTrue(jsonString.contains("\"requestUri\""))
        assertTrue(jsonString.contains("\"statusUri\""))
        assertTrue(jsonString.contains("\"qrPageUri\""))
        assertTrue(jsonString.contains("\"expiresAt\""))

        val decoded = HttpJson.restApi.decodeFromString(CreateOid4vpAuthSessionResponse.serializer(), jsonString)
        assertEquals("session-123", decoded.sessionId)
        assertEquals("data:image/png;base64,abc", decoded.qrCodeDataUri)
        assertEquals("openid4vp://request", decoded.requestUri)
    }

    // ================== Store Integration Tests ==================

    @Test
    fun `store should save and retrieve session`() =
        runTest {
            val session = createSampleSession()

            val saveResult = store.put(session.sessionId, session, 300.seconds)
            assertTrue(saveResult.isOk)

            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(session.sessionId, getResult.value!!.sessionId)
            assertEquals(session.correlationId, getResult.value!!.correlationId)
        }

    @Test
    fun `store should return null for non-existent session`() =
        runTest {
            val result = store.get("non-existent-id")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun `store should update existing session`() =
        runTest {
            val session = createSampleSession()
            store.put(session.sessionId, session, 300.seconds)

            val updatedSession = session.copy(status = Oid4vpAuthSessionStatus.VERIFIED)
            store.put(session.sessionId, updatedSession, 300.seconds)

            val result = store.get(session.sessionId)
            assertTrue(result.isOk)
            assertEquals(Oid4vpAuthSessionStatus.VERIFIED, result.value?.status)
        }

    @Test
    fun `store should delete session`() =
        runTest {
            val session = createSampleSession()
            store.put(session.sessionId, session, 300.seconds)

            val deleteResult = store.delete(session.sessionId)
            assertTrue(deleteResult.isOk)

            val getResult = store.get(session.sessionId)
            assertTrue(getResult.isOk)
            assertNull(getResult.value)
        }

    @Test
    fun `store delete non-existent should succeed`() =
        runTest {
            val deleteResult = store.delete("non-existent")
            assertTrue(deleteResult.isOk)
        }

    @Test
    fun `store should check existence`() =
        runTest {
            val session = createSampleSession()
            store.put(session.sessionId, session, 300.seconds)

            val existsResult = store.exists(session.sessionId)
            assertTrue(existsResult.isOk)
            assertTrue(existsResult.value)

            val notExistsResult = store.exists("non-existent")
            assertTrue(notExistsResult.isOk)
            assertFalse(notExistsResult.value)
        }

    @Test
    fun `store should handle multiple sessions`() =
        runTest {
            store.put("session-1", createSampleSession("session-1", "corr-1"), 300.seconds)
            store.put("session-2", createSampleSession("session-2", "corr-2"), 300.seconds)
            store.put("session-3", createSampleSession("session-3", "corr-3"), 300.seconds)

            assertEquals(3, store.sessionCount())

            val session1 = store.get("session-1")
            val session2 = store.get("session-2")
            val session3 = store.get("session-3")

            assertTrue(session1.isOk && session1.value != null)
            assertTrue(session2.isOk && session2.value != null)
            assertTrue(session3.isOk && session3.value != null)

            assertEquals("corr-1", session1.value!!.correlationId)
            assertEquals("corr-2", session2.value!!.correlationId)
            assertEquals("corr-3", session3.value!!.correlationId)
        }

    // ================== Session Status Validation Tests ==================

    @Test
    fun `session should report as valid when not expired`() {
        val session = createSampleSession(status = Oid4vpAuthSessionStatus.PENDING)
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
        val session = createSampleSession(status = Oid4vpAuthSessionStatus.EXPIRED)
        val now = Clock.System.now()

        assertFalse(session.isValid(now))
    }

    @Test
    fun `session canComplete should be true only for VERIFIED status`() {
        val pending = createSampleSession(status = Oid4vpAuthSessionStatus.PENDING)
        val verified = createSampleSession(status = Oid4vpAuthSessionStatus.VERIFIED)
        val completed = createSampleSession(status = Oid4vpAuthSessionStatus.COMPLETED)
        val error = createSampleSession(status = Oid4vpAuthSessionStatus.ERROR)

        assertFalse(pending.canComplete())
        assertTrue(verified.canComplete())
        assertFalse(completed.canComplete())
        assertFalse(error.canComplete())
    }
}

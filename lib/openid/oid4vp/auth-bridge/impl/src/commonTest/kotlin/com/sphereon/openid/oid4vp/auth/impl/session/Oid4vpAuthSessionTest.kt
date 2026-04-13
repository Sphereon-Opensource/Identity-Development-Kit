/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.auth.impl.session

import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Oid4vpAuthSessionTest {
    private fun createTestSession(
        status: Oid4vpAuthSessionStatus = Oid4vpAuthSessionStatus.PENDING,
        expiresIn: kotlin.time.Duration = 5.minutes,
    ): Oid4vpAuthSession {
        val now = Clock.System.now()
        return Oid4vpAuthSession(
            sessionId = "test-session",
            correlationId = "test-correlation",
            oauthSessionId = null,
            queryId = "test-query",
            status = status,
            verifiedData = null,
            resolvedUserId = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + expiresIn,
        )
    }

    @Test
    fun `isValid should return true for non-expired session`() {
        val session = createTestSession(expiresIn = 5.minutes)
        val now = Clock.System.now()
        assertTrue(session.isValid(now))
    }

    @Test
    fun `isValid should return false for expired session`() {
        val session = createTestSession(expiresIn = (-1).seconds)
        val now = Clock.System.now()
        assertFalse(session.isValid(now))
    }

    @Test
    fun `isValid should return false for EXPIRED status`() {
        val session =
            createTestSession(
                status = Oid4vpAuthSessionStatus.EXPIRED,
                expiresIn = 5.minutes,
            )
        val now = Clock.System.now()
        assertFalse(session.isValid(now))
    }

    @Test
    fun `canComplete should return true only for VERIFIED status`() {
        assertTrue(createTestSession(status = Oid4vpAuthSessionStatus.VERIFIED).canComplete())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.PENDING).canComplete())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.COMPLETED).canComplete())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.ERROR).canComplete())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.EXPIRED).canComplete())
    }

    @Test
    fun `isCompleted should return true only for COMPLETED status`() {
        assertTrue(createTestSession(status = Oid4vpAuthSessionStatus.COMPLETED).isCompleted())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.PENDING).isCompleted())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.VERIFIED).isCompleted())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.ERROR).isCompleted())
    }

    @Test
    fun `hasError should return true only for ERROR status`() {
        assertTrue(createTestSession(status = Oid4vpAuthSessionStatus.ERROR).hasError())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.PENDING).hasError())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.VERIFIED).hasError())
        assertFalse(createTestSession(status = Oid4vpAuthSessionStatus.COMPLETED).hasError())
    }
}

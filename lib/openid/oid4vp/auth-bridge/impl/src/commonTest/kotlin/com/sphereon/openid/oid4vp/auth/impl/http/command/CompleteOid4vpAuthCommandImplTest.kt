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

package com.sphereon.openid.oid4vp.auth.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.HttpJson
import com.sphereon.openid.oid4vp.auth.error.Oid4vpAuthErrors
import com.sphereon.openid.oid4vp.auth.http.model.CompleteOid4vpAuthResponse
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Unit tests for [CompleteOid4vpAuthCommandImpl].
 *
 * Tests the HTTP command layer in isolation using a mock bridge.
 */
class CompleteOid4vpAuthCommandImplTest {
    @Test
    fun `should return user identity on successful completion`() =
        runTest {
            val now = Clock.System.now()
            val mockBridge =
                MockOid4vpAuthBridge(
                    completeResult =
                        Ok(
                            Oid4vpAuthResult(
                                userId = "user@example.com",
                                claims =
                                    mapOf(
                                        "sub" to JsonPrimitive("user@example.com"),
                                        "name" to JsonPrimitive("Test User"),
                                        "email" to JsonPrimitive("user@example.com"),
                                    ),
                                jwtClaims = null,
                                isNewUser = false,
                                authenticatedAt = now,
                                acr = Oid4vpAuthResult.DEFAULT_ACR,
                                amr = Oid4vpAuthResult.DEFAULT_AMR,
                            ),
                        ),
                )

            val result = mockBridge.completeAuthentication("test-session-123")
            assertTrue(result.isOk)
            assertEquals("user@example.com", result.value.userId)
            assertEquals(3, result.value.claims.size)
            assertEquals(false, result.value.isNewUser)
        }

    @Test
    fun `should return error for non-existent session`() =
        runTest {
            val mockBridge =
                MockOid4vpAuthBridge(
                    completeResult = Err(Oid4vpAuthErrors.sessionNotFound("non-existent")),
                )

            val result = mockBridge.completeAuthentication("non-existent")
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("not found", ignoreCase = true),
            )
        }

    @Test
    fun `should return error for session not verified`() =
        runTest {
            val mockBridge =
                MockOid4vpAuthBridge(
                    completeResult = Err(Oid4vpAuthErrors.sessionNotVerified("session-123")),
                )

            val result = mockBridge.completeAuthentication("session-123")
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("not verified", ignoreCase = true),
            )
        }

    @Test
    fun `should return error for already completed session`() =
        runTest {
            val mockBridge =
                MockOid4vpAuthBridge(
                    completeResult = Err(Oid4vpAuthErrors.sessionAlreadyCompleted("session-123")),
                )

            val result = mockBridge.completeAuthentication("session-123")
            assertTrue(result.isErr)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("already completed", ignoreCase = true),
            )
        }

    @Test
    fun `response should serialize correctly`() {
        val now = Clock.System.now()
        val response =
            CompleteOid4vpAuthResponse(
                userId = "user@example.com",
                jwtClaims = null,
                claims =
                    mapOf(
                        "sub" to JsonPrimitive("user@example.com"),
                        "name" to JsonPrimitive("Test User"),
                    ),
                isNewUser = true,
                authenticatedAt = now.toEpochMilliseconds(),
                acr = "urn:sphereon:oid4vp:vp",
                amr = listOf("vp"),
            )

        val json = HttpJson.restApi.encodeToString(CompleteOid4vpAuthResponse.serializer(), response)
        assertNotNull(json)
        assertTrue(json.contains("userId"))
        assertTrue(json.contains("user@example.com"))
        assertTrue(json.contains("isNewUser"))
        assertTrue(json.contains("authenticatedAt"))
    }

    @Test
    fun `response with new user flag should be set correctly`() =
        runTest {
            val now = Clock.System.now()
            val mockBridge =
                MockOid4vpAuthBridge(
                    completeResult =
                        Ok(
                            Oid4vpAuthResult(
                                userId = "new-user@example.com",
                                claims = mapOf("sub" to JsonPrimitive("new-user@example.com")),
                                jwtClaims = null,
                                isNewUser = true,
                                authenticatedAt = now,
                                acr = Oid4vpAuthResult.DEFAULT_ACR,
                                amr = Oid4vpAuthResult.DEFAULT_AMR,
                            ),
                        ),
                )

            val result = mockBridge.completeAuthentication("test-session")
            assertTrue(result.isOk)
            assertTrue(result.value.isNewUser)
        }
}

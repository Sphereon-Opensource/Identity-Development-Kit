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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.oauth2.server.authorization.command.token.VerifyDeviceCodeGrantArgs
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryDeviceAuthorizationStorageImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationRecord
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Unit tests for [VerifyDeviceCodeGrantCommandImpl] covering the full RFC 8628 §3.4 / §3.5 state
 * machine: invalid grant, expired token, access denied, slow_down, authorization_pending, and
 * the success path. Uses the in-memory storage so tests compose the same record shape the
 * verification UI populates in production.
 */
class VerifyDeviceCodeGrantCommandImplTest {
    private val ctx = OAuth2ServerTestContext("verify-device-code-test", this)
    private val execution = ctx.execution

    private val baseInstant: Instant = Instant.parse("2026-04-26T12:00:00Z")
    private val pollInterval = 5
    private val deviceCode = "device-code-abc"
    private val userCode = "ABCD-EFGH"
    private val clientId = "device-client"

    private fun newStorage(): InMemoryDeviceAuthorizationStorageImpl = InMemoryDeviceAuthorizationStorageImpl(InMemoryOAuth2BackingStorageImpl())

    private fun newCommand(storage: InMemoryDeviceAuthorizationStorageImpl): VerifyDeviceCodeGrantCommandImpl =
        VerifyDeviceCodeGrantCommandImpl(
            execution = execution,
            deviceAuthorizationStorage = storage,
        )

    private suspend fun seedRecord(
        storage: InMemoryDeviceAuthorizationStorageImpl,
        state: DeviceAuthorizationState = DeviceAuthorizationState.PENDING,
        approvedSub: String? = null,
        approvedAuthTime: Instant? = null,
        approvedSessionId: String? = null,
        grantedScope: String? = null,
        scope: String? = "read",
        expiresAt: Instant = baseInstant + 600.seconds,
        lastPolledAt: Instant? = null,
    ): DeviceAuthorizationRecord {
        val record =
            DeviceAuthorizationRecord(
                deviceCode = deviceCode,
                userCode = userCode,
                clientId = clientId,
                scope = scope,
                resource = null,
                audience = null,
                state = state,
                createdAt = baseInstant,
                expiresAt = expiresAt,
                intervalSeconds = pollInterval,
                lastPolledAt = lastPolledAt,
                approvedSub = approvedSub,
                approvedAuthTime = approvedAuthTime,
                approvedSessionId = approvedSessionId,
                grantedScope = grantedScope,
            )
        val createResult = storage.create(record)
        assertTrue(createResult.isOk, "Failed to seed device-authorization record")
        return record
    }

    @Test
    fun unknownDeviceCodeReturnsInvalidGrant() =
        runTest {
            val storage = newStorage()
            val command = newCommand(storage)

            val result =
                command.execute(
                    VerifyDeviceCodeGrantArgs(deviceCode = "missing", clientId = clientId, now = baseInstant),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_grant", result.error.code)
        }

    @Test
    fun clientIdMismatchReturnsInvalidGrant() =
        runTest {
            val storage = newStorage()
            seedRecord(storage)
            val command = newCommand(storage)

            val result =
                command.execute(
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = "different-client", now = baseInstant),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_grant", result.error.code)
        }

    @Test
    fun consumedDeviceCodeReturnsInvalidGrant() =
        runTest {
            val storage = newStorage()
            seedRecord(storage, state = DeviceAuthorizationState.CONSUMED)
            val command = newCommand(storage)

            val result =
                command.execute(
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = clientId, now = baseInstant),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_grant", result.error.code)
        }

    @Test
    fun expiredRecordReturnsExpiredToken() =
        runTest {
            val storage = newStorage()
            // Pending record whose expiresAt is already before `now`.
            seedRecord(storage, expiresAt = baseInstant - 1.seconds)
            val command = newCommand(storage)

            val result =
                command.execute(
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = clientId, now = baseInstant),
                )

            assertTrue(result.isErr)
            assertEquals("expired_token", result.error.code)

            // Verify the record was transitioned to EXPIRED so subsequent polls observe the
            // explicit terminal state rather than re-running the expiry check.
            val stored = storage.findByDeviceCode(deviceCode)
            assertTrue(stored.isOk)
            assertEquals(DeviceAuthorizationState.EXPIRED, stored.value?.state)
        }

    @Test
    fun pendingFirstPollReturnsAuthorizationPending() =
        runTest {
            val storage = newStorage()
            seedRecord(storage, lastPolledAt = null)
            val command = newCommand(storage)

            val result =
                command.execute(
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = clientId, now = baseInstant),
                )

            assertTrue(result.isErr)
            assertEquals("authorization_pending", result.error.code)

            // First poll updates lastPolledAt so the next quick poll triggers slow_down.
            val stored = storage.findByDeviceCode(deviceCode)
            assertTrue(stored.isOk)
            assertEquals(baseInstant, stored.value?.lastPolledAt)
        }

    @Test
    fun pendingTooFastSecondPollReturnsSlowDown() =
        runTest {
            val storage = newStorage()
            seedRecord(storage, lastPolledAt = baseInstant)
            val command = newCommand(storage)

            val result =
                command.execute(
                    // Polled only 2s after the previous poll, with interval = 5s.
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = clientId, now = baseInstant + 2.seconds),
                )

            assertTrue(result.isErr)
            assertEquals("slow_down", result.error.code)

            // slow_down does NOT advance lastPolledAt; the device's next poll is still measured
            // against the original timestamp.
            val stored = storage.findByDeviceCode(deviceCode)
            assertTrue(stored.isOk)
            assertEquals(baseInstant, stored.value?.lastPolledAt)
        }

    @Test
    fun pendingNormalSecondPollReturnsAuthorizationPending() =
        runTest {
            val storage = newStorage()
            seedRecord(storage, lastPolledAt = baseInstant)
            val command = newCommand(storage)

            val result =
                command.execute(
                    // Polled exactly at the interval boundary: must NOT slow_down.
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = clientId, now = baseInstant + pollInterval.seconds),
                )

            assertTrue(result.isErr)
            assertEquals("authorization_pending", result.error.code)

            val stored = storage.findByDeviceCode(deviceCode)
            assertTrue(stored.isOk)
            assertEquals(baseInstant + pollInterval.seconds, stored.value?.lastPolledAt)
        }

    @Test
    fun deniedRecordReturnsAccessDenied() =
        runTest {
            val storage = newStorage()
            seedRecord(storage, state = DeviceAuthorizationState.DENIED)
            val command = newCommand(storage)

            val result =
                command.execute(
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = clientId, now = baseInstant),
                )

            assertTrue(result.isErr)
            assertEquals("access_denied", result.error.code)
        }

    @Test
    fun approvedRecordReturnsVerifiedGrant() =
        runTest {
            val storage = newStorage()
            seedRecord(
                storage,
                state = DeviceAuthorizationState.APPROVED,
                approvedSub = "user-42",
                approvedAuthTime = baseInstant - 30.seconds,
                approvedSessionId = "session-42",
                grantedScope = "read offline_access",
            )
            val command = newCommand(storage)

            val result =
                command.execute(
                    VerifyDeviceCodeGrantArgs(deviceCode = deviceCode, clientId = clientId, now = baseInstant),
                )

            assertTrue(result.isOk, "Expected approved record to verify successfully")
            val grant = result.value
            assertEquals(deviceCode, grant.deviceCode)
            assertEquals(clientId, grant.clientId)
            assertEquals("user-42", grant.subject)
            assertEquals(baseInstant - 30.seconds, grant.authTime)
            assertEquals("session-42", grant.sessionId)
            assertEquals("read offline_access", grant.grantedScope)

            // Verify is non-destructive: the orchestrator is responsible for consume() after
            // tokens are minted, so the record should still be APPROVED here.
            val stored = storage.findByDeviceCode(deviceCode)
            assertTrue(stored.isOk)
            assertEquals(DeviceAuthorizationState.APPROVED, stored.value?.state)
        }
}

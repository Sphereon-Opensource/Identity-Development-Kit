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

package com.sphereon.oauth2.server.authorization.impl.command.attestation

import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryAttestationChallengeStorage
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CreateAttestationChallengeCommandImplTest {
    private val ctx = OAuth2ServerTestContext("attestation-challenge-test", this)
    private val challengeStorage = InMemoryAttestationChallengeStorage(defaultSecureRandom())
    private val command = CreateAttestationChallengeCommandImpl(ctx.execution, challengeStorage)

    @Test
    fun testGenerateChallenge() =
        runTest {
            val result = command.execute(CreateAttestationChallengeArgs())

            assertTrue(result.isOk)
            val response = result.value
            assertTrue(response.attestationChallenge.isNotEmpty())
            // 128-bit nonce = 16 bytes = 32 hex chars
            assertEquals(32, response.attestationChallenge.length)
        }

    @Test
    fun testGenerateUniqueChallenges() =
        runTest {
            val result1 = command.execute(CreateAttestationChallengeArgs())
            val result2 = command.execute(CreateAttestationChallengeArgs())

            assertTrue(result1.isOk)
            assertTrue(result2.isOk)
            assertNotEquals(result1.value.attestationChallenge, result2.value.attestationChallenge)
        }

    @Test
    fun testChallengeCanBeConsumed() =
        runTest {
            val result = command.execute(CreateAttestationChallengeArgs())
            assertTrue(result.isOk)
            val challenge = result.value.attestationChallenge

            // Should succeed on first use
            val consumeResult = challengeStorage.verifyAndConsumeChallenge(challenge)
            assertTrue(consumeResult.isOk)
        }

    @Test
    fun testChallengeCannotBeReused() =
        runTest {
            val result = command.execute(CreateAttestationChallengeArgs())
            assertTrue(result.isOk)
            val challenge = result.value.attestationChallenge

            // First use succeeds
            val consumeResult1 = challengeStorage.verifyAndConsumeChallenge(challenge)
            assertTrue(consumeResult1.isOk)

            // Second use fails (replay protection)
            val consumeResult2 = challengeStorage.verifyAndConsumeChallenge(challenge)
            assertTrue(consumeResult2.isErr)
        }

    @Test
    fun testUnknownChallengeFails() =
        runTest {
            val consumeResult = challengeStorage.verifyAndConsumeChallenge("nonexistent-challenge")
            assertTrue(consumeResult.isErr)
        }
}

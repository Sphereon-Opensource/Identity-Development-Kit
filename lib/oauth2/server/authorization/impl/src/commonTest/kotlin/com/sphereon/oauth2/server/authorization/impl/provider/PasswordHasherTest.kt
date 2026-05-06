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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.decodeFromBase64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    private val deploymentSalt = "SGVsbG9TYWx0Rm9yVGVzdHMxMjM0NTY3ODkw".decodeFromBase64()
    private val iterations = 1_000

    @Test
    fun sameUsernameSamePasswordProducesSameHash() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val first = hasher.hash("alice", "secret")
            val second = hasher.hash("alice", "secret")
            assertEquals(first, second)
        }

    @Test
    fun differentUsernamesProduceDifferentHashesEvenForSamePassword() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val alice = hasher.hash("alice", "shared-password")
            val bob = hasher.hash("bob", "shared-password")
            assertNotEquals(alice, bob)
        }

    @Test
    fun differentDeploymentSaltsProduceDifferentHashes() =
        runTest {
            val hasherA = PasswordHasher(deploymentSalt, iterations)
            val otherSalt = "QW5vdGhlclNhbHRGb3JUZXN0cyEhISExMjM0NTY3OA==".decodeFromBase64()
            val hasherB = PasswordHasher(otherSalt, iterations)
            val a = hasherA.hash("alice", "password")
            val b = hasherB.hash("alice", "password")
            assertNotEquals(a, b)
        }

    @Test
    fun verifyAcceptsCorrectPassword() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val hash = hasher.hash("alice", "secret")
            assertTrue(hasher.verify("alice", "secret", hash))
        }

    @Test
    fun verifyRejectsWrongPassword() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val hash = hasher.hash("alice", "secret")
            assertFalse(hasher.verify("alice", "wrong", hash))
        }

    @Test
    fun verifyRejectsWrongUsername() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val hash = hasher.hash("alice", "secret")
            assertFalse(hasher.verify("bob", "secret", hash))
        }

    @Test
    fun verifyRejectsTamperedHashOfDifferentLength() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val hash = hasher.hash("alice", "secret")
            // Truncate by a few base64 chars to change the decoded length
            val truncated = hash.substring(0, hash.length - 4)
            assertFalse(hasher.verify("alice", "secret", truncated))
        }

    @Test
    fun verifyRejectsMalformedBase64Input() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            assertFalse(hasher.verify("alice", "secret", "%%% not base64 %%%"))
        }

    @Test
    fun honoursIterationCountInProducedHash() =
        runTest {
            val low = PasswordHasher(deploymentSalt, iterations = 1_000)
            val high = PasswordHasher(deploymentSalt, iterations = 5_000)
            val a = low.hash("alice", "secret")
            val b = high.hash("alice", "secret")
            assertNotEquals(a, b)
        }

    @Test
    fun honoursKeyLengthBytesInProducedHash() =
        runTest {
            val short = PasswordHasher(deploymentSalt, iterations, keyLengthBytes = 16)
            val long = PasswordHasher(deploymentSalt, iterations, keyLengthBytes = 32)
            val a = short.hash("alice", "secret").decodeFromBase64()
            val b = long.hash("alice", "secret").decodeFromBase64()
            assertEquals(16, a.size)
            assertEquals(32, b.size)
        }
}

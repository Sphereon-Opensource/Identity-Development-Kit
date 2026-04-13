/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.AttestationChallengeStorage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * In-memory implementation of AttestationChallengeStorage.
 *
 * Suitable for development/testing only.
 * Production deployments should use Redis with TTL.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AttestationChallengeStorage>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryAttestationChallengeStorage", exact = true)
class InMemoryAttestationChallengeStorage : AttestationChallengeStorage {

    private data class ChallengeData(
        val expiresAt: Instant,
        val used: Boolean = false
    )

    private val challenges = mutableMapOf<String, ChallengeData>()

    override suspend fun generateChallenge(): IdkResult<String, AuthorizationServerError.StorageError> {
        return try {
            cleanupExpired()
            val bytes = Random.Default.nextBytes(16)
            val challenge = bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
            val expiresAt = Clock.System.now() + 120.seconds
            challenges[challenge] = ChallengeData(expiresAt = expiresAt)
            Ok(challenge)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "generateChallenge",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    override suspend fun verifyAndConsumeChallenge(
        challenge: String
    ): IdkResult<Unit, AuthorizationServerError> {
        return try {
            val data = challenges[challenge]
                ?: return Err(AuthorizationServerError.InvalidClientAttestation(
                    details = "Challenge not found: $challenge"
                ))

            if (data.expiresAt < Clock.System.now()) {
                challenges.remove(challenge)
                return Err(AuthorizationServerError.InvalidClientAttestation(
                    details = "Challenge expired"
                ))
            }

            if (data.used) {
                return Err(AuthorizationServerError.InvalidClientAttestation(
                    details = "Challenge already used (replay attack detected)"
                ))
            }

            challenges[challenge] = data.copy(used = true)
            Ok(Unit)
        } catch (e: Exception) {
            Err(AuthorizationServerError.StorageError(
                operation = "verifyAndConsumeChallenge",
                details = e.message ?: "Unknown error",
                exception = e
            ))
        }
    }

    private fun cleanupExpired() {
        val now = Clock.System.now()
        challenges.entries.removeAll { it.value.expiresAt < now }
    }
}

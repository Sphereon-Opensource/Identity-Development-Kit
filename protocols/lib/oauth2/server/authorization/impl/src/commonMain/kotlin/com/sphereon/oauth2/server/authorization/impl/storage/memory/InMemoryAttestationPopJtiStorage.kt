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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.AttestationPopJtiStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * In-memory implementation of [AttestationPopJtiStorage] for development/testing and single-node
 * deployments. Production deployments with multiple AS replicas must back this with a shared
 * store (e.g. Redis with TTL) so jti dedup is global across replicas.
 *
 * Replay detection is keyed on the raw jti string. The sliding window is anchored on the PoP
 * `iat` plus the per-call [windowSeconds]; entries past their window are evicted lazily on each
 * record attempt to keep the table bounded.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AttestationPopJtiStorage>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryAttestationPopJtiStorage", exact = true)
class InMemoryAttestationPopJtiStorage : AttestationPopJtiStorage {
    private val seen = mutableMapOf<String, Instant>()
    private val mutex = Mutex()

    override suspend fun recordOrReject(
        jti: String,
        iat: Long,
        windowSeconds: Int,
    ): IdkResult<Unit, AuthorizationServerError> =
        try {
            mutex.withLock {
                val now = Clock.System.now()
                seen.entries.removeAll { it.value < now }
                if (seen.containsKey(jti)) {
                    Err(
                        AuthorizationServerError.InvalidClientAttestation(
                            details = "Client Attestation PoP jti has been seen before (replay attack detected)",
                        ),
                    )
                } else {
                    val expiresAt = Instant.fromEpochSeconds(iat) + windowSeconds.seconds
                    seen[jti] = expiresAt
                    Ok(Unit)
                }
            }
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "recordOrReject",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
}

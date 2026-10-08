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

package com.sphereon.oauth2.client.impl.transaction

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.client.transaction.OidcLoginTransaction
import com.sphereon.oauth2.client.transaction.OidcLoginTransactionStore
import com.sphereon.oauth2.common.error.Oauth2Error
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * In-memory default [OidcLoginTransactionStore]. Single-node, sufficient for OIDF conformance and
 * development. Production multi-node RPs must substitute a distributed store (Redis/Postgres) —
 * tracked as a WP5 follow-up.
 *
 * Concurrency posture: atomicity is provided by synchronous map mutations. `consumeByState`
 * removes before returning so two concurrent callback handlers racing on the same `state` cannot
 * both succeed. Production implementations must back this with native atomic primitives.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OidcLoginTransactionStore>())
public class InMemoryOidcLoginTransactionStore : OidcLoginTransactionStore {
    private val entries = HashMap<Key, OidcLoginTransaction>()

    override suspend fun put(transaction: OidcLoginTransaction): IdkResult<Unit, Oauth2Error> {
        entries[Key(transaction.state, transaction.tenantId)] = transaction
        return Ok(Unit)
    }

    override suspend fun consumeByState(
        state: String,
        tenantId: String?,
    ): IdkResult<OidcLoginTransaction, Oauth2Error> {
        val key = Key(state, tenantId)
        val transaction =
            entries.remove(key)
                ?: return Err(
                    Oauth2Error.InvalidGrant(
                        reason = "No OIDC login transaction for state",
                    ),
                )
        if (transaction.expiresAt < Clock.System.now()) {
            // Already removed above; nothing else to clean up.
            return Err(
                Oauth2Error.InvalidGrant(
                    reason = "OIDC login transaction expired",
                ),
            )
        }
        return Ok(transaction)
    }

    private data class Key(
        val state: String,
        val tenantId: String?,
    )
}

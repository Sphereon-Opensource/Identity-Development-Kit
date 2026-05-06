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

import com.sphereon.oauth2.client.transaction.OidcLoginTransaction
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class InMemoryOidcLoginTransactionStoreTest {
    private fun newTransaction(
        state: String = "state-abc",
        tenantId: String? = null,
        expiresIn: kotlin.time.Duration = 5.minutes,
        now: kotlin.time.Instant = Clock.System.now(),
    ): OidcLoginTransaction =
        OidcLoginTransaction(
            state = state,
            nonce = "nonce-xyz",
            pkceVerifier = "verifier-123",
            issuer = "https://issuer.example.com",
            redirectUri = "https://rp.example.com/callback",
            responseMode = OAuth2ResponseMode.QUERY,
            createdAt = now,
            expiresAt = now + expiresIn,
            tenantId = tenantId,
        )

    @Test
    fun put_thenConsume_returnsTransaction() =
        runTest {
            val store = InMemoryOidcLoginTransactionStore()
            val tx = newTransaction()

            val putResult = store.put(tx)
            assertTrue(putResult.isOk)

            val consumeResult = store.consumeByState(tx.state)
            assertTrue(consumeResult.isOk)
            assertEquals(tx, consumeResult.value)
        }

    @Test
    fun consume_unknownState_returnsErr() =
        runTest {
            val store = InMemoryOidcLoginTransactionStore()

            val result = store.consumeByState("never-stored")
            assertTrue(result.isErr)
            assertTrue(result.error is Oauth2Error.InvalidGrant)
        }

    @Test
    fun consume_afterExpiry_returnsErr() =
        runTest {
            val store = InMemoryOidcLoginTransactionStore()
            // Record a transaction that expired in the past so the store treats it as unknown.
            val tx = newTransaction(expiresIn = (-1).minutes)
            assertTrue(store.put(tx).isOk)

            val result = store.consumeByState(tx.state)
            assertTrue(result.isErr)
            assertTrue(result.error is Oauth2Error.InvalidGrant)
        }

    @Test
    fun consume_twice_secondReturnsErr() =
        runTest {
            val store = InMemoryOidcLoginTransactionStore()
            val tx = newTransaction()
            assertTrue(store.put(tx).isOk)

            val first = store.consumeByState(tx.state)
            val second = store.consumeByState(tx.state)

            assertTrue(first.isOk)
            assertTrue(second.isErr, "second consume must fail — atomic consume prevents replay")
            assertTrue(second.error is Oauth2Error.InvalidGrant)
        }

    @Test
    fun consume_wrongTenant_returnsErr() =
        runTest {
            val store = InMemoryOidcLoginTransactionStore()
            val tx = newTransaction(tenantId = "tenant-a")
            assertTrue(store.put(tx).isOk)

            val result = store.consumeByState(tx.state, tenantId = "tenant-b")
            assertTrue(result.isErr, "state lookup must be scoped to its owning tenant")
            assertTrue(result.error is Oauth2Error.InvalidGrant)
        }

    @Test
    fun put_differentTenants_sameState_doNotCollide() =
        runTest {
            val store = InMemoryOidcLoginTransactionStore()
            val txA = newTransaction(state = "shared", tenantId = "tenant-a")
            val txB = newTransaction(state = "shared", tenantId = "tenant-b")

            assertTrue(store.put(txA).isOk)
            assertTrue(store.put(txB).isOk)

            val consumedA = store.consumeByState("shared", tenantId = "tenant-a")
            val consumedB = store.consumeByState("shared", tenantId = "tenant-b")

            assertTrue(consumedA.isOk)
            assertTrue(consumedB.isOk)
            assertEquals("tenant-a", consumedA.value.tenantId)
            assertEquals("tenant-b", consumedB.value.tenantId)
        }

    @Test
    fun put_sameState_sameTenant_second_overwrites_first() =
        runTest {
            // Not strictly required by the spec, but the concurrency posture matters: if a user
            // re-initiates login with a collision, we don't leave a ghost entry pointing at stale
            // PKCE/nonce. Document the chosen behaviour: last-write-wins and the earlier state is
            // invalidated.
            val store = InMemoryOidcLoginTransactionStore()
            val first = newTransaction(state = "s").copy(pkceVerifier = "first")
            val second = newTransaction(state = "s").copy(pkceVerifier = "second")

            assertTrue(store.put(first).isOk)
            assertTrue(store.put(second).isOk)

            val consumed = store.consumeByState("s")
            assertTrue(consumed.isOk)
            assertEquals("second", consumed.value.pkceVerifier)

            assertFalse(store.consumeByState("s").isOk)
        }
}

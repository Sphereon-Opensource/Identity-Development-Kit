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

import com.sphereon.oauth2.server.authorization.storage.ClientRegistrationStatus
import com.sphereon.oauth2.server.authorization.storage.DynamicClientRegistrationMetadata
import com.sphereon.oauth2.server.authorization.storage.StoredClientRegistration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Pins the [ClientRegistrationStore] contract for the single client store. These are SPI-contract
 * tests rather than implementation tests: the same expectations hold for the EDK Postgres store.
 *
 * The identity assertions matter most. A client id is unique only within one authorization server,
 * and a tenant hosts several, so a store that keys on tenant and client id alone lets two servers
 * overwrite each other's clients.
 */
class InMemoryClientRegistrationStoreTest {
    @Test
    fun sameClientIdOnTwoServersAreDistinctClients() =
        runTest {
            val store = InMemoryClientRegistrationStore()
            store.save(TENANT, registration(SERVER_A, CLIENT, clientName = "on A")).assertOk()
            store.save(TENANT, registration(SERVER_B, CLIENT, clientName = "on B")).assertOk()

            val onA = store.findByClientId(TENANT, SERVER_A, CLIENT).valueOrFail()
            val onB = store.findByClientId(TENANT, SERVER_B, CLIENT).valueOrFail()

            assertNotNull(onA)
            assertNotNull(onB)
            assertEquals("on A", onA.registration.clientName)
            assertEquals("on B", onB.registration.clientName)
        }

    @Test
    fun listIsScopedToOneAuthorizationServer() =
        runTest {
            val store = InMemoryClientRegistrationStore()
            store.save(TENANT, registration(SERVER_A, "first")).assertOk()
            store.save(TENANT, registration(SERVER_A, "second")).assertOk()
            store.save(TENANT, registration(SERVER_B, "third")).assertOk()

            val onA = store.list(TENANT, SERVER_A).valueOrFail()
            val onB = store.list(TENANT, SERVER_B).valueOrFail()

            assertEquals(setOf("first", "second"), onA.map { it.clientId }.toSet())
            assertEquals(setOf("third"), onB.map { it.clientId }.toSet())
        }

    @Test
    fun revokingOnOneServerLeavesTheOtherServable() =
        runTest {
            val store = InMemoryClientRegistrationStore()
            store.save(TENANT, registration(SERVER_A, CLIENT)).assertOk()
            store.save(TENANT, registration(SERVER_B, CLIENT)).assertOk()

            assertTrue(store.revoke(TENANT, SERVER_A, CLIENT).valueOrFail())

            assertEquals(
                ClientRegistrationStatus.REVOKED,
                store.findByClientId(TENANT, SERVER_A, CLIENT).valueOrFail()?.status,
            )
            assertEquals(
                ClientRegistrationStatus.ACTIVE,
                store.findByClientId(TENANT, SERVER_B, CLIENT).valueOrFail()?.status,
            )
        }

    @Test
    fun deleteReturnsTheRemovedRowSoItsSecretCanBePurged() =
        runTest {
            val store = InMemoryClientRegistrationStore()
            store.save(
                TENANT,
                registration(SERVER_A, CLIENT).copy(secretResourceHandle = "sec_handle", secretRecordVersion = 7L),
            ).assertOk()

            val removed = store.delete(TENANT, SERVER_A, CLIENT).valueOrFail()

            assertNotNull(removed)
            assertEquals("sec_handle", removed.secretResourceHandle)
            assertEquals(7L, removed.secretRecordVersion)
            assertNull(store.findByClientId(TENANT, SERVER_A, CLIENT).valueOrFail())
        }

    @Test
    fun deletingAnAbsentClientReportsNothingRemoved() =
        runTest {
            val store = InMemoryClientRegistrationStore()
            assertNull(store.delete(TENANT, SERVER_A, "never-registered").valueOrFail())
        }

    @Test
    fun revocationIsOneWay() =
        runTest {
            val store = InMemoryClientRegistrationStore()
            store.save(TENANT, registration(SERVER_A, CLIENT)).assertOk()
            assertTrue(store.revoke(TENANT, SERVER_A, CLIENT).valueOrFail())

            val reactivated = store.save(TENANT, registration(SERVER_A, CLIENT))

            assertTrue(reactivated.isErr, "a revoked client must not be resurrected by saving over it")
        }

    private fun registration(
        authorizationServerId: String,
        clientId: String,
        clientName: String? = null,
    ): StoredClientRegistration {
        val now = Clock.System.now()
        return StoredClientRegistration(
            tenantId = TENANT,
            authorizationServerId = authorizationServerId,
            clientId = clientId,
            registeredAt = now,
            updatedAt = now,
            registration = DynamicClientRegistrationMetadata(clientName = clientName),
        )
    }

    private companion object {
        const val TENANT = "tenant-a"
        const val SERVER_A = "as-alpha"
        const val SERVER_B = "as-beta"
        const val CLIENT = "shared-client-id"
    }
}

private fun <V, E> com.sphereon.core.api.IdkResult<V, E>.assertOk() {
    assertTrue(isOk, "expected a successful store result but got $this")
}

private fun <V, E> com.sphereon.core.api.IdkResult<V, E>.valueOrFail(): V {
    assertTrue(isOk, "expected a successful store result but got $this")
    return value
}

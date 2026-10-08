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

package com.sphereon.openid.oid4vci.issuer.impl.store

import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KvNotificationStateStoreTest {
    private val instanceId = "issuer-instance-notification-store"

    private fun createStore(): KvNotificationStateStore {
        val kvStoreManager = InMemoryTestKvStoreManager()
        return KvNotificationStateStore(
            kvStoreManager = kvStoreManager,
            kvStoreService = UnconfiguredKvStoreService,
            execution = NoOpSessionExecution(),
        )
    }

    @Test
    fun unknownNotificationCannotBeRecorded() =
        runTest {
            val store = createStore()

            val result = store.recordNotification("notification-unknown", CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
            assertTrue(result.isOk)
            assertNull(result.getOrThrow())
        }

    @Test
    fun registeredNotificationReturnsExactProtocolSessionAndInstance() =
        runTest {
            val store = createStore()
            store.registerNotification("notification-001", "protocol-session-002", instanceId, ttlSeconds = 300).getOrThrow()

            val recordResult = store.recordNotification("notification-001", CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
            assertTrue(recordResult.isOk, "recordNotification should succeed")
            assertEquals("protocol-session-002", recordResult.getOrThrow()?.protocolSessionId)
            assertEquals(instanceId, recordResult.getOrThrow()?.instanceId)
            assertTrue(recordResult.getOrThrow()?.firstReceipt == true)
        }

    @Test
    fun racingReceiptsAreAtomicAndIdempotent() =
        runTest {
            val store = createStore()
            store.registerNotification("notification-002", "protocol-session-shared-config-b", instanceId, ttlSeconds = 300).getOrThrow()

            val receipts =
                coroutineScope {
                    (1..8)
                        .map {
                            async {
                                store.recordNotification("notification-002", CredentialNotificationEvent.CREDENTIAL_ACCEPTED).getOrThrow()
                            }
                        }.awaitAll()
                }.filterNotNull()

            assertEquals(8, receipts.size)
            assertEquals(1, receipts.count { it.firstReceipt })
            assertTrue(receipts.all { it.protocolSessionId == "protocol-session-shared-config-b" })
            assertTrue(receipts.all { it.instanceId == instanceId })
        }

    @Test
    fun notificationIdCannotBeReboundToAnotherSession() =
        runTest {
            val store = createStore()
            store.registerNotification("notification-003", "protocol-session-a", instanceId, ttlSeconds = 300).getOrThrow()

            val conflict =
                store.registerNotification(
                    "notification-003",
                    "protocol-session-b",
                    "issuer-instance-conflicting-notification-binding",
                    ttlSeconds = 300,
                )

            assertFalse(conflict.isOk)
            assertEquals("NOTIFICATION_BINDING_CONFLICT", conflict.error.code)
        }
}

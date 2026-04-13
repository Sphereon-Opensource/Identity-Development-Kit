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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KvNotificationStateStoreTest {
    private fun createStore(): KvNotificationStateStore {
        val kvStoreManager = InMemoryTestKvStoreManager()
        return KvNotificationStateStore(
            kvStoreManager = kvStoreManager,
            execution = NoOpSessionExecution(),
        )
    }

    @Test
    fun isProcessedReturnsFalseForUnknownNotification() =
        runTest {
            val store = createStore()

            val result = store.isProcessed("notification-unknown")
            assertTrue(result.isOk, "isProcessed should succeed")
            assertFalse(result.getOrThrow())
        }

    @Test
    fun recordNotificationThenIsProcessedReturnsTrue() =
        runTest {
            val store = createStore()

            val recordResult = store.recordNotification("notification-001", CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
            assertTrue(recordResult.isOk, "recordNotification should succeed")

            val result = store.isProcessed("notification-001")
            assertTrue(result.isOk, "isProcessed should succeed")
            assertTrue(result.getOrThrow())
        }

    @Test
    fun recordingSameNotificationIdTwiceIsIdempotent() =
        runTest {
            val store = createStore()

            val firstResult = store.recordNotification("notification-002", CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
            assertTrue(firstResult.isOk, "first recordNotification should succeed")

            val secondResult = store.recordNotification("notification-002", CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
            assertTrue(secondResult.isOk, "second recordNotification should succeed")

            val processed = store.isProcessed("notification-002")
            assertTrue(processed.isOk, "isProcessed should succeed")
            assertTrue(processed.getOrThrow())
        }
}

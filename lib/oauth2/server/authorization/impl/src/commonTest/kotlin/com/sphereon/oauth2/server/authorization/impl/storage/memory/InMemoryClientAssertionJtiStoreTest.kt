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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class InMemoryClientAssertionJtiStoreTest {
    @Test
    fun recordIfNew_firstCall_returnsTrue() =
        runTest {
            val store = InMemoryClientAssertionJtiStore()
            val accepted = store.recordIfNew("client1", "jti-1", Clock.System.now() + 5.minutes)
            assertTrue(accepted)
        }

    @Test
    fun recordIfNew_replayWithinTtl_returnsFalse() =
        runTest {
            val store = InMemoryClientAssertionJtiStore()
            val expiry = Clock.System.now() + 5.minutes
            assertTrue(store.recordIfNew("client1", "jti-1", expiry))
            assertFalse(store.recordIfNew("client1", "jti-1", expiry))
        }

    @Test
    fun recordIfNew_afterExpiry_canReuse() =
        runTest {
            val store = InMemoryClientAssertionJtiStore()
            // Record with an already-past expiry — opportunistic GC removes the entry on the next
            // call, so the replay is treated as new.
            assertTrue(store.recordIfNew("client1", "jti-1", Clock.System.now() - 1.minutes))
            assertTrue(store.recordIfNew("client1", "jti-1", Clock.System.now() + 5.minutes))
        }

    @Test
    fun recordIfNew_differentClients_shareNoJtiSpace() =
        runTest {
            val store = InMemoryClientAssertionJtiStore()
            val expiry = Clock.System.now() + 5.minutes
            assertTrue(store.recordIfNew("client1", "jti-1", expiry))
            assertTrue(store.recordIfNew("client2", "jti-1", expiry))
        }
}

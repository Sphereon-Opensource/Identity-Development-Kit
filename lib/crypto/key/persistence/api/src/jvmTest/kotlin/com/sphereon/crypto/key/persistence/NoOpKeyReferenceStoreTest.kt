/*
 * (c) 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.key.persistence

import com.sphereon.core.api.model.Origin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class NoOpKeyReferenceStoreTest {
    private val store = NoOpKeyReferenceStore()

    private fun testRecord() =
        KeyReferenceRecord(
            id = "test-id",
            tenantId = "test-tenant",
            alias = "test-alias",
            kid = "test-kid",
            providerId = "test-provider",
            origin = Origin.MANAGED,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

    @Test
    fun isAvailableReturnsFalse() {
        assertFalse(store.isAvailable)
    }

    @Test
    fun saveReturnsOk() =
        runTest {
            val record = testRecord()
            val result = store.save(record)
            assertTrue(result.isOk)
            assertEquals(record, result.value)
        }

    @Test
    fun upsertReturnsOk() =
        runTest {
            val record = testRecord()
            val result = store.upsert(record)
            assertTrue(result.isOk)
            assertEquals(record, result.value)
        }

    @Test
    fun findByIdReturnsNull() =
        runTest {
            val result = store.findById("tenant", "id")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun findByKidReturnsNull() =
        runTest {
            val result = store.findByKid("tenant", "kid")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun findByAliasReturnsNull() =
        runTest {
            val result = store.findByAlias("tenant", "alias")
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun findAllReturnsEmptyList() =
        runTest {
            val result = store.findAll("tenant")
            assertTrue(result.isOk)
            assertTrue(result.value.isEmpty())
        }

    @Test
    fun existsReturnsFalse() =
        runTest {
            val result = store.exists("tenant", "alias", "provider")
            assertTrue(result.isOk)
            assertFalse(result.value)
        }

    @Test
    fun deleteReturnsFalse() =
        runTest {
            val result = store.delete("tenant", "alias", "provider")
            assertTrue(result.isOk)
            assertFalse(result.value)
        }

    @Test
    fun deleteByKidReturnsFalse() =
        runTest {
            val result = store.deleteByKid("tenant", "kid")
            assertTrue(result.isOk)
            assertFalse(result.value)
        }
}

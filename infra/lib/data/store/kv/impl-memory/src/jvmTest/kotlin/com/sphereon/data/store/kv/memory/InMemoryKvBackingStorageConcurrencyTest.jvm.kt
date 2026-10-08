/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.data.store.kv.memory

import com.sphereon.data.store.kv.KvPartitionKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class InMemoryKvBackingStorageConcurrencyTest {
    @Test
    fun `concurrent first access returns one shared partition`() {
        val executor = Executors.newFixedThreadPool(16)
        try {
            repeat(100) {
                val storage = InMemoryKvBackingStorageImpl()
                val key = KvPartitionKey(storeId = "shared")
                val start = CountDownLatch(1)
                val futures =
                    List(16) {
                        executor.submit<InMemoryKvPartition> {
                            start.await()
                            storage.getPartition(key)
                        }
                    }

                start.countDown()
                val partitions = futures.map { it.get(5, TimeUnit.SECONDS) }
                assertTrue(partitions.all { it === partitions.first() })
            }
        } finally {
            executor.shutdownNow()
        }
    }
}

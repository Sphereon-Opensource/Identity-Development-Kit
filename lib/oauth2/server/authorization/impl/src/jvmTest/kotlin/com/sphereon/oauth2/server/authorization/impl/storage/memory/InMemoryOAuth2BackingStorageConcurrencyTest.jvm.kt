/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryOAuth2BackingStorageConcurrencyTest {
    @Test
    fun `concurrent first access returns one shared partition`() {
        val executor = Executors.newFixedThreadPool(16)
        try {
            repeat(100) {
                val storage = InMemoryOAuth2BackingStorageImpl()
                val key = OAuth2StoragePartitionKey.appLevel()
                val start = CountDownLatch(1)
                val futures =
                    List(16) {
                        executor.submit<OAuth2StoragePartition> {
                            start.await()
                            storage.getPartition(key)
                        }
                    }

                start.countDown()
                val partitions = futures.map { it.get(5, TimeUnit.SECONDS) }
                assertTrue(partitions.all { it === partitions.first() })
                assertEquals(1, storage.getPartitionCount())
            }
        } finally {
            executor.shutdownNow()
        }
    }
}

/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.events.DefaultEvent
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.EventFilter
import com.sphereon.core.events.EventStoreConfig
import com.sphereon.core.events.eventFilter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RingBufferEventStoreTest {
    private fun createTestEvent(origin: String = "test.command"): DefaultEvent =
        DefaultEvent(
            id = Uuid.random(),
            type = EventTypes.COMMAND_COMPLETED,
            origin = origin,
            timestamp = Clock.System.now(),
            context = EventContext.EMPTY,
            subsystem = EventSubsystems.SESSION,
            category = EventCategories.LIFECYCLE,
            payload = buildJsonObject { },
            signature = null,
            encryption = null,
        )

    @Test
    fun testStoreAndRetrieve() =
        runTest {
            val store = RingBufferEventStore()
            val event = createTestEvent()

            val storeResult = store.store(event)
            assertTrue(storeResult.isOk)

            val getResult = store.getById(event.id)
            assertTrue(getResult.isOk)
            assertNotNull(getResult.value)
            assertEquals(event.id, getResult.value!!.id)
        }

    @Test
    fun testStoreBatch() =
        runTest {
            val store = RingBufferEventStore()
            val events =
                listOf(
                    createTestEvent(origin = "batch.1"),
                    createTestEvent(origin = "batch.2"),
                    createTestEvent(origin = "batch.3"),
                )

            val result = store.storeBatch(events)
            assertTrue(result.isOk)
            assertEquals(3, result.value.size)

            val sizeResult = store.size()
            assertTrue(sizeResult.isOk)
            assertEquals(3L, sizeResult.value)
        }

    @Test
    fun testQueryWithFilter() =
        runTest {
            val store = RingBufferEventStore()

            // Store events with different categories
            store.store(
                DefaultEvent(
                    id = Uuid.random(),
                    type = EventTypes.COMMAND_COMPLETED,
                    origin = "test",
                    timestamp = Clock.System.now(),
                    context = EventContext.EMPTY,
                    subsystem = EventSubsystems.SESSION,
                    category = EventCategories.ERROR,
                    payload = buildJsonObject { },
                    signature = null,
                    encryption = null,
                ),
            )
            store.store(
                DefaultEvent(
                    id = Uuid.random(),
                    type = EventTypes.COMMAND_COMPLETED,
                    origin = "test",
                    timestamp = Clock.System.now(),
                    context = EventContext.EMPTY,
                    subsystem = EventSubsystems.SESSION,
                    category = EventCategories.LIFECYCLE,
                    payload = buildJsonObject { },
                    signature = null,
                    encryption = null,
                ),
            )
            store.store(
                DefaultEvent(
                    id = Uuid.random(),
                    type = EventTypes.COMMAND_COMPLETED,
                    origin = "test",
                    timestamp = Clock.System.now(),
                    context = EventContext.EMPTY,
                    subsystem = EventSubsystems.SESSION,
                    category = EventCategories.ERROR,
                    payload = buildJsonObject { },
                    signature = null,
                    encryption = null,
                ),
            )

            val filter =
                eventFilter {
                    category(EventCategories.ERROR)
                }

            val result = store.query(filter)
            assertTrue(result.isOk)
            assertEquals(2, result.value.size)
            assertTrue(result.value.all { it.category == EventCategories.ERROR })
        }

    @Test
    fun testQueryWithLimit() =
        runTest {
            val store = RingBufferEventStore()

            repeat(10) {
                store.store(createTestEvent(origin = "event.$it"))
            }

            val result = store.query(EventFilter.ALL, limit = 5)
            assertTrue(result.isOk)
            assertEquals(5, result.value.size)
        }

    @Test
    fun testQueryWithOffset() =
        runTest {
            val store = RingBufferEventStore()

            repeat(10) {
                store.store(createTestEvent(origin = "event.$it"))
            }

            val result = store.query(EventFilter.ALL, limit = 5, offset = 3)
            assertTrue(result.isOk)
            assertEquals(5, result.value.size)
        }

    @Test
    fun testGetRecent() =
        runTest {
            val store = RingBufferEventStore()

            repeat(10) {
                store.store(createTestEvent(origin = "event.$it"))
            }

            val result = store.getRecent(3)
            assertTrue(result.isOk)
            assertEquals(3, result.value.size)
        }

    @Test
    fun testCount() =
        runTest {
            val store = RingBufferEventStore()

            repeat(5) {
                store.store(createTestEvent())
            }

            val countResult = store.count(EventFilter.ALL)
            assertTrue(countResult.isOk)
            assertEquals(5L, countResult.value)
        }

    @Test
    fun testClear() =
        runTest {
            val store = RingBufferEventStore()

            repeat(5) {
                store.store(createTestEvent())
            }

            val clearResult = store.clear()
            assertTrue(clearResult.isOk)
            assertEquals(5L, clearResult.value)

            val sizeResult = store.size()
            assertTrue(sizeResult.isOk)
            assertEquals(0L, sizeResult.value)
        }

    @Test
    fun testCapacityEviction() =
        runTest {
            val config = EventStoreConfig(capacity = 5)
            val store = RingBufferEventStore(config)

            // Store more events than capacity
            repeat(10) {
                store.store(createTestEvent(origin = "event.$it"))
            }

            val sizeResult = store.size()
            assertTrue(sizeResult.isOk)
            assertEquals(5L, sizeResult.value)

            // Verify oldest events were evicted
            val recentResult = store.getRecent(5)
            assertTrue(recentResult.isOk)
            // The last 5 events should remain (events 5-9)
            val origins = recentResult.value.map { it.origin }
            assertTrue(origins.all { it.startsWith("event.") })
        }

    @Test
    fun testGetByIdNotFound() =
        runTest {
            val store = RingBufferEventStore()

            val result = store.getById(Uuid.random())
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun testNoneFilterMatchesNothing() =
        runTest {
            val store = RingBufferEventStore()

            repeat(5) {
                store.store(createTestEvent())
            }

            val result = store.query(EventFilter.NONE)
            assertTrue(result.isOk)
            assertEquals(0, result.value.size)
        }
}

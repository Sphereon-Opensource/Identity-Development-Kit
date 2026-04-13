/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.events.DefaultEvent
import com.sphereon.core.events.EventCategories
import com.sphereon.core.events.EventCategory
import com.sphereon.core.events.EventContext
import com.sphereon.core.events.EventSubsystem
import com.sphereon.core.events.EventSubsystems
import com.sphereon.core.events.EventType
import com.sphereon.core.events.EventTypes
import com.sphereon.core.events.eventFilter
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class EventHubImplTest {

    private fun createTestEvent(
        type: EventType = EventTypes.COMMAND_COMPLETED,
        subsystem: EventSubsystem = EventSubsystems.SESSION,
        category: EventCategory = EventCategories.LIFECYCLE,
        origin: String = "test.command"
    ): DefaultEvent = DefaultEvent(
        id = Uuid.random(),
        type = type,
        origin = origin,
        timestamp = Clock.System.now(),
        context = EventContext.EMPTY,
        subsystem = subsystem,
        category = category,
        payload = buildJsonObject { },
        signature = null,
        encryption = null
    )

    @Test
    fun testPublishAndCollect() = runTest {
        val hub = EventHubImpl()
        val events = mutableListOf<DefaultEvent>()

        val job = launch {
            hub.events.take(3).collect { events.add(it as DefaultEvent) }
        }

        // Give subscriber time to start
        delay(10)

        val event1 = createTestEvent(origin = "test.1")
        val event2 = createTestEvent(origin = "test.2")
        val event3 = createTestEvent(origin = "test.3")

        hub.publish(event1)
        hub.publish(event2)
        hub.publish(event3)

        job.join()

        assertEquals(3, events.size)
        assertEquals("test.1", events[0].origin)
        assertEquals("test.2", events[1].origin)
        assertEquals("test.3", events[2].origin)
    }

    @Test
    fun testSubscribeWithFilter() = runTest {
        val hub = EventHubImpl()
        val filter = eventFilter {
            category(EventCategories.ERROR)
        }

        val collectedEvents = mutableListOf<DefaultEvent>()

        val job = hub.subscribe(this, filter) { event ->
            collectedEvents.add(event as DefaultEvent)
        }

        delay(10)

        // Publish mix of events
        hub.publish(createTestEvent(category = EventCategories.LIFECYCLE))
        hub.publish(createTestEvent(category = EventCategories.ERROR))
        hub.publish(createTestEvent(category = EventCategories.OPERATION))
        hub.publish(createTestEvent(category = EventCategories.ERROR))

        delay(50)
        job.cancelAndJoin()

        // Should only have collected ERROR events
        assertEquals(2, collectedEvents.size)
        assertTrue(collectedEvents.all { it.category == EventCategories.ERROR })
    }

    @Test
    fun testSubscribeWithDslBuilder() = runTest {
        val hub = EventHubImpl()
        val collectedEvents = mutableListOf<DefaultEvent>()

        val job = hub.subscribe(scope = this, builder = {
            filter {
                subsystem(EventSubsystems.CRYPTO)
            }
            onEvent { event ->
                collectedEvents.add(event as DefaultEvent)
            }
        })

        delay(10)

        hub.publish(createTestEvent(subsystem = EventSubsystems.CRYPTO))
        hub.publish(createTestEvent(subsystem = EventSubsystems.SESSION))
        hub.publish(createTestEvent(subsystem = EventSubsystems.CRYPTO))

        delay(50)
        job.cancelAndJoin()

        assertEquals(2, collectedEvents.size)
        assertTrue(collectedEvents.all { it.subsystem == EventSubsystems.CRYPTO })
    }

    @Test
    fun testFilteredEventsFlow() = runTest {
        val hub = EventHubImpl()
        val filter = eventFilter {
            type(EventTypes.COMMAND_FAILED)
        }

        val job = launch {
            val events = hub.filteredEvents(filter).take(2).toList()
            assertEquals(2, events.size)
            assertTrue(events.all { it.type == EventTypes.COMMAND_FAILED })
        }

        delay(10)

        hub.publish(createTestEvent(type = EventTypes.COMMAND_COMPLETED))
        hub.publish(createTestEvent(type = EventTypes.COMMAND_FAILED))
        hub.publish(createTestEvent(type = EventTypes.COMMAND_STARTED))
        hub.publish(createTestEvent(type = EventTypes.COMMAND_FAILED))

        job.join()
    }

    @Test
    fun testEventsByTypePattern() = runTest {
        val hub = EventHubImpl()

        val job = launch {
            val events = hub.eventsByTypePattern("command.*").take(3).toList()
            assertEquals(3, events.size)
        }

        delay(10)

        hub.publish(createTestEvent(type = EventTypes.COMMAND_STARTED))
        hub.publish(createTestEvent(type = EventTypes.SESSION_CREATED))
        hub.publish(createTestEvent(type = EventTypes.COMMAND_COMPLETED))
        hub.publish(createTestEvent(type = EventTypes.COMMAND_FAILED))

        job.join()
    }

    @Test
    fun testEventsBySubsystem() = runTest {
        val hub = EventHubImpl()

        val job = launch {
            val events = hub.eventsBySubsystem(EventSubsystems.KMS).take(2).toList()
            assertEquals(2, events.size)
            assertTrue(events.all { it.subsystem == EventSubsystems.KMS })
        }

        delay(10)

        hub.publish(createTestEvent(subsystem = EventSubsystems.CRYPTO))
        hub.publish(createTestEvent(subsystem = EventSubsystems.KMS))
        hub.publish(createTestEvent(subsystem = EventSubsystems.SESSION))
        hub.publish(createTestEvent(subsystem = EventSubsystems.KMS))

        job.join()
    }

    @Test
    fun testEventsByCategory() = runTest {
        val hub = EventHubImpl()

        val job = launch {
            val events = hub.eventsByCategory(EventCategories.SECURITY).take(2).toList()
            assertEquals(2, events.size)
            assertTrue(events.all { it.category == EventCategories.SECURITY })
        }

        delay(10)

        hub.publish(createTestEvent(category = EventCategories.LIFECYCLE))
        hub.publish(createTestEvent(category = EventCategories.SECURITY))
        hub.publish(createTestEvent(category = EventCategories.OPERATION))
        hub.publish(createTestEvent(category = EventCategories.SECURITY))

        job.join()
    }
}

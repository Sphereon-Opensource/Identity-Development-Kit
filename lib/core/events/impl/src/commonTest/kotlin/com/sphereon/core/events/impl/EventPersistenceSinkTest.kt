/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventPersistenceSink
import com.sphereon.core.events.EventStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventPersistenceSinkTest {
    @Test
    fun durableSinkCompletesBeforeBroadcast() = runTest {
        val hub = EventHubImpl()
        var persisted = false
        val sink = EventPersistenceSink {
            assertTrue(hub.events.replayCache.isEmpty())
            persisted = true
        }
        val service = AppEventServiceImpl(
            hub,
            RingBufferEventStore(),
            NoOpEventSigningService(),
            NoOpEventEncryptionService(),
            setOf(sink),
        )
        val event = service.eventBuilder()
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("sink-test")
            .payload(buildJsonObject { })
            .build()

        service.emit(event)

        assertTrue(persisted)
        assertEquals(listOf(event.id), hub.events.replayCache.map { it.id })
    }

    @Test
    fun sinkFailurePreventsBroadcastAndPropagates() = runTest {
        val hub = EventHubImpl()
        val service = AppEventServiceImpl(
            hub,
            RingBufferEventStore(),
            NoOpEventSigningService(),
            NoOpEventEncryptionService(),
            setOf(EventPersistenceSink { error("durable history unavailable") }),
        )
        val event = service.eventBuilder()
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("sink-test")
            .payload(buildJsonObject { })
            .build()

        assertFailsWith<IllegalStateException> { service.emit(event) }
        assertTrue(hub.events.replayCache.isEmpty())
    }

    @Test
    fun eventStoreFailurePreventsSinksAndBroadcastAndPropagates() = runTest {
        val hub = EventHubImpl()
        var sinkCalled = false
        val service = AppEventServiceImpl(
            hub,
            FailingEventStore(),
            NoOpEventSigningService(),
            NoOpEventEncryptionService(),
            setOf(EventPersistenceSink { sinkCalled = true }),
        )
        val event = service.eventBuilder()
            .type(EventTypes.COMMAND_COMPLETED)
            .origin("store-test")
            .payload(buildJsonObject { })
            .build()

        assertFailsWith<Throwable> { service.emit(event) }
        assertTrue(!sinkCalled)
        assertTrue(hub.events.replayCache.isEmpty())
    }
}

private class FailingEventStore(
    delegate: EventStore = RingBufferEventStore(),
) : EventStore by delegate {
    override suspend fun store(event: Event): IdkResult<Event, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "event store unavailable"))
}

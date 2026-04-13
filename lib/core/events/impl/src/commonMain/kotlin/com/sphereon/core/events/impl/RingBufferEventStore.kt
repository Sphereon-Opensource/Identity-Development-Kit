/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventFilter
import com.sphereon.core.events.EventStore
import com.sphereon.core.events.EventStoreConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory ring buffer implementation of EventStore.
 *
 * This is the default IDK event store implementation:
 * - Fixed capacity with automatic eviction of oldest events
 * - Thread-safe using Mutex
 * - Efficient for recent event queries
 * - No persistence across restarts
 *
 * For persistent storage, use EDK's database-backed implementations.
 */
@OptIn(ExperimentalUuidApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<EventStore>())
class RingBufferEventStore(
    private val config: EventStoreConfig = EventStoreConfig()
) : EventStore {

    private val buffer = ArrayDeque<Event>(config.capacity)
    private val mutex = Mutex()

    override suspend fun store(event: Event): IdkResult<Event, IdkError> = mutex.withLock {
        // Evict oldest if at capacity
        if (buffer.size >= config.capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(event)
        Ok(event)
    }

    override suspend fun storeBatch(events: List<Event>): IdkResult<List<Event>, IdkError> = mutex.withLock {
        events.forEach { event ->
            if (buffer.size >= config.capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(event)
        }
        Ok(events)
    }

    override suspend fun query(
        filter: EventFilter,
        limit: Int,
        offset: Int
    ): IdkResult<List<Event>, IdkError> = mutex.withLock {
        val filtered = buffer
            .filter { filter.matches(it) }
            .sortedByDescending { it.timestamp }
            .drop(offset)
            .take(limit)
        Ok(filtered)
    }

    override suspend fun getById(id: Uuid): IdkResult<Event?, IdkError> = mutex.withLock {
        Ok(buffer.find { it.id == id })
    }

    override suspend fun count(filter: EventFilter): IdkResult<Long, IdkError> = mutex.withLock {
        Ok(buffer.count { filter.matches(it) }.toLong())
    }

    override suspend fun getRecent(count: Int): IdkResult<List<Event>, IdkError> = mutex.withLock {
        Ok(buffer.takeLast(count).reversed())
    }

    override suspend fun deleteOlderThan(olderThanMillis: Long): IdkResult<Long, IdkError> = mutex.withLock {
        val cutoff = Clock.System.now().toEpochMilliseconds() - olderThanMillis
        val initialSize = buffer.size
        buffer.removeAll { it.timestamp.toEpochMilliseconds() < cutoff }
        Ok((initialSize - buffer.size).toLong())
    }

    override suspend fun clear(): IdkResult<Long, IdkError> = mutex.withLock {
        val count = buffer.size.toLong()
        buffer.clear()
        Ok(count)
    }

    override suspend fun size(): IdkResult<Long, IdkError> = mutex.withLock {
        Ok(buffer.size.toLong())
    }
}

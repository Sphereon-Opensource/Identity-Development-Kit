/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Storage interface for persisting and querying events.
 *
 * EventStore is an AppScope singleton that provides persistent
 * storage for events. IDK provides an in-memory ring buffer
 * implementation; EDK provides database-backed implementations
 * (PostgreSQL, MySQL, SQLite).
 *
 * ## IDK Implementation
 *
 * IDK's [RingBufferEventStore] provides:
 * - In-memory storage with configurable capacity
 * - Automatic eviction of oldest events when capacity is reached
 * - Fast queries for recent events
 *
 * ## EDK Implementations
 *
 * EDK extends with database-backed stores:
 * - PostgreSQL, MySQL, SQLite implementations
 * - Retention policies with automatic cleanup
 * - Tenant-aware queries
 * - Full-text search on payloads
 *
 * ## Usage Examples
 *
 * ```kotlin
 * // Store an event
 * eventStore.store(event)
 *
 * // Query with filter
 * val filter = eventFilter {
 *     types(EventTypes.COMMAND_FAILED)
 *     categories(EventCategories.ERROR)
 * }
 * val errors = eventStore.query(filter).getOrThrow()
 *
 * // Get by ID
 * val event = eventStore.getById(eventId).getOrThrow()
 * ```
 *
 * @see EventHub for real-time event streaming
 */
@OptIn(ExperimentalUuidApi::class)
@JsExportCompat
interface EventStore {
    /**
     * Store an event.
     *
     * @param event The event to store
     * @return The stored event (may include store-assigned metadata)
     */
    suspend fun store(event: Event): IdkResult<Event, IdkError>

    /**
     * Store multiple events in a batch.
     *
     * @param events The events to store
     * @return The stored events
     */
    suspend fun storeBatch(events: List<Event>): IdkResult<List<Event>, IdkError>

    /**
     * Query events matching a filter.
     *
     * Results are ordered by timestamp descending (newest first)
     * unless otherwise specified.
     *
     * @param filter Filter criteria
     * @param limit Maximum number of events to return (default: 100)
     * @param offset Number of events to skip (for pagination)
     * @return List of matching events
     */
    suspend fun query(
        filter: EventFilter,
        limit: Int = 100,
        offset: Int = 0,
    ): IdkResult<List<Event>, IdkError>

    /**
     * Get an event by its ID.
     *
     * @param id Event ID
     * @return The event if found, null otherwise
     */
    suspend fun getById(id: Uuid): IdkResult<Event?, IdkError>

    /**
     * Count events matching a filter.
     *
     * Useful for pagination and statistics.
     *
     * @param filter Filter criteria
     * @return Number of matching events
     */
    suspend fun count(filter: EventFilter): IdkResult<Long, IdkError>

    /**
     * Get recent events.
     *
     * Convenience method for getting the N most recent events.
     *
     * @param count Maximum number of events to return
     * @return List of recent events, newest first
     */
    suspend fun getRecent(count: Int = 10): IdkResult<List<Event>, IdkError>

    /**
     * Delete events older than a specified age.
     *
     * Used for retention policy enforcement.
     *
     * @param olderThanMillis Events older than this (in milliseconds) will be deleted
     * @return Number of events deleted
     */
    suspend fun deleteOlderThan(olderThanMillis: Long): IdkResult<Long, IdkError>

    /**
     * Clear all events from the store.
     *
     * Use with caution! This removes all stored events.
     *
     * @return Number of events deleted
     */
    suspend fun clear(): IdkResult<Long, IdkError>

    /**
     * Get the current number of stored events.
     */
    suspend fun size(): IdkResult<Long, IdkError>

    /**
     * DI graph interface for AppScope contribution.
     */
    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    @JsExportIgnoreCompat
    interface Graph {
        val eventStore: EventStore
    }
}

/**
 * Configuration for event store behavior.
 */
@JsExportCompat
data class EventStoreConfig
    @JvmOverloads
    constructor(
        /**
         * Maximum number of events to store (for in-memory stores).
         * Set to 0 for unlimited (database stores).
         */
        val capacity: Int = 10000,
        /**
         * Whether to enable retention policy.
         */
        val retentionEnabled: Boolean = false,
        /**
         * Retention period in milliseconds (default: 7 days).
         * Events older than this will be deleted.
         */
        val retentionPeriodMillis: Long = 7 * 24 * 60 * 60 * 1000L,
        /**
         * How often to run retention cleanup in milliseconds (default: 1 hour).
         */
        val retentionCheckIntervalMillis: Long = 60 * 60 * 1000L,
    )

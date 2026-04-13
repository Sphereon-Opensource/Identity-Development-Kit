/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * Central hub for event broadcasting and subscription.
 *
 * EventHub is an AppScope singleton that serves as the central point
 * for event distribution across all scope levels. All EventService
 * implementations (App, User, Session) publish events through the
 * same EventHub instance.
 *
 * ## Key Features
 *
 * - **SharedFlow Broadcasting**: Hot flow with configurable replay buffer
 * - **Filtered Subscriptions**: Subscribe with EventFilter for selective events
 * - **Scope-Agnostic**: All events from all scopes flow through this hub
 * - **Flow-Based API**: Native Kotlin coroutines support
 *
 * ## Usage Examples
 *
 * ```kotlin
 * // Collect all events
 * eventHub.events.collect { event ->
 *     when (event.type) {
 *         EventTypes.COMMAND_COMPLETED -> log.info("Command completed: ${event.origin}")
 *         EventTypes.COMMAND_FAILED -> log.error("Command failed: ${event.origin}")
 *     }
 * }
 *
 * // Subscribe with filter
 * val job = eventHub.subscribe(scope) {
 *     filter {
 *         subsystems(EventSubsystems.CRYPTO, EventSubsystems.KMS)
 *         categories(EventCategories.ERROR)
 *     }
 *     onEvent { event ->
 *         alertService.notify("Error in ${event.subsystem.value}: ${event.payload}")
 *     }
 * }
 *
 * // Cancel subscription
 * job.cancel()
 * ```
 *
 * @see EventService for event emission
 * @see EventFilter for filtering options
 */
interface EventHub {

    /**
     * Hot SharedFlow of all events published to this hub.
     *
     * This flow has configurable replay (default: 0) and buffer capacity
     * (default: 1000). Subscribers receive events as they are published.
     * Late subscribers do not receive past events unless replay > 0.
     */
    val events: SharedFlow<Event>

    /**
     * Publish an event to all subscribers.
     *
     * This is typically called by EventService implementations,
     * not directly by application code.
     *
     * @param event The event to publish
     */
    suspend fun publish(event: Event)

    /**
     * Subscribe to events with an optional filter.
     *
     * The subscription runs in the provided CoroutineScope and
     * returns a Job that can be used to cancel the subscription.
     *
     * @param scope CoroutineScope for the subscription lifecycle
     * @param filter Optional filter to select specific events (null = all events)
     * @param handler Suspend function called for each matching event
     * @return Job that can be cancelled to stop the subscription
     */
    fun subscribe(
        scope: CoroutineScope,
        filter: EventFilter? = null,
        handler: suspend (Event) -> Unit
    ): Job

    /**
     * Subscribe to events using a DSL builder.
     *
     * @param scope CoroutineScope for the subscription lifecycle
     * @param builder DSL builder for configuring filter and handler
     * @return Job that can be cancelled to stop the subscription
     */
    fun subscribe(
        scope: CoroutineScope,
        builder: EventSubscriptionBuilder.() -> Unit
    ): Job

    /**
     * Create a filtered flow of events.
     *
     * This returns a Flow that can be collected directly,
     * useful for integration with other Flow operators.
     *
     * @param filter Filter to apply to events
     * @return Flow of filtered events
     */
    fun filteredEvents(filter: EventFilter): Flow<Event>

    /**
     * Get events matching a type pattern.
     *
     * @param pattern Glob-style pattern (e.g., "command.*")
     * @return Flow of events matching the pattern
     */
    fun eventsByTypePattern(pattern: String): Flow<Event>

    /**
     * Get events for specific subsystems.
     *
     * @param subsystems Subsystems to filter by
     * @return Flow of events from specified subsystems
     */
    fun eventsBySubsystem(subsystems: Set<EventSubsystem>): Flow<Event>

    /**
     * Get events for a single subsystem.
     *
     * @param subsystem Subsystem to filter by
     * @return Flow of events from the specified subsystem
     */
    fun eventsBySubsystem(subsystem: EventSubsystem): Flow<Event> =
        eventsBySubsystem(setOf(subsystem))

    /**
     * Get events for specific categories.
     *
     * @param categories Categories to filter by
     * @return Flow of events in specified categories
     */
    fun eventsByCategory(categories: Set<EventCategory>): Flow<Event>

    /**
     * Get events for a single category.
     *
     * @param category Category to filter by
     * @return Flow of events in the specified category
     */
    fun eventsByCategory(category: EventCategory): Flow<Event> =
        eventsByCategory(setOf(category))

    /**
     * DI component interface for AppScope contribution.
     */
    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    interface Component {
        val eventHub: EventHub
    }
}

/**
 * Builder interface for subscription DSL.
 */
interface EventSubscriptionBuilder {
    /**
     * Configure the event filter.
     */
    fun filter(configure: EventFilterBuilder.() -> Unit)

    /**
     * Set the event handler.
     */
    fun onEvent(handler: suspend (Event) -> Unit)
}

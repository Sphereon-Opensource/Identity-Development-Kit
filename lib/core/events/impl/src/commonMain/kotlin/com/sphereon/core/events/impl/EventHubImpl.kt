/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.events.Event
import com.sphereon.core.events.EventCategory
import com.sphereon.core.events.EventFilter
import com.sphereon.core.events.EventFilterBuilder
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.EventSubscriptionBuilder
import com.sphereon.core.events.EventSubsystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default implementation of EventHub.
 *
 * Uses Kotlin SharedFlow for event broadcasting with:
 * - No replay (late subscribers don't receive past events)
 * - Buffer capacity of 1000 events
 * - DROP_OLDEST overflow policy (prevents backpressure issues)
 *
 * This is an AppScope singleton shared across all scope levels.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<EventHub>())
class EventHubImpl : EventHub {

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val events: SharedFlow<Event> = _events.asSharedFlow()

    override suspend fun publish(event: Event) {
        _events.emit(event)
    }

    override fun subscribe(
        scope: CoroutineScope,
        filter: EventFilter?,
        handler: suspend (Event) -> Unit
    ): Job = scope.launch {
        val flow = if (filter != null) {
            events.filter { filter.matches(it) }
        } else {
            events
        }
        flow.collect { event ->
            handler(event)
        }
    }

    override fun subscribe(
        scope: CoroutineScope,
        builder: EventSubscriptionBuilder.() -> Unit
    ): Job {
        val subscriptionBuilder = DefaultEventSubscriptionBuilder()
        builder(subscriptionBuilder)
        return subscribe(scope, subscriptionBuilder.filter, subscriptionBuilder.handler)
    }

    override fun filteredEvents(filter: EventFilter): Flow<Event> =
        events.filter { filter.matches(it) }

    override fun eventsByTypePattern(pattern: String): Flow<Event> =
        events.filter { it.type.matches(pattern) }

    override fun eventsBySubsystem(subsystems: Set<EventSubsystem>): Flow<Event> =
        events.filter { it.subsystem in subsystems }

    override fun eventsByCategory(categories: Set<EventCategory>): Flow<Event> =
        events.filter { it.category in categories }
}

/**
 * Default implementation of EventSubscriptionBuilder.
 */
private class DefaultEventSubscriptionBuilder : EventSubscriptionBuilder {
    var filter: EventFilter? = null
        private set

    var handler: suspend (Event) -> Unit = {}
        private set

    override fun filter(configure: EventFilterBuilder.() -> Unit) {
        val builder = EventFilterBuilder()
        configure(builder)
        filter = builder.build()
    }

    override fun onEvent(handler: suspend (Event) -> Unit) {
        this.handler = handler
    }
}

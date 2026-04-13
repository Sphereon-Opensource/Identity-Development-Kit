/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events.impl

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventCategory
import com.sphereon.core.api.events.EventSubsystem
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventType
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.tracing.TraceContext
import com.sphereon.core.events.DEFAULT_EVENT_VERSION
import com.sphereon.core.events.DefaultEvent
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventBuilder
import com.sphereon.core.events.EventContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Default implementation of EventBuilder.
 *
 * Creates DefaultEvent instances with the provided configuration.
 * The builder validates required fields and provides sensible defaults.
 */
@OptIn(ExperimentalUuidApi::class)
class DefaultEventBuilder(
    private val scope: IdkScope,
    private val defaultContext: EventContext = EventContext.EMPTY,
) : EventBuilder {
    private var type: EventType? = null
    private var origin: String? = null
    private var context: EventContext = defaultContext
    private var subsystem: EventSubsystem = EventSubsystems.CUSTOM
    private var category: EventCategory = EventCategories.OPERATION
    private var payload: JsonObject = buildJsonObject { }
    private var correlationId: String? = null
    private var timestamp: Instant = Clock.System.now()
    private var version: String = DEFAULT_EVENT_VERSION
    private var traceContext: TraceContext? = null

    override fun type(type: EventType): EventBuilder =
        apply {
            this.type = type
        }

    override fun origin(origin: String): EventBuilder =
        apply {
            this.origin = origin
        }

    override fun context(context: EventContext): EventBuilder =
        apply {
            this.context = context
        }

    override fun subsystem(subsystem: EventSubsystem): EventBuilder =
        apply {
            this.subsystem = subsystem
        }

    override fun category(category: EventCategory): EventBuilder =
        apply {
            this.category = category
        }

    override fun payload(payload: JsonObject): EventBuilder =
        apply {
            this.payload = payload
        }

    override fun correlationId(correlationId: String): EventBuilder =
        apply {
            this.correlationId = correlationId
        }

    override fun version(version: String): EventBuilder =
        apply {
            this.version = version
        }

    override fun trace(traceContext: TraceContext?): EventBuilder =
        apply {
            this.traceContext = traceContext
        }

    /**
     * Set the event timestamp.
     * Defaults to Clock.System.now() if not specified.
     */
    fun timestamp(timestamp: Instant): DefaultEventBuilder =
        apply {
            this.timestamp = timestamp
        }

    override fun build(): Event {
        val eventType = checkNotNull(type) { "Event type is required" }
        val eventOrigin = checkNotNull(origin) { "Event origin is required" }

        // Merge correlationId into context if specified
        val mergedCorrelation =
            if (correlationId != null && context.correlationId == null) {
                context.copy(correlationId = correlationId)
            } else {
                context
            }
        val finalContext = mergedCorrelation.withTraceContext(traceContext)

        return DefaultEvent(
            id = Uuid.random(),
            type = eventType,
            origin = eventOrigin,
            timestamp = timestamp,
            context = finalContext,
            subsystem = subsystem,
            category = category,
            payload = payload,
            signature = null,
            encryption = null,
            version = version,
        )
    }

    companion object {
        /**
         * Create a builder for app-scope events.
         */
        fun forAppScope(): DefaultEventBuilder = DefaultEventBuilder(IdkScope.APP, EventContext.EMPTY)

        /**
         * Create a builder for user-scope events.
         */
        fun forUserScope(context: EventContext): DefaultEventBuilder = DefaultEventBuilder(IdkScope.USER, context)

        /**
         * Create a builder for session-scope events.
         */
        fun forSessionScope(context: EventContext): DefaultEventBuilder = DefaultEventBuilder(IdkScope.SESSION, context)
    }
}

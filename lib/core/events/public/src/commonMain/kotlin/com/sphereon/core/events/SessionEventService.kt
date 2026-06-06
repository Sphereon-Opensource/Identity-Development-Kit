/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import dev.zacsweers.metro.SingleIn

/**
 * Session-scoped event service marker interface.
 *
 * Use this for emitting events at the session scope level,
 * where full context (session, tenant, principal) is available.
 *
 * Events emitted from SessionEventService will have complete [EventContext]
 * populated from the [SessionContext].
 *
 * This is the most common event service to use in command implementations.
 *
 * Injection:
 * ```kotlin
 * @Inject
 * class MyCommand(
 *     execution: SessionExecution,
 *     private val eventService: SessionEventService
 * ) : ExecutionScopedCommandAdapter<...>(...) {
 *
 *     override suspend fun doExecute(...) {
 *         // Emit custom event
 *         eventService.emit(
 *             eventService.eventBuilder()
 *                 .type(EventTypes.custom("my.operation.completed"))
 *                 .subsystem(EventSubsystems.CUSTOM)
 *                 .category(EventCategories.OPERATION)
 *                 .payload(buildJsonObject { put("result", "success") })
 *                 .build()
 *         )
 *     }
 * }
 * ```
 */
@JsExportCompat
interface SessionEventService : EventService {
    override val scope: IdkScope get() = IdkScope.SESSION

    /**
     * Parent user-scoped event service.
     */
    val parent: UserEventService

    /**
     * The session context this service is scoped to.
     */
    val sessionContext: SessionContext

    /**
     * Graph interface for DI contribution.
     */
    @SingleIn(SessionScope::class)
    @ContributesTo(SessionScope::class)
    @JsExportIgnoreCompat
    interface Graph {
        val sessionEventService: SessionEventService
    }
}

/**
 * Exposes [SessionEventService] as an optional graph accessor so consumers declaring
 * `SessionEventService? = null` constructor parameters resolve cleanly under the Metro
 * `nullable type key`. The real [com.sphereon.core.events.impl.SessionEventServiceImpl] adds a
 * second `@ContributesBinding(SessionScope::class, binding = binding<SessionEventService?>())`
 * so this default `null` body is overridden whenever the events-impl module is on the classpath.
 *
 * The accessor name is distinct from [SessionEventService.Graph.sessionEventService] so the merged
 * Metro graph can implement both interfaces without a Kotlin property-name collision (the merged
 * class would otherwise declare two `sessionEventService` properties of different types).
 */
@ContributesTo(SessionScope::class)
interface SessionEventServiceOptionalProvider {
    @OptionalBinding
    val optionalSessionEventService: SessionEventService? get() = null
}

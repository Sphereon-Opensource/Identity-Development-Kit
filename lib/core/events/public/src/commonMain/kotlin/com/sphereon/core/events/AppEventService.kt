/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.context.IdkScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * App-scoped event service marker interface.
 *
 * Use this for emitting events at the application scope level,
 * where no user/session context is available.
 *
 * Events emitted from AppEventService will have empty [EventContext].
 *
 * Injection:
 * ```kotlin
 * @Inject
 * class MyAppService(private val eventService: AppEventService) {
 *     suspend fun doSomething() {
 *         eventService.emit(
 *             eventService.eventBuilder()
 *                 .type(EventTypes.custom("app.started"))
 *                 .subsystem(EventSubsystems.SESSION)
 *                 .category(EventCategories.LIFECYCLE)
 *                 .build()
 *         )
 *     }
 * }
 * ```
 */
interface AppEventService : EventService {
    override val scope: IdkScope get() = IdkScope.APP

    /**
     * Graph interface for DI contribution.
     */
    @SingleIn(AppScope::class)
    @ContributesTo(AppScope::class)
    interface Graph {
        val appEventService: AppEventService
    }
}

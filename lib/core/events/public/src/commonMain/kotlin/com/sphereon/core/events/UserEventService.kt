/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.context.IdkScope
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * User-scoped event service marker interface.
 *
 * Use this for emitting events at the user context scope level,
 * where tenant and principal are known but no session is active.
 *
 * Events emitted from UserEventService will have [EventContext] with
 * tenantId and principalId populated from the [UserContext].
 *
 * Injection:
 * ```kotlin
 * @Inject
 * class MyUserService(private val eventService: UserEventService) {
 *     suspend fun doSomething() {
 *         eventService.emit(
 *             eventService.eventBuilder()
 *                 .type(EventTypes.USER_CONTEXT_CREATED)
 *                 .subsystem(EventSubsystems.SESSION)
 *                 .category(EventCategories.LIFECYCLE)
 *                 .build()
 *         )
 *     }
 * }
 * ```
 */
interface UserEventService : EventService {
    override val scope: IdkScope get() = IdkScope.USER

    /**
     * Parent app-scoped event service.
     */
    val parent: AppEventService

    /**
     * The user context this service is scoped to.
     */
    val userContext: UserContext

    /**
     * Graph interface for DI contribution.
     */
    @SingleIn(UserScope::class)
    @ContributesTo(UserScope::class)
    interface Graph {
        val userEventService: UserEventService
    }
}

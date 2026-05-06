/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.requiredaction

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionsExecutionStrategy
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Declares the [RequiredActionsExecutionStrategy] multibinding. Allow-empty so the
 * IDK compiles in the (unusual) absence of any strategy contributions; with the two
 * defaults below on the classpath the set is non-empty in practice.
 *
 * SessionScope mirrors the strategies themselves: they read tenant config and
 * delegate to session-scoped IDV commands.
 */
@ContributesTo(SessionScope::class)
interface RequiredActionsStrategyMultibindings {
    @Multibinds(allowEmpty = true)
    fun requiredActionsExecutionStrategies(): Set<RequiredActionsExecutionStrategy>
}

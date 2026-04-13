/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.di.context

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Manages singleton anonymous and background user context components.
 *
 * This interface ensures that:
 * - Anonymous user/tenant graph is always a singleton (cannot have more than one instance)
 * - Background user/tenant graph is always a singleton (cannot have more than one instance)
 * - Anonymous components are kept separate from regular user components
 * - Background components are kept separate from anonymous and regular user components
 * - Internal state is not exposed to the outside world
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AnonymousUserGraphManager", exact = true)
interface AnonymousUserGraphManager {
    /**
     * Gets the singleton anonymous user context graph.
     * Creates it on first access. The anonymous context is always a singleton with the ID UserContext.ANONYMOUS.
     * This method always returns a valid graph.
     */
    fun getAnonymousGraph(): UserContextGraph

    /**
     * Gets the singleton background service user context graph.
     * Creates it on first access. The background context is always a singleton with the ID UserContext.BACKGROUND_SERVICE.
     * Background sessions use the same semantics as anonymous sessions.
     * This method always returns a valid graph.
     */
    fun getBackgroundGraph(): UserContextGraph

    /**
     * Clears the anonymous context. The context will be recreated on next access.
     */
    fun clearAnonymous()

    /**
     * Clears the background context. The context will be recreated on next access.
     */
    fun clearBackground()

    /**
     * Clears all managed contexts. They will be recreated on next access.
     */
    fun clearAll()

    @ContributesTo(AppScope::class)
    interface Graph {
        val anonymousUserGraphManager: AnonymousUserGraphManager
    }
}

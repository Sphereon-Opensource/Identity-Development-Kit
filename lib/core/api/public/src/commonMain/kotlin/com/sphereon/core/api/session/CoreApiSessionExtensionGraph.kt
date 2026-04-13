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

package com.sphereon.core.api.session

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionGraph
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName

/**
 * Access the [CoreApiSessionExtensionGraph] contribution from a [SessionInstance].
 *
 * Safe to call because the session-scope graph always merges this contributed interface.
 * Note: [SessionGraph] is a [@GraphExtension][dev.zacsweers.metro.GraphExtension],
 * so [asContribution][dev.zacsweers.metro.asContribution] cannot be used here.
 */
fun SessionInstance.asCoreApiServiceGraph(): CoreApiSessionExtensionGraph = this.graph as CoreApiSessionExtensionGraph

/**
 * Access the [CoreApiSessionExtensionGraph] contribution from a [SessionGraph].
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun SessionGraph.asCoreApiServiceGraph(): CoreApiSessionExtensionGraph = this as CoreApiSessionExtensionGraph

/**
 * Extension graph contributed to [SessionScope] providing core API session services.
 *
 * Automatically merged into the session-scope graph by Metro.
 * Access via the convenience extension `sessionInstance.asCoreApiServiceGraph()`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoreApiSessionExtensionGraph", exact = true)
@SingleIn(SessionScope::class)
@ContributesTo(SessionScope::class)
interface CoreApiSessionExtensionGraph {
    val serviceExecution: SessionExecution
    val sessionContext: SessionContext

    val logManager: SessionLogManager

    fun logger() = loggerWithTag()

    fun loggerWithTag(tag: String = sessionContext.sessionId) = logManager.withTagAsync(tag)
}

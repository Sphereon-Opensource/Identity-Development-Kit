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

package com.sphereon.core.api.context

import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.core.api.session.CommandInvoker
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextGraph
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Access the [CoreApiContextExtensionGraph] contribution from a [UserContextInstance].
 *
 * Safe to call because the user-scope graph always merges this contributed interface.
 * Note: [UserContextGraph] is a [@GraphExtension][dev.zacsweers.metro.GraphExtension],
 * so [asContribution][dev.zacsweers.metro.asContribution] cannot be used here.
 */
fun UserContextInstance.asCoreApiContextGraph(): CoreApiContextExtensionGraph = this.graph as CoreApiContextExtensionGraph

/**
 * Access the [CoreApiContextExtensionGraph] contribution from a [UserContextGraph].
 */
fun UserContextGraph.asCoreApiContextGraph(): CoreApiContextExtensionGraph = this as CoreApiContextExtensionGraph

/**
 * Extension graph contributed to [UserScope] providing core API context services.
 *
 * Automatically merged into the user-scope graph by Metro.
 * Access via the convenience extension `userContextInstance.asCoreApiContextGraph()`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoreApiContextExtensionGraph", exact = true)
@SingleIn(UserScope::class)
@ContributesTo(UserScope::class)
interface CoreApiContextExtensionGraph {
    val sessionContextManager: SessionContextManager
    val logManager: UserContextLogManager
    val conf: ContextConfig
    val commandInvoker: CommandInvoker

    fun loggerWithTag(tag: String = "sphereon") = logManager.withTagAsync(tag)

    fun createExecutionContextGraph(
        context: UserContext,
        sessionId: String,
        correlationId: String = sessionId,
    ): SessionInstance = sessionContextManager.createOrGetFromId(
        sessionId = sessionId,
        correlationId = correlationId,
        principalType = context.principalType,
    )
}

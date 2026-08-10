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

package com.sphereon.core.api.app

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.context.CoreApiContextExtensionGraph
import com.sphereon.core.api.context.asCoreApiContextGraph
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.core.api.session.AppCommandInvoker
import com.sphereon.di.app.AppGraph
import com.sphereon.di.context.IdentityResolutionPipeline
import com.sphereon.di.context.UserContextManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.asContribution
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Extension graph contributed to [AppScope] providing core API application services.
 *
 * Automatically merged into the app-scope graph by Metro.
 * Access via `appGraph.asContribution<CoreApiAppExtensionGraph>()`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoreApiAppExtensionGraph", exact = true)
@SingleIn(AppScope::class)
@ContributesTo(AppScope::class)
interface CoreApiAppExtensionGraph {
    /**
     * Provides access to the [UserContextManager] from the app graph.
     * Declared here so contributed code can access it without casting to [AppGraph].
     */
    val userContextManager: UserContextManager

    /**
     * Resolves validated identity claims into tenant, principal, and principal type.
     *
     * Request adapters must use this shared pipeline rather than duplicating token
     * classification heuristics when constructing a typed user context.
     */
    val identityResolutionPipeline: IdentityResolutionPipeline

    /**
     * Used to provide an anonymous context scope. Mainly useful for libraries and global logging.
     * Always use context and/or session scopes instead of this graph when available.
     */
    val anonymousContextGraph: Lazy<CoreApiContextExtensionGraph>
        get() = lazy { userContextManager.getAnonymous().asCoreApiContextGraph() }

    // Since all our loggers require the context scope, we use the above anonymous scope and fetch the context logger from there
    val appContextLogManager: UserContextLogManager
        get() = anonymousContextGraph.value.logManager

    val appLogManager: AppLogManager
    val appConfig: AppConfigService
    val commandInvoker: AppCommandInvoker

    fun appLoggerWithTag(tag: String = "sphereon") = appContextLogManager.withTagAsync(tag)

    fun appLogger() = appLoggerWithTag()
}

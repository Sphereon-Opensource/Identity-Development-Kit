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

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.core.api.session.EmptyInterceptorChain
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionExecution", exact = true)
interface SessionExecution : HasProvenance {
    val sessionContextManager: SessionContextManager
    val sessionContext: SessionContext
    val log: SessionLogService
    val conf: ContextConfig
    val interceptorChain: CommandLifecycleInterceptorChain get() = EmptyInterceptorChain

    fun isAnonymous(): Boolean = sessionContext.isAnonymous()

    override val principalId: String
        get() = sessionContext.context.principal.toString()

    override val tenantId: String
        get() = sessionContext.context.tenant.tenantId

    @ContributesTo(SessionScope::class)
    interface Graph {
        val sessionExecution: SessionExecution
    }
}

interface HasProvenance {
    val principalId: String
    val tenantId: String
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ContextConfig", exact = true)
interface ContextConfig {
    val app: AppConfigService
    val tenant: TenantConfigService
    val principal: PrincipalConfigService

    fun conf(level: ConfigLevel): ConfigService
}

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ContextConfigImpl", exact = true)
class ContextConfigImpl(
    override val app: AppConfigService,
    override val tenant: TenantConfigService,
    override val principal: PrincipalConfigService,
) : ContextConfig {
    override fun conf(level: ConfigLevel): ConfigService =
        when (level) {
            ConfigLevel.APP -> app
            ConfigLevel.TENANT -> tenant
            ConfigLevel.PRINCIPAL -> principal
        }
}

@JsExportCompat
enum class IdkScope {
    APP,
    USER,
    SESSION,
}

// interface ICoreApiContextGraph: ISureContextGraph, ICoreApiContextExtensionGraph

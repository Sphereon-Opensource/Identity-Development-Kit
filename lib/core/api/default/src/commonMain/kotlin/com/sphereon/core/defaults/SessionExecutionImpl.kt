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

package com.sphereon.core.defaults

import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.session.CommandLifecycleInterceptorChain
import com.sphereon.di.context.ResolvedTenantIdProvider
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionExecutionImpl", exact = true)
class SessionExecutionImpl(
    override val sessionContext: SessionContext,
    override val log: SessionLogService,
    override val conf: ContextConfig,
    override val sessionContextManager: SessionContextManager,
    override val interceptorChain: CommandLifecycleInterceptorChain,
    /**
     * Path-peel descent override. Null in the common case; populated by the
     * adapter dispatcher when a [com.sphereon.core.api.http.command.TenantPathPolicy]
     * peel resolves to a child or root tenant. The override is preferred over
     * the base session tenant in [tenantId] so downstream consumers see the
     * descended tenant transparently.
     */
    private val resolvedTenantIdProvider: ResolvedTenantIdProvider,
) : SessionExecution {
    override val tenantId: String
        get() = resolvedTenantIdProvider.currentTenantId() ?: sessionContext.context.tenant.tenantId

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as SessionExecutionImpl

        return sessionContext == other.sessionContext
    }

    override fun hashCode(): Int = sessionContext.hashCode()
}

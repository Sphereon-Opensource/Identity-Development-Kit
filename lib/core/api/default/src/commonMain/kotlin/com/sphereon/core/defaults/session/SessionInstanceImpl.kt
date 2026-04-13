/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.core.defaults.session

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.conf.ConfigCacheWarmup
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.di.session.SessionComponent
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import dev.zacsweers.metro.Inject
import software.amazon.app.platform.scope.Scope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.concurrent.Volatile

/**
 * SessionInstance implementation.
 * Component and scope are set by the manager after the component is created.
 * Thread-safe initialization is ensured through synchronization.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionInstance>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionInstanceImpl", exact = true)
class SessionInstanceImpl(
    override val sessionContext: SessionContext,
    override val sessionExecution: SessionExecution,
    override val sessionContextManager: SessionContextManager,
    private val configCacheWarmup: ConfigCacheWarmup
) : SessionInstance, SynchronizedObject() {

    override lateinit var component: SessionComponent
        private set

    override lateinit var scope: Scope
        private set

    override val sessionId: String
        get() = sessionContext.sessionId

    @Volatile
    private var initialized = false

    @Volatile
    private var cacheWarmedUp = false

    internal fun initialize(component: SessionComponent, scope: Scope) {
        synchronized(this) {
            if (!initialized) {
                this.component = component
                this.scope = scope
                initialized = true
            }
        }
    }

    override suspend fun warmupCacheAsync() {
        if (cacheWarmedUp) return

        val tenantId = sessionContext.context.tenant.tenantId
        val principalId = sessionContext.context.principal?.toString()

        // Warm up KMS-related config prefixes
        configCacheWarmup.warmupAsync(
            prefixes = KMS_CONFIG_PREFIXES,
            level = ConfigLevel.PRINCIPAL,
            tenantId = tenantId,
            principalId = principalId
        )

        cacheWarmedUp = true
    }

    companion object {
        /**
         * Config prefixes that need to be warmed up before KMS initialization.
         */
        val KMS_CONFIG_PREFIXES = setOf(
            "kms.providers",
            "kms.keystores",
            "sphereon.default.kms"
        )
    }

    override fun isCurrentlyActive(): Boolean {
        return sessionContextManager.getActive().sessionId == sessionId
    }

    override fun makeActive(): Boolean {
        return sessionContextManager.activateById(sessionId)
    }

    override fun destroy() {
        sessionContextManager.destroyById(sessionId)
    }

    override fun <T : Any> getService(id: String): T {
        val service: T = scope.children().find { it.name == id }?.getService(id)
            ?: throw NotFoundException("Service with id $id not found in session $sessionId. Available children: ${scope.children().joinToString(",")}")
        return service
    }

    override fun addService(id: String, service: Any): Scope {
        return scope.buildChild(id) {
            addService(id, service)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SessionInstanceImpl

        return sessionContext == other.sessionContext
    }

    override fun hashCode(): Int {
        return sessionContext.hashCode()
    }


}

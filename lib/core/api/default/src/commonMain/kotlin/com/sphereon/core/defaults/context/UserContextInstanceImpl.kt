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

package com.sphereon.core.defaults.context

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextGraph
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserContextManager
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import software.amazon.app.platform.scope.Scope
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * UserContextInstance implementation.
 * Graph and scope are set by the manager after the graph is created.
 * This object is normally used to get access to the DI graph and user context from within the UserContext scope itself. It is injectable in UserScope
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<UserContextInstance>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextInstanceImpl", exact = true)
class UserContextInstanceImpl(
    override val userContextManager: UserContextManager,
) : SynchronizedObject(),
    UserContextInstance {
    override val context: UserContext
        get() = graph.userContext
    override val contextId: String
        get() = context.id

    override lateinit var graph: UserContextGraph
        private set

    override lateinit var scope: Scope
        private set

    override val sessionContextManager: SessionContextManager
        get() = this.graph.sessionContextManager

    @Volatile
    private var initialized = false

    internal fun initialize(
        graph: UserContextGraph,
        scope: Scope,
    ) {
        synchronized(this) {
            if (!initialized) {
                this.graph = graph
                this.scope = scope
                initialized = true
            }
        }
    }

    override fun isCurrentlyActive(): Boolean = userContextManager.getActive().contextId == contextId

    override fun makeActive(): Boolean = userContextManager.activateById(contextId)

    override fun destroy() {
        userContextManager.destroyById(contextId)
    }

    override fun <T : Any> getService(id: String): T {
        val service: T =
            scope.getService(id)
                ?: throw NotFoundException("Service with id $id not found in context $contextId")
        return service
    }

    override fun addService(
        id: String,
        service: Any,
    ): Scope =
        scope.buildChild(id) {
            addService(id, service)
        }

    override fun createSession(
        sessionId: String,
        correlationId: String,
        makeActive: Boolean,
    ): SessionInstance = sessionContextManager.createOrGetFromId(sessionId, correlationId, makeActive)

    override fun getOrCreateAnonymousSession(makeActive: Boolean): SessionInstance = sessionContextManager.getAnonymous(makeActive)

    override fun getOrCreateBackgroundServiceSession(makeActive: Boolean): SessionInstance = sessionContextManager.getOrCreateBackgroundService(makeActive)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as UserContextInstanceImpl

        if (context != other.context) {
            return false
        }
        if (contextId != other.contextId) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + contextId.hashCode()
        return result
    }
}

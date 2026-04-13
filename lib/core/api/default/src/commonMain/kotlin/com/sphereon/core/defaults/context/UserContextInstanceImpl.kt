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

package com.sphereon.core.defaults.context

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextComponent
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserContextManager
import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import dev.zacsweers.metro.Inject
import software.amazon.app.platform.scope.Scope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.concurrent.Volatile

/**
 * UserContextInstance implementation.
 * Component and scope are set by the manager after the component is created.
 * This object is normally used to get access to the DI component and user context from within the UserContext scope itself. It is injectable in UserScope
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<UserContextInstance>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextInstanceImpl", exact = true)
class UserContextInstanceImpl(
    override val userContextManager: UserContextManager,
) : UserContextInstance, SynchronizedObject() {
    override val context: UserContext
        get() = component.userContext
    override val contextId: String
        get() = context.id

    override lateinit var component: UserContextComponent
        private set

    override lateinit var scope: Scope
        private set

    @Volatile
    private var initialized = false

    internal fun initialize(component: UserContextComponent, scope: Scope) {
        synchronized(this) {
            if (!initialized) {
                this.component = component
                this.scope = scope
                initialized = true
            }
        }
    }

    override fun isCurrentlyActive(): Boolean {
        return userContextManager.getActive().contextId == contextId
    }

    override fun makeActive(): Boolean {
        return userContextManager.activateById(contextId)
    }

    override fun destroy() {
        userContextManager.destroyById(contextId)
    }

    override fun <T : Any> getService(id: String): T {
        val service: T = scope.getService(id)
            ?: throw NotFoundException("Service with id $id not found in context $contextId")
        return service
    }

    override fun addService(id: String, service: Any): Scope {
        return scope.buildChild(id) {
            addService(id, service)
        }
    }

    override val sessionContextManager: SessionContextManager
        get() = this.component.sessionContextManager


    override fun createSession(sessionId: String, makeActive: Boolean): SessionInstance {
        return sessionContextManager.createOrGetFromId(sessionId, makeActive)
    }

    override fun getOrCreateAnonymousSession(makeActive: Boolean): SessionInstance {
        return sessionContextManager.getAnonymous(makeActive)
    }

    override fun getOrCreateBackgroundServiceSession(makeActive: Boolean): SessionInstance {
        return sessionContextManager.getOrCreateBackgroundService(makeActive)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UserContextInstanceImpl

        if (context != other.context) return false
        if (contextId != other.contextId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + contextId.hashCode()
        return result
    }


}
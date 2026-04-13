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

package com.sphereon.di.context

import software.amazon.app.platform.scope.Scope
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Thread-safe user context instance.
 * Provides stable access to context operations without race conditions.
 * This instance holds a stable reference to the context data at creation time.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextInstance", exact = true)
interface UserContextInstance {
    // Stable context data (immutable references)
    val contextId: String
    val context: UserContext
    val component: UserContextComponent
    val scope: Scope

    val userContextManager: UserContextManager

    // Context operations (thread-safe using stable references)
    fun <T : Any> getService(id: String): T
    fun addService(id: String, service: Any): Scope

    // Context lifecycle
    fun isCurrentlyActive(): Boolean  // Check if this context is currently the active one
    fun makeActive(): Boolean         // Make this context the active one
    fun destroy()                     // Destroy this context

    fun createSession(sessionId: String, makeActive: Boolean = true): SessionInstance
    fun getOrCreateAnonymousSession(makeActive: Boolean = false): SessionInstance
    fun getOrCreateBackgroundServiceSession(makeActive: Boolean = false): SessionInstance
    val sessionContextManager: SessionContextManager
}

// Convenience extension function for type-safe service access
inline fun <reified T : Any> UserContextInstance.getService(): T =
    getService(T::class.simpleName ?: error("Cannot get service name for ${T::class}"))
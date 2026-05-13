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

package com.sphereon.di.session

import dev.zacsweers.metro.Named
import kotlinx.coroutines.flow.StateFlow
import software.amazon.app.platform.scope.Scope
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContextManager", exact = true)
interface SessionContextManager {
    // Primary access method - always returns instance (anonymous if map empty)
    fun getActive(): SessionInstance

    fun hasActive(): Boolean

    // Instance-based access (primary approach) - with makeActive capability
    fun getById(
        sessionId: String,
        makeActive: Boolean = false,
    ): SessionInstance?

    fun hasById(sessionId: String): Boolean

    // Session switching (updates flows)
    fun activateById(sessionId: String): Boolean

    // Session listing
    fun listIds(): Set<String>

    val activeInstance: StateFlow<SessionInstance?>

    // Session creation (returns instances)
    fun createOrGetFromCallbacks(sessionContextProvider: () -> SessionContext): SessionInstance

    /**
     * Open a session by id, optionally supplying a correlationId. When
     * omitted the session uses its own [sessionId] as the correlation
     * anchor — natural when there is no upstream caller-supplied
     * correlation to inherit. If a session with the given id already
     * exists it is returned as-is and the supplied `correlationId` is
     * ignored (the session's correlationId was fixed at first creation).
     */
    fun createOrGetFromId(
        @Named("sessionId") sessionId: String,
        @Named("correlationId") correlationId: String = sessionId,
        makeActive: Boolean = true,
    ): SessionInstance

    // Session cleanup
    fun destroyById(sessionId: String)

    fun destroyAll()

    // Special session types - return instances (not components, since these are convenience methods)
    fun getOrCreateBackgroundService(makeActive: Boolean = false): SessionInstance

    fun getAnonymous(makeActive: Boolean = false): SessionInstance

    fun getBackgroundServiceId(): String
}

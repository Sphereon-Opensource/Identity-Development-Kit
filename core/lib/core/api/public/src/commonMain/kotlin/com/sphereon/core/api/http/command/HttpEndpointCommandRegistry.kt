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
 */

package com.sphereon.core.api.http.command

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo

/**
 * Session-scoped, keyed lazy resolver for HTTP endpoint commands.
 *
 * Implementations must not materialize commands while listing identities. A route selected from
 * AppScope supplies the one [handlerCommandId] that is allowed to be resolved for the request.
 */
interface HttpEndpointCommandRegistry {
    /** Session graph access point for transport boundaries that need the selected endpoint. */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val httpEndpointCommandRegistry: HttpEndpointCommandRegistry
    }

    fun get(handlerCommandId: String): HttpEndpointCommand?

    fun listHandlerCommandIds(): Set<String>
}

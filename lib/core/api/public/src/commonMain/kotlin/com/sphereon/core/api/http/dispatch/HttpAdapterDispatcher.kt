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

package com.sphereon.core.api.http.dispatch

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo

/**
 * Session-scoped execution boundary for a route selected from the application-scoped catalog.
 * Route selection, normalization metadata, and ambiguity rejection happen before SessionScope.
 */
interface HttpAdapterDispatcher {
    /**
     * Executes a route selected from AppScope before the request SessionScope was constructed.
     * Implementations must defensively verify that the runtime adapter still has this identity.
     */
    suspend fun dispatch(
        request: GenericHttpRequest,
        route: HttpAdapterRouteMatch,
    ): GenericHttpResponse

    @ContributesTo(SessionScope::class)
    interface Graph {
        val httpAdapterDispatcher: HttpAdapterDispatcher
    }
}

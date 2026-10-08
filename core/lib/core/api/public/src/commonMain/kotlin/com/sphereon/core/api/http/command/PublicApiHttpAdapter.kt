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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.describe.HttpAdapterMount

/**
 * Base class for HTTP adapters that expose public service commands as HTTP endpoints.
 *
 * Endpoint wrappers are contributed independently to [HttpEndpointCommandRegistry], so selecting
 * an adapter does not instantiate every service command behind it.
 *
 * **Usage:**
 * ```kotlin
 * @Inject
 * @SingleIn(SessionScope::class)
 * @ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
 * @StringKey(BookingHttpAdapter.ID)
 * class BookingHttpAdapter(
 *     execution: SessionExecution,
 *     endpointCommandRegistry: HttpEndpointCommandRegistry,
 * ) : PublicApiHttpAdapter(
 *     id = ID,
 *     execution = execution,
 *     mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api/booking/v1/bookings"),
 *     endpointCommandRegistry = endpointCommandRegistry,
 * )
 * ```
 *
 * @param id Unique identifier for this adapter
 * @param execution The session execution context
 * @param mount The mount configuration for this adapter
 * @param endpointCommandRegistry Session-scoped lazy endpoint resolver. App-scoped
 * descriptors remain the source of route metadata.
 */
abstract class PublicApiHttpAdapter(
    id: String,
    protected val sessionExecution: SessionExecution,
    mount: HttpAdapterMount,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
) : CommandBackedHttpAdapter(
        id = id,
        execution = sessionExecution,
        mount = mount,
        endpointCommandRegistry = endpointCommandRegistry,
    )

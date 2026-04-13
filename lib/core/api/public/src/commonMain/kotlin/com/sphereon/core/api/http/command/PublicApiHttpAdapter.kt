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
import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.service.ServiceCommand

/**
 * Base class for HTTP adapters that expose [ServiceCommand]s as HTTP endpoints.
 *
 * This adapter bridges [ServiceCommand] (typed I/O, authorization, audit, tracing,
 * dual transport) with the HTTP adapter infrastructure ([CommandBackedHttpAdapter],
 * [HttpEndpointCommand]).
 *
 * Subclasses declare their service commands via the [serviceCommands] property.
 * Each command that implements [PublicApiCommand] is automatically wrapped in a
 * [ServiceCommandEndpoint] for metadata-driven routing.
 *
 * **Usage:**
 * ```kotlin
 * @Inject
 * @SingleIn(SessionScope::class)
 * @ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
 * class BookingHttpAdapter(
 *     execution: SessionExecution,
 *     listBookings: ListBookingsServiceCommand,
 *     getBooking: GetBookingServiceCommand
 * ) : PublicApiHttpAdapter(
 *     id = ID,
 *     execution = execution,
 *     mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api/booking/v1/bookings")
 * ) {
 *     companion object { const val ID = "RESOURCE-BOOKINGS" }
 *
 *     override val serviceCommands = listOf(listBookings, getBooking)
 * }
 * ```
 *
 * @param id Unique identifier for this adapter
 * @param execution The session execution context
 * @param mount The mount configuration for this adapter
 */
abstract class PublicApiHttpAdapter(
    id: String,
    protected val sessionExecution: SessionExecution,
    mount: HttpAdapterMount,
) : CommandBackedHttpAdapter(id = id, execution = sessionExecution, mount = mount) {
    /** Service commands to expose as HTTP endpoints. Must implement [PublicApiCommand]. */
    abstract val serviceCommands: List<ServiceCommand<*, *>>

    override val endpointCommands: List<HttpEndpointCommand> by lazy {
        serviceCommands.mapNotNull { cmd ->
            val publicApi = cmd as? PublicApiCommand ?: return@mapNotNull null
            ServiceCommandEndpoint(
                serviceCommand = cmd,
                endpoint = publicApi.httpEndpoint,
                execution = sessionExecution,
            )
        }
    }
}

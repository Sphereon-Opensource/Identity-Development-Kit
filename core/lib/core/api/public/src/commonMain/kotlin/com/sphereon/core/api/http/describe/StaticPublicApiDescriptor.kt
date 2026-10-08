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

package com.sphereon.core.api.http.describe

import com.sphereon.core.compat.JsExportCompat

/**
 * Convenience base class for app-scoped [HttpAdapterDescriptorProvider] implementations
 * that describe a [PublicApiHttpAdapter][com.sphereon.core.api.http.command.PublicApiHttpAdapter]
 * with a static list of endpoints.
 *
 * Designed to be used as a nested `Descriptor` class inside the adapter itself, replacing
 * separate `*HttpAdapterDescriptors.kt` and `*PublicApiRoutes.kt` files.
 *
 * **Usage:**
 * ```kotlin
 * class BookingHttpAdapter(...) : PublicApiHttpAdapter(...) {
 *
 *     @Inject
 *     @SingleIn(AppScope::class)
 *     @ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
 *     class Descriptor : StaticPublicApiDescriptor(
 *         adapterId = ID,
 *         mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api/booking/v1/bookings"),
 *         endpoints = listOf(
 *             ListBookingsServiceCommand.ENDPOINT,
 *             GetBookingServiceCommand.ENDPOINT
 *         )
 *     )
 * }
 * ```
 *
 * @param adapterId Stable identifier matching the runtime adapter's [id]
 * @param mount The mount configuration for the adapter
 * @param endpoints The endpoint descriptors (with relative paths within the adapter base)
 */
@JsExportCompat
abstract class StaticPublicApiDescriptor(
    private val adapterId: String,
    private val mount: HttpAdapterMount,
    private val endpoints: List<HttpEndpointDescriptor>,
) : HttpAdapterDescriptorProvider {
    override val id: String get() = adapterId

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = adapterId,
            mount = mount,
            endpoints =
                endpoints.map { endpoint ->
                    endpoint.copy(
                        pathPatterns = endpoint.pathPatterns.map { buildFullPath(mount.adapterBasePath, it) },
                    )
                },
        )

    private fun buildFullPath(
        basePath: String,
        relativePath: String,
    ): String {
        if (basePath.isEmpty() || basePath == "/") {
            return relativePath
        }
        return "$basePath$relativePath".replace("//", "/")
    }
}

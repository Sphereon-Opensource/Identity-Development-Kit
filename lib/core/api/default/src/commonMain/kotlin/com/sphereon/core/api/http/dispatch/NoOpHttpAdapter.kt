/*
 * Copyright 2023-2026 Sphereon International B.V.
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

import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class NoOpHttpAdapter : RoutedHttpAdapter() {
    override val id: String = ID

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "",
        )

    // Routes are relative to the mount (adapterBasePath = /keys)
    // The mount prefix is automatically prepended when matching requests
    override val routes =
        httpRoutes {
        }

    companion object {
        const val ID = "__noop__"
    }
}

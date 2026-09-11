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

package com.sphereon.core.defaults.service

import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.config.UniversalHttpConfigContribution
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.ServiceCommandGroupDescriptorProvider
import com.sphereon.core.api.service.PublicApiRouteDescriptor
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Named

/**
 * Declares multibindings that may be empty at session scope.
 *
 * Metro requires `@Multibinds(allowEmpty = true)` for sets/maps that might have
 * zero contributors on a given classpath configuration.
 */
@ContributesTo(SessionScope::class)
interface SessionScopeMultibindDeclarations {
    @Multibinds(allowEmpty = true)
    fun httpAdaptersById(): Map<String, HttpAdapter>

    @Multibinds(allowEmpty = true)
    fun httpEndpointCommandsById(): Map<String, HttpEndpointCommand>

    @Multibinds(allowEmpty = true)
    fun localCommands(): Map<String, ServiceCommand<*, *, *>>

    @Multibinds(allowEmpty = true)
    @Named("remote")
    fun remoteCommands(): Map<String, ServiceCommand<*, *, *>>
}

/**
 * Declares multibindings that may be empty at app scope.
 */
@ContributesTo(AppScope::class)
interface AppScopeMultibindDeclarations {
    @Multibinds(allowEmpty = true)
    fun httpAdapterDescriptorProviders(): Set<HttpAdapterDescriptorProvider>

    @Multibinds(allowEmpty = true)
    fun universalHttpConfigContributions(): Set<UniversalHttpConfigContribution>

    @Multibinds(allowEmpty = true)
    fun serviceCommandGroupDescriptorProviders(): Set<ServiceCommandGroupDescriptorProvider>

    @Multibinds(allowEmpty = true)
    fun publicApiRouteDescriptors(): Set<PublicApiRouteDescriptor>
}

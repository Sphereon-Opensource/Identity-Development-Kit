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

package com.sphereon.did.manager.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.DidProvider
import com.sphereon.did.manager.DidProviderRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of the DID provider registry.
 *
 * Manages a collection of method-specific providers and provides
 * lookup functionality by DID method.
 *
 * Providers are automatically discovered via DI multibinding.
 * Any class annotated with `@ContributesIntoSet(SessionScope::class, binding = binding<DidProvider>())`
 * will be automatically registered.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidProviderRegistry>())
class DidProviderRegistryImpl(
    providers: Set<DidProvider>,
) : DidProviderRegistry {
    private val providersByMethod: Map<String, DidProvider> by lazy {
        providers.associateBy { it.method }
    }

    override fun getProvider(method: String): DidProvider? = providersByMethod[method]

    override fun getCapabilities(method: String): DidMethodCapabilities? = providersByMethod[method]?.capabilities

    override fun getSupportedMethods(): List<String> = providersByMethod.keys.toList()
}

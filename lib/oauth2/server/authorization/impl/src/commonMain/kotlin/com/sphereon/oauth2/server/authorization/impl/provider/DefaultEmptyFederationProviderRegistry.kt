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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default empty registry — federation effectively disabled. The DI graph composes cleanly with
 * this binding present; deployments wanting actual federation contribute a real registry with
 * `replaces = [DefaultEmptyFederationProviderRegistry::class]`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<FederationProviderRegistry>())
class DefaultEmptyFederationProviderRegistry : FederationProviderRegistry {
    override fun findById(providerId: String): FederationProviderConfig? = null

    override fun all(): List<FederationProviderConfig> = emptyList()

    override fun defaultProviderId(): String? = null
}

/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.kms.rest.api.mapper.KeyProviderPresentation
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Supplies what an engine provider cannot say about itself.
 *
 * An engine-native provider is built from configuration and describes its own technology, so it
 * needs nothing here. A provider that only exists because a tenant owns a resource has no
 * constructible engine type: its real technology, name, owner and default flag live in that
 * resource record, and only a deployment that can read the record can supply them.
 *
 * The seam exists so the engine keeps one way to render a provider. Making the adapter reach for
 * the record itself would put resource-model knowledge into a surface whose whole purpose is not
 * to have any.
 */
fun interface KeyProviderPresentationSource {
    /** Null means the provider describes itself and is rendered from its engine type. */
    suspend fun presentationFor(provider: KmsProvider): KeyProviderPresentation?
}

/**
 * The engine-native answer: every provider describes itself.
 *
 * A deployment with a resource model replaces this binding rather than extending it, so an
 * assembly that has no such model cannot accidentally present a half-resolved provider.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyProviderPresentationSource>())
class SelfDescribingKeyProviderPresentationSource : KeyProviderPresentationSource {
    override suspend fun presentationFor(provider: KmsProvider): KeyProviderPresentation? = null
}

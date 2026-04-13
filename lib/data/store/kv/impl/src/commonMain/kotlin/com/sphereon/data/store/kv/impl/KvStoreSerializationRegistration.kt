/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.data.store.kv.impl

import com.sphereon.core.api.json.SerializerRegistration
import com.sphereon.data.store.kv.KvStoreJsonSupport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped

/**
 * DI-driven serialization registration for KV store config types.
 *
 * Automatically registers polymorphic serializers for KvStoreConfigBase subtypes
 * when the AppScope is entered.
 */
@Inject
@ContributesBinding(AppScope::class, binding = binding<SerializerRegistration>())
@SingleIn(AppScope::class)
class KvStoreSerializationRegistration : SerializerRegistration {
    override fun onEnterScope(scope: Scope) {
        KvStoreJsonSupport.ensureBaseTypesRegistered()
    }
}

@ContributesTo(AppScope::class)
interface KvStoreSerializationRegistrationModule {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideScoped(impl: KvStoreSerializationRegistration): Scoped = impl
}

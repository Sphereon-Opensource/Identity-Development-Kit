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

package com.sphereon.data.store.blob.kv

import com.sphereon.core.api.json.SerializerRegistration
import com.sphereon.data.store.blob.BlobStoreConfigBase
import com.sphereon.data.store.blob.BlobStoreJsonSupport
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped

/**
 * Automatic JSON serialization registration for KvStore-backed blob store config.
 *
 * Discovered and initialized when the AppScope is created via DI multibinding.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<SerializerRegistration>())
@SingleIn(AppScope::class)
class KvBlobStoreSerializationRegistration : SerializerRegistration {
    override fun onEnterScope(scope: Scope) {
        BlobStoreJsonSupport.register("kv-blob-store") {
            polymorphic(BlobStoreConfigBase::class) {
                subclass(KvBlobStoreConfig::class, KvBlobStoreConfig.serializer())
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface KvBlobStoreSerializationRegistrationModule {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideScoped(impl: KvBlobStoreSerializationRegistration): Scoped = impl
}

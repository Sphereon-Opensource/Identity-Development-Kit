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
 *
 */

package com.sphereon.crypto.kms.keystore.memory

import kotlinx.serialization.modules.polymorphic
import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.json.SerializerRegistration
import com.sphereon.crypto.core.kms.AbstractKeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreConfig
import dev.zacsweers.metro.Inject
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Automatic JSON serialization registration for Memory KeyStore.
 *
 * This class is automatically discovered and initialized when the AppScope is created.
 * No manual registration calls are needed.
 */
@Inject
@ContributesBinding(AppScope::class, binding = binding<SerializerRegistration>())
@SingleIn(AppScope::class)
class MemoryKeystoreSerializationRegistration : SerializerRegistration {
    override fun onEnterScope(scope: Scope) {
        CryptoJsonSupport.register("memory-keystore") {
            polymorphic(KeyStoreConfig::class) {
                subclass(MemoryKeyStoreConfig::class, MemoryKeyStoreConfig.serializer())
            }
            polymorphic(MemoryKeyStoreConfigType::class) {
                subclass(MemoryKeyStoreConfig::class, MemoryKeyStoreConfig.serializer())
            }
            polymorphic(AbstractKeyStoreConfig::class) {
                subclass(MemoryKeyStoreConfig::class, MemoryKeyStoreConfig.serializer())
            }
        }
    }
}

@ContributesTo(AppScope::class)
interface MemoryKeystoreSerializationRegistrationModule {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideScoped(impl: MemoryKeystoreSerializationRegistration): Scoped = impl
}

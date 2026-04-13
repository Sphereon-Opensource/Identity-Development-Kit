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

package com.sphereon.crypto.kms.provider.mobile

import com.sphereon.core.api.log.AppLogManager
import kotlinx.serialization.modules.polymorphic
import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.json.SerializerRegistration
import com.sphereon.crypto.core.kms.AbstractKmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
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
 * Automatic JSON serialization registration for Mobile KMS Provider.
 *
 * This class is automatically discovered and initialized when the AppScope is created.
 * No manual registration calls are needed.
 */
@Inject
@ContributesBinding(AppScope::class, binding = binding<SerializerRegistration>())
@SingleIn(AppScope::class)
class MobileKmsSerializationRegistration(logManager: AppLogManager) : SerializerRegistration {
    private val log = logManager.withTag("SerializationRegistration")
    override fun onEnterScope(scope: Scope) {
        CryptoJsonSupport.register("mobile-kms") {
            polymorphic(KmsProviderConfigBase::class) {
                subclass(MobileKmsProviderConfig::class, MobileKmsProviderConfig.serializer())
            }
            polymorphic(MobileKmsProviderConfigType::class) {
                subclass(MobileKmsProviderConfig::class, MobileKmsProviderConfig.serializer())
            }
            polymorphic(AbstractKmsProviderConfig::class) {
                subclass(MobileKmsProviderConfig::class, MobileKmsProviderConfig.serializer())
            }
        }
        log.info("MobileKms Serialization registered")
    }
}

@ContributesTo(AppScope::class)
interface MobileKmsSerializationRegistrationModule {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun provideScoped(impl: MobileKmsSerializationRegistration): Scoped = impl
}

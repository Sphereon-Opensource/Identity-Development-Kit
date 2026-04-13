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

package com.sphereon.crypto.kms.provider.software

import com.sphereon.core.api.context.SessionExecution
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderFactory
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.ContributesIntoSet
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName


@AssistedFactory
@OptIn(ExperimentalObjCName::class)
@ObjCName("RealKmsProviderFactory", exact = true)
interface RealKmsProviderFactory {
    fun create(providerConfig: KmsProviderConfigBase, execution: SessionExecution, keyStoreManager: KeyStoreManager? = null): SoftwareKmsProviderImpl
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<com.sphereon.crypto.core.kms.KmsProviderFactory>())
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("SoftwareKmsProviderFactoryImpl", exact = true)
class SoftwareKmsProviderFactoryImpl(
    val keyStoreManager: KeyStoreManager,
    val kmsProviderFactory: RealKmsProviderFactory,
) : SoftwareKmsProviderFactory {
    // Note: Serialization registration is now handled automatically via SerializerRegistration
    // when the AppScope is created. No manual registration needed.

    override val kmsProviderType: String = PredefinedKmsProviderTypes.SOFTWARE.kmsProviderType
    override fun create(config: KmsProviderConfigBase, execution: SessionExecution): SoftwareKmsProviderImpl = kmsProviderFactory.create(providerConfig = config, execution = execution, keyStoreManager = keyStoreManager)

    @ContributesTo(AppScope::class)
    interface Component {
        val softwareKmsProvider: SoftwareKmsProviderFactory
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("SoftwareKmsProviderFactory", exact = true)
interface SoftwareKmsProviderFactory : KmsProviderFactory {
    override fun create(config: KmsProviderConfigBase, execution: SessionExecution): SoftwareKmsProviderImpl
}

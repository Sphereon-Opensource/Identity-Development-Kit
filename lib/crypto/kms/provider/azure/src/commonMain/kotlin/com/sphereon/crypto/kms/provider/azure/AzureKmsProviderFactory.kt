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

package com.sphereon.crypto.kms.provider.azure

import com.sphereon.core.api.context.SessionExecution
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesIntoSet
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderFactory
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<com.sphereon.crypto.core.kms.KmsProviderFactory>())
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("AzureKmsProviderFactoryImpl", exact = true)
class AzureKmsProviderFactoryImpl : AzureKmsProviderFactory {
    init {
        registerAzureKmsSerialization()
    }

    override val kmsProviderType: String = PredefinedKmsProviderTypes.AZURE_KEYVAULT.kmsProviderType

    override fun create(config: KmsProviderConfigBase, execution: SessionExecution): AzureKeyVaultCryptoProvider {
        require(config is AzureKmsProviderConfig) {
            "Config must be of type AzureKmsProviderConfig, got ${config::class.simpleName}"
        }

        return AzureKeyVaultCryptoProvider(config)
    }

    @ContributesTo(AppScope::class)
    interface Component {
        val azureKmsProvider: AzureKmsProviderFactory
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AzureKmsProviderFactory", exact = true)
interface AzureKmsProviderFactory : KmsProviderFactory {
    override fun create(config: KmsProviderConfigBase, execution: SessionExecution): AzureKeyVaultCryptoProvider
}

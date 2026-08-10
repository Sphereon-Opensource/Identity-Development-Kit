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

package com.sphereon.crypto.kms.provider.aws

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderFactory
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
import com.sphereon.crypto.core.kms.model.KeyProviderConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<KmsProviderFactory>())
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("AwsKmsProviderFactoryImpl", exact = true)
class AwsKmsProviderFactoryImpl : AwsKmsProviderFactory {
    init {
        registerAwsKmsSerialization()
    }

    override val kmsProviderType: String = PredefinedKmsProviderTypes.AWS_KMS.kmsProviderType

    override fun create(
        config: KmsProviderConfigBase,
        execution: SessionExecution,
    ): AwsKmsCryptoProvider {
        require(config is AwsKmsProviderConfig) {
            "Config must be of type AwsKmsProviderConfig, got ${config::class.simpleName}"
        }
        execution.log.debug(
            "[AwsKmsProviderFactory] creating provider id=${config.id} region=${config.region} " +
                "endpointConfigured=${!config.endpointUrl.isNullOrBlank()} credentialMode=${config.credentialOpts.credentialMode}",
        )
        // Bridge the polymorphic config to the legacy KeyProviderSettings the AWS crypto provider expects.
        val settings = KeyProviderSettings(
            id = config.id,
            config = KeyProviderConfig(
                type = KeyProviderType.AWS_KMS,
                aws = config.toAwsKmsClientConfig(),
            ),
        )
        return AwsKmsCryptoProvider(settings)
    }

    @ContributesTo(AppScope::class)
    interface Graph {
        val awsKmsProvider: AwsKmsProviderFactory
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AwsKmsProviderFactory", exact = true)
interface AwsKmsProviderFactory : KmsProviderFactory {
    override fun create(
        config: KmsProviderConfigBase,
        execution: SessionExecution,
    ): AwsKmsCryptoProvider
}

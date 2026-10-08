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

import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.kms.AbstractKmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.model.AwsKmsClientConfig
import kotlinx.serialization.modules.polymorphic

/** Build the legacy [AwsKmsClientConfig] the AWS crypto provider consumes from this polymorphic config. */
fun AwsKmsProviderConfig.toAwsKmsClientConfig(): AwsKmsClientConfig =
    AwsKmsClientConfig(
        applicationId = applicationId,
        region = region,
        endpointUrl = endpointUrl,
        credentialOpts = credentialOpts,
        exponentialBackoffRetryOpts = exponentialBackoffRetryOpts,
    )

/**
 * Registers Json serialization at runtime; the KMS providers are resolved dynamically off the classpath,
 * so each provider type registers its polymorphic config subclass the way `azure`/`software` do.
 */
object AwsKmsSerializationRegistration {
    init {
        CryptoJsonSupport.register {
            polymorphic(KmsProviderConfigBase::class) {
                subclass(AwsKmsProviderConfig::class, AwsKmsProviderConfig.serializer())
            }
            polymorphic(AwsKmsProviderConfigType::class) {
                subclass(AwsKmsProviderConfig::class, AwsKmsProviderConfig.serializer())
            }
            polymorphic(AbstractKmsProviderConfig::class) {
                subclass(AwsKmsProviderConfig::class, AwsKmsProviderConfig.serializer())
            }
        }
    }
}

fun registerAwsKmsSerialization() {
    AwsKmsSerializationRegistration
}

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

package com.sphereon.crypto.kms.provider.rest

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderFactory
import com.sphereon.crypto.core.kms.PredefinedKmsProviderTypes
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
@ContributesIntoSet(AppScope::class, binding = binding<com.sphereon.crypto.core.kms.KmsProviderFactory>())
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestClientKmsProviderFactoryImpl", exact = true)
class RestClientKmsProviderFactoryImpl(
    val keyStoreManager: KeyStoreManager,
    val realFactory: RestClientKmsProviderImplFactory,
) : RestClientKmsProviderFactory {
    init {
        registerRestClientKmsSerialization()
    }

    override val kmsProviderType: String = PredefinedKmsProviderTypes.REST.kmsProviderType

    override fun create(
        config: KmsProviderConfigBase,
        execution: SessionExecution,
    ): RestClientKmsProviderImpl = realFactory.create(config, execution)

    @ContributesTo(AppScope::class)
    interface Graph {
        val restClientKmsProvider: RestClientKmsProviderFactory
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RestClientKmsProviderFactory", exact = true)
interface RestClientKmsProviderFactory : KmsProviderFactory {
    override fun create(
        config: KmsProviderConfigBase,
        execution: SessionExecution,
    ): RestClientKmsProvider
}

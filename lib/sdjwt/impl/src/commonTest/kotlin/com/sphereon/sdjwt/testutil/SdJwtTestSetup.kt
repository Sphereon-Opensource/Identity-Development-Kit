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

package com.sphereon.sdjwt.testutil

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance
import dev.whyoleg.cryptography.CryptographyProvider

expect fun createSdJwtTestAppComponent(testInstance: Any): AppComponent

class SdJwtTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent = createSdJwtTestAppComponent(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)
    val keyManagerService: KeyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService

    init {
        val config = SoftwareKmsProviderConfig(
            id = "$sessionId-software-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val softwareKmsProvider = (app as SoftwareKmsProviderFactoryImpl.Component)
            .softwareKmsProvider
            .create(config, session.asCoreApiServiceComponent().serviceExecution)
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }
}

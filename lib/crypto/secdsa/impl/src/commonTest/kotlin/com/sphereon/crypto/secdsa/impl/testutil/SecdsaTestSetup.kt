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

package com.sphereon.crypto.secdsa.impl.testutil

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProvider
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance
import dev.whyoleg.cryptography.CryptographyProvider

expect fun createSecdsaTestAppGraph(testInstance: Any): AppGraph

class SecdsaTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph = createSecdsaTestAppGraph(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)

    val softwareKmsProvider: SoftwareKmsProvider =
        (app as SoftwareKmsProviderFactoryImpl.Graph)
            .softwareKmsProvider
            .create(
                SoftwareKmsProviderConfig(
                    id = "$sessionId-software-provider",
                    cryptographyProvider = CryptographyProvider.Default.name,
                ),
                session.asCoreApiServiceGraph().serviceExecution,
            )
}

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

package com.sphereon.mdoc.core.testutil

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.cose.CoseCryptoProviderToCallbackAdapter
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.CertificateServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionGraph
import com.sphereon.mdoc.MdocSignService

expect fun createMdocTestAppGraph(testInstance: Any): AppGraph

expect fun createMdocSignService(execution: SessionExecution): MdocSignService

class MdocTestContext(
    testInstance: Any,
) {
    val app: AppGraph = createMdocTestAppGraph(testInstance)
    val userContext = app.userContextManager.getAnonymous()
    val sessionContext = userContext.sessionContextManager.createOrGetFromId("mdoc-test-${Uuid.v4String()}", principalType = com.sphereon.di.context.PrincipalType.USER)
    val sessionGraph: SessionGraph = sessionContext.graph

    val kms: KeyManagerService = sessionGraph.asKeyManagerServiceGraph().keyManagerService
    val certificateService: CertificateService
    val mdocSignService: MdocSignService

    init {
        val softwareProviderFactory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
        val softwareConfig = SoftwareKmsProviderConfig(id = "test-software")
        val softwareProvider = softwareProviderFactory.create(softwareConfig, sessionContext.sessionExecution)
        kms.registerProvider(softwareProvider, makeDefaultKms = true)

        DefaultCallbacks.setCoseCryptoDefault(
            CoseCryptoProviderToCallbackAdapter(keyManagerServiceProvider = { kms }),
        )

        certificateService = CertificateServiceImpl(keyManagerService = kms)
        mdocSignService = createMdocSignService(sessionContext.sessionExecution)
    }
}

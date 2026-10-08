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
 */

package com.sphereon.did.manager.impl.testutil

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.impl.DidCreationDslProcessor
import com.sphereon.did.manager.impl.DidCreationDslProcessorImpl
import com.sphereon.did.manager.impl.DidManagerServiceImpl

expect fun createDidManagerTestAppGraph(testInstance: Any): AppGraph

class DidManagerTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph

    init {
        // Configure the software KMS provider via properties
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "kms.providers.softwaretest.keystore.overwriteAlias" to "true",
            ),
        )

        app = createDidManagerTestAppGraph(testInstance)

        // Destroy any existing contexts to ensure config is re-read
        app.userContextManager.destroyAll()
    }

    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)
    val dslProcessor: DidCreationDslProcessor = (session.graph as DidCreationDslProcessorImpl.Graph).didCreationDslProcessor
    val didManager: DidManager = (session.graph as DidManagerServiceImpl.Graph).didManager

    fun destroy() {
        app.userContextManager.destroyAll()
    }
}

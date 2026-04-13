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

package com.sphereon.did.manager.impl.testutil

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.impl.DidCreationDslProcessor
import com.sphereon.did.manager.impl.DidCreationDslProcessorImpl
import com.sphereon.did.manager.impl.DidManagerServiceImpl
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance

expect fun createDidManagerTestAppComponent(testInstance: Any): AppComponent

class DidManagerTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent

    init {
        // Configure the software KMS provider via properties
        // Property prefix is {appId}.{profile}.kms.providers = did-manager-test.test.kms.providers
        // When normalized (dashes -> dots): did.manager.test.test.kms.providers
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "did.manager.test.test.kms.providers.softwaretest.type" to "software",
                "did.manager.test.test.kms.providers.softwaretest.id" to "softwaretest",
                "did.manager.test.test.kms.providers.softwaretest.keystore.type" to "memory",
                "did.manager.test.test.kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "did.manager.test.test.kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "did.manager.test.test.kms.providers.softwaretest.keystore.overwriteAlias" to "true"
            )
        )

        app = createDidManagerTestAppComponent(testInstance)

        // Destroy any existing contexts to ensure config is re-read
        app.userContextManager.destroyAll()
    }

    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)
    val dslProcessor: DidCreationDslProcessor = (session.component as DidCreationDslProcessorImpl.Component).didCreationDslProcessor
    val didManager: DidManager = (session.component as DidManagerServiceImpl.Component).didManager
}

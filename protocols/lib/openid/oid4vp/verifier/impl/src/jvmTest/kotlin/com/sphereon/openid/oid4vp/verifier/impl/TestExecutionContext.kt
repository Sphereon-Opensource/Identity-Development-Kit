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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionInstance
import com.sphereon.openid.oid4vp.verifier.impl.createOid4vpRpJvmTestAppGraph
import kotlinx.coroutines.test.TestScope

/**
 * Test helper for creating execution contexts for RP tests
 */
object TestExecutionContext {
    /**
     * Create a test SessionInstance using the standard App -> UserContextManager -> SessionContextManager flow.
     */
    fun createSession(): SessionInstance {
        val testScope = TestScope()

        val app = createOid4vpRpJvmTestAppGraph(testScope, "test-verifier-app", "test", "1.0.0")
        val context = app.userContextManager.getAnonymous()
        return context.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)
    }

    /**
     * Create a test SessionExecution
     */
    fun createExecution(): SessionExecution = createSession().sessionExecution

    /**
     * Create a test SessionContext
     */
    fun createSessionContext(): SessionContext = createExecution().sessionContext
}

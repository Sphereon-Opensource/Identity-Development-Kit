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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.TestScope

/**
 * Test helper for creating execution contexts
 */
object TestExecutionContext {
    /**
     * Create a test SessionExecution
     */
    fun createExecution(): SessionExecution {
        val testScope = TestScope()
        val app = staticMinimalTestAppGraph(testScope, "test-app", "test", "1.0.0")
        val user = app.userContextManager.getAnonymous()
        val session = user.sessionContextManager.getAnonymous()
        return session.asCoreApiServiceGraph().serviceExecution
    }

    /**
     * Create a test SessionContext
     */
    fun createSessionContext(): SessionContext = createExecution().sessionContext
}

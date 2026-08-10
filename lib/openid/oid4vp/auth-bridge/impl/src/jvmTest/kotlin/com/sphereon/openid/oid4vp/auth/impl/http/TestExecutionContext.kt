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

package com.sphereon.openid.oid4vp.auth.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.session.SessionContext

/**
 * Test helper for creating execution contexts for OID4VP Authentication Bridge tests.
 *
 * Uses proper DI graph hierarchy to ensure all contributed bindings
 * (commands, adapters, stores) are available via injection.
 */
object TestExecutionContext {
    /**
     * Create a test SessionExecution using the full DI graph hierarchy.
     *
     * This ensures all commands and adapters are properly wired via DI.
     */
    fun createExecution(): SessionExecution {
        val app = createOid4vpAuthTestAppGraph(Unit)
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("oid4vp-auth-test", principalType = com.sphereon.di.context.PrincipalType.USER)
        return session.asCoreApiServiceGraph().serviceExecution
    }

    /**
     * Create a test SessionContext.
     */
    fun createSessionContext(): SessionContext = createExecution().sessionContext

    /**
     * Create test components and return the session graph for direct access to DI-wired services.
     *
     * Use this when you need direct access to the adapter or commands via the merged graph interfaces.
     *
     * @return A pair of (SessionExecution, SessionGraph) for accessing DI-wired services
     */
    fun createTestSession(): TestSession {
        val app = createOid4vpAuthTestAppGraph(Unit)
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("oid4vp-auth-test", principalType = com.sphereon.di.context.PrincipalType.USER)
        val execution = session.asCoreApiServiceGraph().serviceExecution
        return TestSession(execution, session.graph)
    }

    /**
     * Container for test session resources.
     */
    data class TestSession(
        val execution: SessionExecution,
        val sessionGraph: Any,
    ) {
        /**
         * Get the Oid4vpAuthHttpAdapter from the session graph.
         */
        val adapter: Oid4vpAuthHttpAdapter
            get() = (sessionGraph as Oid4vpAuthHttpAdapter.Graph).oid4vpAuthHttpAdapter
    }
}

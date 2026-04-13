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

package com.sphereon.core.defaults

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionExecutionTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "session-execution-test", "test-profile", "0.0.1-TEST"
    )

    // ========== SessionExecutionImpl Tests ==========

    @Test
    fun sessionExecutionHasSessionContext() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            assertNotNull(sessionExecution.sessionContext)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionExecutionHasLog() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            assertNotNull(sessionExecution.log)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionExecutionHasConf() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            assertNotNull(sessionExecution.conf)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionExecutionHasSessionContextManager() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            assertNotNull(sessionExecution.sessionContextManager)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionExecutionEqualsItself() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            assertTrue(sessionExecution == sessionExecution)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionExecutionHashCodeConsistent() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            val hashCode1 = sessionExecution.hashCode()
            val hashCode2 = sessionExecution.hashCode()
            assertEquals(hashCode1, hashCode2)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionExecutionNotEqualsNull() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            assertFalse(sessionExecution.equals(null))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionExecutionNotEqualsOtherType() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = sessionInstance.sessionExecution
            assertFalse(sessionExecution.equals("string"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun differentSessionsHaveDifferentExecutions() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session1 = userContextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session2 = userContextInstance.sessionContextManager.createOrGetFromId("session-2")

            val exec1 = session1.sessionExecution
            val exec2 = session2.sessionExecution

            // Different sessions should have different executions
            assertNotEquals(exec1.sessionContext.sessionId, exec2.sessionContext.sessionId)
        } finally {
            appComponent.destroy()
        }
    }
}

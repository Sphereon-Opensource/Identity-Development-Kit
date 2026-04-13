/*
 * Â© 2025 Sphereon International B.V.
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

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.di.session.SessionComponent
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.context.NoOpSessionContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import software.amazon.app.platform.scope.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionCommandExecutorTest {

    private class TestCommand(
        override val id: String,
        override val isEnabled: Boolean = true,
        private val supportsResult: Boolean = true,
        private val executeResult: IdkResult<Int, IdkErrorType> = Ok(42),
        private val supportsException: Throwable? = null,
        private val executeException: Throwable? = null
    ) : Command<String, Int, IdkErrorType> {
        override val subsystem = EventSubsystems.CUSTOM
        var supportsCalled: Int = 0
        var executeCalled: Int = 0
        var lastSupportsContext: SessionContext? = null

        override suspend fun supports(args: Any): Boolean {
            supportsCalled++
            supportsException?.let { throw it }
            return supportsResult
        }

        override suspend fun execute(args: String): IdkResult<Int, IdkErrorType> {
            executeCalled++
            executeException?.let { throw it }
            return executeResult
        }
    }

    private class FakeSessionInstance(
        override val sessionId: String = "test-session",
        override val sessionContext: SessionContext = NoOpSessionContext,
        private val serviceResolver: (String) -> Any
    ) : SessionInstance {
        override val sessionExecution: SessionExecution
            get() = throw NotImplementedError("Not needed for test")

        override val component: SessionComponent
            get() = throw NotImplementedError("Not needed for test")

        override val scope: Scope
            get() = throw NotImplementedError("Not needed for test")

        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for test")

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getService(id: String): T = serviceResolver(id) as T

        override fun addService(id: String, service: Any): Scope =
            throw NotImplementedError("Not needed for test")

        override fun isCurrentlyActive(): Boolean = true
        override fun makeActive(): Boolean = true
        override fun destroy() {}
        override suspend fun warmupCacheAsync() {}
    }

    private class FakeSessionContextManager(
        private val activeSession: SessionInstance
    ) : SessionContextManager {
        var getActiveCalls: Int = 0
        override val activeInstance: StateFlow<SessionInstance?> = MutableStateFlow(activeSession)

        override fun getActive(): SessionInstance {
            getActiveCalls++
            return activeSession
        }

        override fun hasActive(): Boolean = true
        override fun getById(sessionId: String, makeActive: Boolean): SessionInstance? = null
        override fun hasById(sessionId: String): Boolean = false
        override fun activateById(sessionId: String): Boolean = false
        override fun listIds(): Set<String> = setOf(activeSession.sessionId)
        override fun createOrGetFromCallbacks(sessionContextProvider: () -> SessionContext): SessionInstance = activeSession
        override fun createOrGetFromId(sessionId: String, makeActive: Boolean): SessionInstance = activeSession
        override fun destroyById(sessionId: String) {}
        override fun destroyAll() {}
        override fun getOrCreateBackgroundService(makeActive: Boolean): SessionInstance = activeSession
        override fun getAnonymous(makeActive: Boolean): SessionInstance = activeSession
        override fun getBackgroundServiceId(): String = "background-service"
    }

    @Test
    fun executeRejectsInvalidCommandIdBeforeSessionLookup() = runTest {
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { error("Service should not be resolved") })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>("invalid-no-dots", "args")

        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        assertEquals(0, manager.getActiveCalls)
    }

    @Test
    fun executeReturnsUnsupportedArgErrorWhenCommandDoesNotSupportArgs() = runTest {
        val command = TestCommand(
            id = "core.command.execute",
            supportsResult = false
        )
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { command })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>(command.id, "args")

        assertTrue(result.isErr)
        assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", result.error.code)
        assertEquals(1, command.supportsCalled)
        assertEquals(0, command.executeCalled)
    }

    @Test
    fun executeMapsNotFoundExceptionToNotFoundError() = runTest {
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { throw NotFoundException(resource = "service:core.command.execute") })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>("core.command.execute", "args")

        assertTrue(result.isErr)
        assertEquals("NOT_FOUND_ERROR", result.error.code)
        assertTrue(result.error.message.defaultMessage.contains("core.command.execute"))
    }

    @Test
    fun executeMapsNotFoundExceptionFromCommandExecutionToUnknownError() = runTest {
        val expectedException = NotFoundException(resource = "tenant:missing-tenant")
        val command = TestCommand(
            id = "core.command.execute",
            executeException = expectedException
        )
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { command })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>(command.id, "args")

        assertTrue(result.isErr)
        assertEquals("UNKNOWN_ERROR", result.error.code)
        assertSame(expectedException, result.error.exception)
        assertTrue(result.error.message.defaultMessage.contains(command.id))
    }

    @Test
    fun executeMapsNotFoundExceptionFromSupportsToUnknownError() = runTest {
        val expectedException = NotFoundException(resource = "principal:missing-principal")
        val command = TestCommand(
            id = "core.command.execute",
            supportsException = expectedException
        )
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { command })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>(command.id, "args")

        assertTrue(result.isErr)
        assertEquals("UNKNOWN_ERROR", result.error.code)
        assertSame(expectedException, result.error.exception)
        assertTrue(result.error.message.defaultMessage.contains(command.id))
        assertEquals(1, command.supportsCalled)
        assertEquals(0, command.executeCalled)
    }

    @Test
    fun executeMapsUnexpectedExceptionToUnknownError() = runTest {
        val expectedException = IllegalStateException("boom")
        val command = TestCommand(
            id = "core.command.execute",
            executeException = expectedException
        )
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { command })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>(command.id, "args")

        assertTrue(result.isErr)
        assertEquals("UNKNOWN_ERROR", result.error.code)
        assertSame(expectedException, result.error.exception)
        assertTrue(result.error.message.defaultMessage.contains(command.id))
    }

    @Test
    fun executeReturnsCommandResultWhenSupported() = runTest {
        val expected = Ok(1337)
        val command = TestCommand(
            id = "core.command.execute",
            supportsResult = true,
            executeResult = expected
        )
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { command })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>(command.id, "args")

        assertTrue(result.isOk)
        assertEquals(1337, result.value)
        assertEquals(1, command.supportsCalled)
        assertEquals(1, command.executeCalled)
    }

    @Test
    fun executeReturnsCommandDisabledWhenServiceIsDisabled() = runTest {
        val command = TestCommand(
            id = "core.command.execute",
            isEnabled = false,
            supportsResult = true
        )
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(serviceResolver = { command })
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>(command.id, "args")

        assertTrue(result.isErr)
        assertEquals("COMMAND_DISABLED", result.error.code)
        assertEquals(0, command.supportsCalled)
        assertEquals(0, command.executeCalled)
    }

    @Test
    fun executeDoesNotPassSessionContextToSupports() = runTest {
        val runtimeSessionContext = NoOpSessionContext
        val command = TestCommand(id = "core.command.execute", supportsResult = true)
        val manager = FakeSessionContextManager(
            activeSession = FakeSessionInstance(
                sessionContext = runtimeSessionContext,
                serviceResolver = { command }
            )
        )
        val executor = SessionCommandExecutorImpl(sessionContextManager = manager)

        val result = executor.execute<String, Int>(command.id, "args")

        assertTrue(result.isOk)
        assertEquals(null, command.lastSupportsContext)
    }
}

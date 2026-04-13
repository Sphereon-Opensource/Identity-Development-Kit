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

package com.sphereon.core.api.session

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandAggregatorServiceTest {

    private class MockSessionExecution(
        override val sessionContext: SessionContext = NoOpSessionContext
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError()
        override val log: SessionLogService
            get() = throw NotImplementedError()
        override val conf: ContextConfig
            get() = throw NotImplementedError()
    }

    private class TestAggregatorService(
        execution: SessionExecution
    ) : CommandAggregatorService(execution)

    private class TestCommand(
        private val result: IdkResult<String, IdkErrorType>
    ) : BaseCommand<String, String, IdkErrorType> {
        override suspend fun execute(args: String): IdkResult<String, IdkErrorType> = result
    }

    @Test
    fun sessionContextDelegatesToExecution() {
        val context = NoOpSessionContext
        val execution = MockSessionExecution(context)
        val service = TestAggregatorService(execution)

        assertEquals(context, service.sessionContext)
    }

    @Test
    fun canInvokeCommandsThroughDelegator() = runTest {
        val command = TestCommand(IdkResult.ok("result"))
        val execution = MockSessionExecution()
        val service = TestAggregatorService(execution)

        with(service) {
            val result = command("args")
            assertTrue(result.isOk)
            assertEquals("result", result.value)
        }
    }

    @Test
    fun executionIsAccessible() {
        val execution = MockSessionExecution()
        val service = TestAggregatorService(execution)

        assertEquals(execution, service.execution)
    }
}

class TypedCommandAggregatorServiceTest {

    private class MockSessionExecution(
        override val sessionContext: SessionContext = NoOpSessionContext
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError()
        override val log: SessionLogService
            get() = throw NotImplementedError()
        override val conf: ContextConfig
            get() = throw NotImplementedError()
    }

    private class SimpleCommand : BaseCommand<String, String, IdkErrorType> {
        override suspend fun execute(args: String): IdkResult<String, IdkErrorType> =
            IdkResult.ok("command result: $args")
    }

    private data class TestCommands(
        val simpleCommand: SimpleCommand
    )

    private class TestTypedAggregatorService(
        execution: SessionExecution,
        override val commands: TestCommands
    ) : TypedCommandAggregatorService<TestCommands>(execution)

    @Test
    fun commandsContainerIsAccessible() {
        val commands = TestCommands(SimpleCommand())
        val execution = MockSessionExecution()
        val service = TestTypedAggregatorService(execution, commands)

        assertEquals(commands, service.commands)
    }

    @Test
    fun canInvokeCommandsFromContainer() = runTest {
        val commands = TestCommands(SimpleCommand())
        val execution = MockSessionExecution()
        val service = TestTypedAggregatorService(execution, commands)

        with(service) {
            val result = commands.simpleCommand("test")
            assertTrue(result.isOk)
            assertEquals("command result: test", result.value)
        }
    }

    @Test
    fun sessionContextDelegatesToExecution() {
        val context = NoOpSessionContext
        val commands = TestCommands(SimpleCommand())
        val execution = MockSessionExecution(context)
        val service = TestTypedAggregatorService(execution, commands)

        assertEquals(context, service.sessionContext)
    }
}

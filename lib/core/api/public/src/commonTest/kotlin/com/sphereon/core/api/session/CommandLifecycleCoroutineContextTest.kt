/*
 * (c) 2026 Sphereon International B.V.
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
import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommandLifecycleCoroutineContextTest {
    @Test
    fun contributedContextWrapsCommandBodyAndAfterExecute() = runTest {
        val marker = ExecutionMarker("invocation")
        var beforeMarker: ExecutionMarker? = null
        var commandMarker: ExecutionMarker? = null
        var afterMarker: ExecutionMarker? = null
        val interceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "context-test"

                override fun executionCoroutineContext(
                    context: CommandExecutionContext,
                    args: Any,
                ): CoroutineContext = marker

                override suspend fun beforeExecute(
                    context: CommandExecutionContext,
                    args: Any,
                ): InterceptorVerdict {
                    beforeMarker = currentCoroutineContext()[ExecutionMarker]
                    return InterceptorVerdict.Continue
                }

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    afterMarker = currentCoroutineContext()[ExecutionMarker]
                }
            }
        val command =
            ContextAwareCommand(
                interceptorChain =
                    object : CommandLifecycleInterceptorChain {
                        override val interceptors: List<CommandLifecycleInterceptor> = listOf(interceptor)
                    },
                onExecute = {
                    commandMarker = currentCoroutineContext()[ExecutionMarker]
                },
            )

        assertEquals("ok", command.execute(Unit).value)
        assertSame(marker, beforeMarker)
        assertSame(marker, commandMarker)
        assertSame(marker, afterMarker)
    }

    @Test
    fun defaultInterceptorContextRemainsSourceCompatible() = runTest {
        val interceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "legacy"
            }
        val command =
            ContextAwareCommand(
                interceptorChain =
                    object : CommandLifecycleInterceptorChain {
                        override val interceptors: List<CommandLifecycleInterceptor> = listOf(interceptor)
                    },
            )

        assertEquals("ok", command.execute(Unit).value)
    }

    @Test
    fun contributedContextWrapsAfterExecuteWhenCommandIsDenied() = runTest {
        val marker = ExecutionMarker("denied")
        var commandExecuted = false
        var afterMarker: ExecutionMarker? = null
        var afterDenial: InterceptorVerdict.Deny? = null
        val interceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "deny"

                override suspend fun beforeExecute(
                    context: CommandExecutionContext,
                    args: Any,
                ): InterceptorVerdict = InterceptorVerdict.Deny("not allowed")

                override fun executionCoroutineContext(
                    context: CommandExecutionContext,
                    args: Any,
                ): CoroutineContext = marker

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    afterMarker = currentCoroutineContext()[ExecutionMarker]
                    afterDenial = denied
                }
            }
        val command =
            ContextAwareCommand(
                interceptorChain = chainOf(interceptor),
                onExecute = { commandExecuted = true },
            )

        val result = command.execute(Unit)

        assertFalse(commandExecuted)
        assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
        assertSame(marker, afterMarker)
        assertEquals("not allowed", afterDenial?.reason)
    }

    @Test
    fun contributedContextWrapsAfterExecuteWhenCommandThrows() = runTest {
        val marker = ExecutionMarker("exception")
        var afterMarker: ExecutionMarker? = null
        var afterCalled = false
        var afterResult: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>? = null
        val interceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "exception"

                override fun executionCoroutineContext(
                    context: CommandExecutionContext,
                    args: Any,
                ): CoroutineContext = marker

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    afterCalled = true
                    afterMarker = currentCoroutineContext()[ExecutionMarker]
                    afterResult = result
                }
            }
        val command =
            ContextAwareCommand(
                interceptorChain = chainOf(interceptor),
                onExecute = { throw IllegalStateException("boom") },
            )

        val thrown =
            try {
                command.execute(Unit)
                null
            } catch (expected: Throwable) {
                expected
            }

        assertIs<IllegalStateException>(thrown)
        assertTrue(afterCalled)
        assertSame(marker, afterMarker)
        assertNull(afterResult)
    }

    @Test
    fun cancellationFromContextContributionPropagatesAndRunsAfterHooks() = runTest {
        val cancellation = CancellationException("context cancelled")
        val marker = ExecutionMarker("context-cancellation")
        val afterCalls = mutableListOf<String>()
        val contextInterceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "context"
                override val order: Int = 10

                override fun executionCoroutineContext(
                    context: CommandExecutionContext,
                    args: Any,
                ): CoroutineContext = marker

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    assertSame(marker, currentCoroutineContext()[ExecutionMarker])
                    afterCalls += name
                }
            }
        val cancellingInterceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "cancel-context"
                override val order: Int = 20

                override fun executionCoroutineContext(
                    context: CommandExecutionContext,
                    args: Any,
                ): CoroutineContext = throw cancellation

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    assertSame(marker, currentCoroutineContext()[ExecutionMarker])
                    afterCalls += name
                }
            }
        val command = ContextAwareCommand(chainOf(contextInterceptor, cancellingInterceptor))

        val thrown = captureFailure { command.execute(Unit) }

        assertIs<CancellationException>(thrown)
        assertEquals(cancellation.message, thrown.message)
        assertEquals(listOf("cancel-context", "context"), afterCalls)
    }

    @Test
    fun cancellationFromBeforeHookPropagates() = runTest {
        val cancellation = CancellationException("before cancelled")
        val marker = ExecutionMarker("before-cancellation")
        var commandExecuted = false
        var afterCalled = false
        val interceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "cancel-before"

                override fun executionCoroutineContext(
                    context: CommandExecutionContext,
                    args: Any,
                ): CoroutineContext = marker

                override suspend fun beforeExecute(
                    context: CommandExecutionContext,
                    args: Any,
                ): InterceptorVerdict = throw cancellation

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    assertSame(marker, currentCoroutineContext()[ExecutionMarker])
                    afterCalled = true
                }
            }
        val command =
            ContextAwareCommand(
                interceptorChain = chainOf(interceptor),
                onExecute = { commandExecuted = true },
            )

        val thrown = captureFailure { command.execute(Unit) }

        assertIs<CancellationException>(thrown)
        assertEquals(cancellation.message, thrown.message)
        assertFalse(commandExecuted)
        assertTrue(afterCalled)
    }

    @Test
    fun cancellationFromCommandBodyPropagates() = runTest {
        val cancellation = CancellationException("body cancelled")
        var afterCalled = false
        val interceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = "body-observer"

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    afterCalled = true
                }
            }
        val command =
            ContextAwareCommand(
                interceptorChain = chainOf(interceptor),
                onExecute = { throw cancellation },
            )

        val thrown = captureFailure { command.execute(Unit) }

        assertIs<CancellationException>(thrown)
        assertEquals(cancellation.message, thrown.message)
        assertTrue(afterCalled)
    }

    @Test
    fun cancellationFromAfterHookPropagatesAfterEveryHookIsAttempted() = runTest {
        val cancellation = CancellationException("after cancelled")
        val afterCalls = mutableListOf<String>()

        fun interceptor(
            interceptorName: String,
            interceptorOrder: Int,
            failure: Throwable? = null,
        ): CommandLifecycleInterceptor =
            object : CommandLifecycleInterceptor {
                override val name: String = interceptorName
                override val order: Int = interceptorOrder

                override suspend fun afterExecute(
                    context: CommandExecutionContext,
                    args: Any,
                    result: IdkResult<Any, com.sphereon.core.api.error.IdkErrorType>?,
                    denied: InterceptorVerdict.Deny?,
                    durationMs: Long,
                ) {
                    afterCalls += name
                    failure?.let { throw it }
                }
            }

        val command =
            ContextAwareCommand(
                chainOf(
                    interceptor("low", 10),
                    interceptor("cancel", 20, cancellation),
                    interceptor("high", 30, IllegalStateException("observational failure")),
                ),
            )

        val thrown = captureFailure { command.execute(Unit) }

        assertIs<CancellationException>(thrown)
        assertEquals(cancellation.message, thrown.message)
        assertEquals(listOf("high", "cancel", "low"), afterCalls)
    }
}

private suspend fun captureFailure(block: suspend () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (failure: Throwable) {
        failure
    }

private fun chainOf(vararg interceptors: CommandLifecycleInterceptor): CommandLifecycleInterceptorChain =
    object : CommandLifecycleInterceptorChain {
        override val interceptors: List<CommandLifecycleInterceptor> = interceptors.toList()
    }

private class ExecutionMarker(
    val value: String,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ExecutionMarker>
}

private class ContextAwareCommand(
    interceptorChain: CommandLifecycleInterceptorChain,
    private val onExecute: suspend () -> Unit = {},
) : CommandAdapter<Unit, String, IdkError>(
        id = "test.lifecycle.context",
        interceptorChain = interceptorChain,
    ) {
    override suspend fun doExecute(
        args: Unit,
        applyDuring: (Unit) -> Unit,
    ): IdkResult<String, IdkError> {
        applyDuring(args)
        onExecute()
        return IdkResult.ok("ok")
    }
}

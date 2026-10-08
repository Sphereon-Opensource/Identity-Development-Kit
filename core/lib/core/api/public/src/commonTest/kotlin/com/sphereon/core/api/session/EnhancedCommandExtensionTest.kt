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
 *
 */

package com.sphereon.core.api.session

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.session.testing.CommandTestSupport.testContext
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EnhancedCommandExtensionTest {
    // Helper to create a test command
    private fun <A : Any, R : Any> testCommand(
        commandId: String,
        executeFn: suspend (A) -> IdkResult<R, IdkError>,
    ): Command<A, R, IdkError> =
        object : Command<A, R, IdkError> {
            override val id = commandId
            override val isEnabled = true
            override val subsystem = EventSubsystems.CUSTOM

            override suspend fun supports(args: Any) = true

            override suspend fun execute(args: A) = executeFn(args)
        }

    // === BeforeExecuteResult tests ===

    @Test
    fun beforeExecuteResultContinueStoresArgs() {
        val result = BeforeExecuteResult.Continue("test-args")

        assertIs<BeforeExecuteResult.Continue<String>>(result)
        assertEquals("test-args", result.args)
    }

    @Test
    fun beforeExecuteResultSkipIsSingleton() {
        val skip1 = BeforeExecuteResult.Skip
        val skip2 = BeforeExecuteResult.Skip

        assertIs<BeforeExecuteResult.Skip>(skip1)
        assertEquals(skip1, skip2)
    }

    @Test
    fun beforeExecuteResultShortCircuitStoresResult() {
        val okResult = Ok("success")
        val shortCircuit = BeforeExecuteResult.ShortCircuit(okResult)

        assertIs<BeforeExecuteResult.ShortCircuit<String, IdkError>>(shortCircuit)
        assertTrue(shortCircuit.result.isOk)
        assertEquals("success", shortCircuit.result.value)
    }

    @Test
    fun beforeExecuteResultShortCircuitWithError() {
        val errResult = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Test error"))
        val shortCircuit = BeforeExecuteResult.ShortCircuit(errResult)

        assertIs<BeforeExecuteResult.ShortCircuit<Nothing, IdkError>>(shortCircuit)
        assertTrue(shortCircuit.result.isErr)
    }

    // === IEnhancedCommandExecutionExtension default implementations ===

    @Test
    fun defaultBeforeExecuteReturnsContinueWithOriginalArgs() =
        runTest {
            val extension = object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {}
            val command =
                testCommand<String, Int>("test.ext.default.before") { input ->
                    Ok(input.length)
                }

            val result = extension.beforeExecute(command, "test-input")

            assertIs<BeforeExecuteResult.Continue<String>>(result)
            assertEquals("test-input", result.args)
        }

    @Test
    fun defaultDuringExecuteReturnsOriginalArgs() =
        runTest {
            val extension = object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {}
            val command =
                testCommand<String, Int>("test.ext.default.during") { input ->
                    Ok(input.length)
                }

            val args = extension.duringExecute(command, "original")

            assertEquals("original", args)
        }

    @Test
    fun defaultAfterExecuteReturnsOriginalResult() =
        runTest {
            val extension = object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {}
            val command =
                testCommand<String, Int>("test.ext.default.after") { input ->
                    Ok(input.length)
                }

            val originalResult = Ok(42)
            val result = extension.afterExecute(command, "test", originalResult)

            assertEquals(originalResult, result)
        }

    // === Custom extension implementations ===

    @Test
    fun extensionCanModifyArgsInBeforeExecute() =
        runTest {
            val extension =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.Continue(args.uppercase())
                }

            val command =
                testCommand<String, Int>("test.ext.modify.before") { input ->
                    Ok(input.length)
                }

            val result = extension.beforeExecute(command, "hello")

            assertIs<BeforeExecuteResult.Continue<String>>(result)
            assertEquals("HELLO", result.args)
        }

    @Test
    fun extensionCanSkipInBeforeExecute() =
        runTest {
            val extension =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.Skip
                }

            val command =
                testCommand<String, Int>("test.ext.skip") { _ ->
                    Ok(0)
                }

            val result = extension.beforeExecute(command, "test")

            assertIs<BeforeExecuteResult.Skip>(result)
        }

    @Test
    fun extensionCanShortCircuitInBeforeExecute() =
        runTest {
            val extension =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.ShortCircuit(Ok(999))
                }

            val command =
                testCommand<String, Int>("test.ext.shortcircuit") { _ ->
                    Ok(0)
                }

            val result = extension.beforeExecute(command, "test")

            assertIs<BeforeExecuteResult.ShortCircuit<Int, IdkError>>(result)
            assertTrue(result.result.isOk)
            assertEquals(999, result.result.value)
        }

    @Test
    fun extensionCanTransformArgsInDuringExecute() =
        runTest {
            val extension =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun duringExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): String = "$args-transformed"
                }

            val command =
                testCommand<String, Int>("test.ext.during.transform") { input ->
                    Ok(input.length)
                }

            val result = extension.duringExecute(command, "input")

            assertEquals("input-transformed", result)
        }

    @Test
    fun extensionCanTransformResultInAfterExecute() =
        runTest {
            val extension =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun afterExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                        result: IdkResult<Int, IdkError>,
                    ): IdkResult<Int, IdkError> = if (result.isOk) Ok(result.value * 2) else result
                }

            val command =
                testCommand<String, Int>("test.ext.after.transform") { input ->
                    Ok(input.length)
                }

            val result = extension.afterExecute(command, "test", Ok(5))

            assertTrue(result.isOk)
            assertEquals(10, result.value)
        }

    // === EnhancedExtensionAdapter tests ===

    @Test
    fun enhancedExtensionAdapterDuringExecutePassesThroughArgs() {
        val enhanced = object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {}
        val adapter = EnhancedExtensionAdapter(enhanced)

        val command =
            testCommand<String, Int>("test.adapter.during") { input ->
                Ok(input.length)
            }

        val result = adapter.duringExecute(command, "test")

        assertEquals("test", result)
    }

    @Test
    fun enhancedExtensionAdapterBeforeExecuteDoesNotThrow() {
        val enhanced = object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {}
        val adapter = EnhancedExtensionAdapter(enhanced)

        val command =
            testCommand<String, Int>("test.adapter.before") { input ->
                Ok(input.length)
            }

        // Should not throw - just a no-op
        adapter.beforeExecute(command, "test")
    }

    @Test
    fun enhancedExtensionAdapterAfterExecuteDoesNotThrow() {
        val enhanced = object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {}
        val adapter = EnhancedExtensionAdapter(enhanced)

        val command =
            testCommand<String, Int>("test.adapter.after") { input ->
                Ok(input.length)
            }

        // Should not throw - just a no-op
        adapter.afterExecute(command, "test", Ok(5))
    }

    // === CompositeEnhancedExtension tests ===

    @Test
    fun compositeExtensionChainsBeforeExecuteContinue() =
        runTest {
            val ext1 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.Continue("$args-ext1")
                }

            val ext2 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.Continue("$args-ext2")
                }

            val composite = CompositeEnhancedExtension(listOf(ext1, ext2))
            val command =
                testCommand<String, Int>("test.composite.chain") { input ->
                    Ok(input.length)
                }

            val result = composite.beforeExecute(command, "start")

            assertIs<BeforeExecuteResult.Continue<String>>(result)
            assertEquals("start-ext1-ext2", result.args)
        }

    @Test
    fun compositeExtensionStopsOnSkip() =
        runTest {
            var ext2Called = false

            val ext1 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.Skip
                }

            val ext2 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> {
                        ext2Called = true
                        return BeforeExecuteResult.Continue(args)
                    }
                }

            val composite = CompositeEnhancedExtension(listOf(ext1, ext2))
            val command =
                testCommand<String, Int>("test.composite.skip") { input ->
                    Ok(input.length)
                }

            val result = composite.beforeExecute(command, "test")

            assertIs<BeforeExecuteResult.Skip>(result)
            assertTrue(!ext2Called) // ext2 should not have been called
        }

    @Test
    fun compositeExtensionStopsOnShortCircuit() =
        runTest {
            var ext2Called = false

            val ext1 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.ShortCircuit(Ok(42))
                }

            val ext2 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> {
                        ext2Called = true
                        return BeforeExecuteResult.Continue(args)
                    }
                }

            val composite = CompositeEnhancedExtension(listOf(ext1, ext2))
            val command =
                testCommand<String, Int>("test.composite.shortcircuit") { input ->
                    Ok(input.length)
                }

            val result = composite.beforeExecute(command, "test")

            assertIs<BeforeExecuteResult.ShortCircuit<Int, IdkError>>(result)
            assertEquals(42, result.result.value)
            assertTrue(!ext2Called) // ext2 should not have been called
        }

    @Test
    fun compositeExtensionChainsDuringExecute() =
        runTest {
            val ext1 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun duringExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): String = "$args-during1"
                }

            val ext2 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun duringExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): String = "$args-during2"
                }

            val composite = CompositeEnhancedExtension(listOf(ext1, ext2))
            val command =
                testCommand<String, Int>("test.composite.during") { input ->
                    Ok(input.length)
                }

            val result = composite.duringExecute(command, "start")

            assertEquals("start-during1-during2", result)
        }

    @Test
    fun compositeExtensionChainsAfterExecuteInReverseOrder() =
        runTest {
            val order = mutableListOf<String>()

            val ext1 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun afterExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                        result: IdkResult<Int, IdkError>,
                    ): IdkResult<Int, IdkError> {
                        order.add("ext1")
                        return if (result.isOk) Ok(result.value + 1) else result
                    }
                }

            val ext2 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun afterExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                        result: IdkResult<Int, IdkError>,
                    ): IdkResult<Int, IdkError> {
                        order.add("ext2")
                        return if (result.isOk) Ok(result.value * 2) else result
                    }
                }

            val composite = CompositeEnhancedExtension(listOf(ext1, ext2))
            val command =
                testCommand<String, Int>("test.composite.after") { input ->
                    Ok(input.length)
                }

            val result = composite.afterExecute(command, "test", Ok(5))

            // afterExecute processes in reverse order: ext2 first, then ext1
            assertEquals(listOf("ext2", "ext1"), order)
            // 5 * 2 = 10, 10 + 1 = 11
            assertTrue(result.isOk)
            assertEquals(11, result.value)
        }

    @Test
    fun compositeExtensionWithEmptyList() =
        runTest {
            val composite = CompositeEnhancedExtension<String, Int, IdkError>(emptyList())
            val command =
                testCommand<String, Int>("test.composite.empty") { input ->
                    Ok(input.length)
                }

            val beforeResult = composite.beforeExecute(command, "test")
            assertIs<BeforeExecuteResult.Continue<String>>(beforeResult)
            assertEquals("test", beforeResult.args)

            val duringResult = composite.duringExecute(command, "original")
            assertEquals("original", duringResult)

            val afterResult = composite.afterExecute(command, "test", Ok(42))
            assertTrue(afterResult.isOk)
            assertEquals(42, afterResult.value)
        }

    // === compositeExtension function tests ===

    @Test
    fun compositeExtensionFunctionCreatesComposite() =
        runTest {
            val ext1 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.Continue("$args-1")
                }

            val ext2 =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun beforeExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): BeforeExecuteResult<String, Int, IdkError> = BeforeExecuteResult.Continue("$args-2")
                }

            val composite = compositeExtension(ext1, ext2)
            val command =
                testCommand<String, Int>("test.composite.fn") { input ->
                    Ok(input.length)
                }

            val result = composite.beforeExecute(command, "start")

            assertIs<BeforeExecuteResult.Continue<String>>(result)
            assertEquals("start-1-2", result.args)
        }

    @Test
    fun compositeExtensionFunctionWithSingleExtension() =
        runTest {
            val ext =
                object : IEnhancedCommandExecutionExtension<String, Int, IdkError> {
                    override suspend fun duringExecute(
                        service: Command<String, Int, IdkError>,
                        args: String,
                    ): String = "$args-transformed"
                }

            val composite = compositeExtension(ext)
            val command =
                testCommand<String, Int>("test.composite.single") { input ->
                    Ok(input.length)
                }

            val result = composite.duringExecute(command, "input")

            assertEquals("input-transformed", result)
        }
}

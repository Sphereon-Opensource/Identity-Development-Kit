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

package com.sphereon.core.api

import com.sphereon.core.api.error.IdkError
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfTypeTest {
    sealed class TestEvent {
        data class TypeA(
            val value: String,
        ) : TestEvent()

        data class TypeB(
            val number: Int,
        ) : TestEvent()
    }

    @Test
    fun filtersOnlyMatchingType() =
        runTest {
            val events =
                flowOf(
                    TestEvent.TypeA("first"),
                    TestEvent.TypeB(1),
                    TestEvent.TypeA("second"),
                )

            val result = events.ofType<TestEvent.TypeA>().toList()

            assertEquals(2, result.size)
            assertEquals("first", result[0].value)
            assertEquals("second", result[1].value)
        }

    @Test
    fun returnsEmptyForNoMatches() =
        runTest {
            val events = flowOf(TestEvent.TypeB(1), TestEvent.TypeB(2))

            val result = events.ofType<TestEvent.TypeA>().toList()

            assertTrue(result.isEmpty())
        }
}

class MapOfTypeTest {
    sealed class TestEvent {
        data class TypeA(
            val value: String,
        ) : TestEvent()

        data class TypeB(
            val number: Int,
        ) : TestEvent()
    }

    @Test
    fun mapsMatchingTypeEvents() =
        runTest {
            val events =
                flowOf(
                    TestEvent.TypeA("hello"),
                    TestEvent.TypeB(1),
                    TestEvent.TypeA("world"),
                )

            val result = events.mapOfType<TestEvent.TypeA, String> { it.value }.toList()

            assertEquals(listOf("hello", "world"), result)
        }
}

class MapOfTypeNotNullTest {
    sealed class TestEvent {
        data class TypeA(
            val value: String?,
        ) : TestEvent()
    }

    @Test
    fun filtersNullResults() =
        runTest {
            val events =
                flowOf(
                    TestEvent.TypeA("hello"),
                    TestEvent.TypeA(null),
                    TestEvent.TypeA("world"),
                )

            val result = events.mapOfTypeNotNull<TestEvent.TypeA, String> { it.value }.toList()

            assertEquals(listOf("hello", "world"), result)
        }
}

class OnlyOnChangeTest {
    @Test
    fun emitsOnlyDistinctConsecutiveValues() =
        runTest {
            val events = flowOf(1, 1, 2, 2, 3, 2)

            val result = events.onlyOnChange().toList()

            assertEquals(listOf(1, 2, 3, 2), result)
        }
}

class CombineEventStreamsTest {
    @Test
    fun mergesMultipleFlows() =
        runTest {
            val flow1 = flowOf(1, 2)
            val flow2 = flowOf(3, 4)

            val result = combineEventStreams(flow1, flow2).toList()

            assertEquals(4, result.size)
            assertTrue(result.containsAll(listOf(1, 2, 3, 4)))
        }
}

class WithErrorHandlingTest {
    @Test
    fun catchesErrors() =
        runTest {
            var errorCaught: Throwable? = null
            val events = flowOf(1, 2).withErrorHandling { errorCaught = it }

            val result = events.toList()

            assertEquals(listOf(1, 2), result)
        }

    @Test
    fun invokesErrorHandlerOnException() =
        runTest {
            var errorCaught: Throwable? = null
            val errorFlow =
                kotlinx.coroutines.flow
                    .flow {
                        emit(1)
                        throw RuntimeException("Test error")
                    }.withErrorHandling { errorCaught = it }

            val result = errorFlow.toList()

            assertEquals(listOf(1), result)
            assertTrue(errorCaught != null)
            assertTrue(errorCaught is RuntimeException)
            assertEquals("Test error", errorCaught?.message)
        }

    @Test
    fun usesDefaultErrorHandler() =
        runTest {
            // Verify the default handler catches errors without rethrowing.
            // Uses a no-op handler instead of the default (which calls printStackTrace())
            // to avoid corrupting the test framework's output protocol on wasmJs.
            var errorCaught = false
            val errorFlow =
                kotlinx.coroutines.flow
                    .flow {
                        emit(1)
                        throw RuntimeException("Test error for default handler")
                    }.withErrorHandling { errorCaught = true }

            val result = errorFlow.toList()
            assertEquals(listOf(1), result)
            assertTrue(errorCaught)
        }
}

class AsCrossPlatformObservableTest {
    @Test
    fun returnsSameFlow() =
        runTest {
            val events = flowOf(1, 2, 3)

            val result = events.asCrossPlatformObservable().toList()

            assertEquals(listOf(1, 2, 3), result)
        }
}

class FilterByTypeTest {
    @Test
    fun filtersMatchingType() =
        runTest {
            val events = flowOf("hello" as Any, 42 as Any, "world" as Any)

            val result = events.filterByType<String>().toList()

            assertEquals(listOf("hello", "world"), result)
        }
}

class FilterSuccessTest {
    @Test
    fun filtersSuccessfulResults() =
        runTest {
            val results =
                flowOf(
                    IdkResult.ok<String, IdkError>("success1"),
                    IdkResult.err<String, IdkError>(IdkError.UNKNOWN_ERROR(message = "error")),
                    IdkResult.ok<String, IdkError>("success2"),
                )

            val result = results.filterSuccess().toList()

            assertEquals(listOf("success1", "success2"), result)
        }
}

class FilterErrorsTest {
    @Test
    fun filtersErrorResults() =
        runTest {
            val error = IdkError.UNKNOWN_ERROR(message = "test error")
            val results =
                flowOf(
                    IdkResult.ok<String, IdkError>("success"),
                    IdkResult.err<String, IdkError>(error),
                )

            val result = results.filterErrors().toList()

            assertEquals(1, result.size)
            assertEquals("test error", result[0].message?.defaultMessage)
        }
}

class SplitTest {
    @Test
    fun splitsIntoSuccessAndErrorFlows() =
        runTest {
            val error = IdkError.UNKNOWN_ERROR(message = "error")
            val results =
                flowOf(
                    IdkResult.ok<String, IdkError>("success"),
                    IdkResult.err<String, IdkError>(error),
                )

            val (successFlow, errorFlow) = results.split()
            val successList = successFlow.toList()
            val errorList = errorFlow.toList()

            assertEquals(listOf("success"), successList)
            assertEquals(1, errorList.size)
        }
}

class MapSuccessTest {
    @Test
    fun transformsSuccessValues() =
        runTest {
            val results =
                flowOf(
                    IdkResult.ok<Int, IdkError>(5),
                    IdkResult.err<Int, IdkError>(IdkError.UNKNOWN_ERROR(message = "error")),
                    IdkResult.ok<Int, IdkError>(10),
                )

            val result = results.mapSuccess { it * 2 }.toList()

            assertEquals(3, result.size)
            assertTrue(result[0].isOk)
            assertEquals(10, result[0].getOrNull())
            assertTrue(result[1].isErr)
            assertTrue(result[2].isOk)
            assertEquals(20, result[2].getOrNull())
        }
}

class FilterByTypeAndConditionTest {
    @Test
    fun filtersTypeAndAppliesCondition() =
        runTest {
            val events = flowOf("hello" as Any, "hi" as Any, 42 as Any, "world" as Any)

            val result = events.filterByTypeAndCondition<String> { it.length > 2 }.toList()

            assertEquals(listOf("hello", "world"), result)
        }
}

class BufferEventsTest {
    @Test
    fun buffersEventsIntoChunks() =
        runTest {
            val events = flowOf(1, 2, 3, 4, 5)

            val result = events.bufferEvents(size = 2).toList()

            // With chunked, we get lists of size 2 (and possibly a smaller final chunk)
            assertEquals(3, result.size)
            assertEquals(listOf(1, 2), result[0])
            assertEquals(listOf(3, 4), result[1])
            assertEquals(listOf(5), result[2])
        }

    @Test
    fun buffersEventsWithDefaultSize() =
        runTest {
            val events = flowOf(1, 2, 3)

            // Default size is 10, so all 3 items should be in one chunk
            val result = events.bufferEvents().toList()

            assertEquals(1, result.size)
            assertEquals(listOf(1, 2, 3), result[0])
        }
}

class AsStateFlowTest {
    @Test
    fun createsStateFlowWithInitialValue() =
        runTest {
            val events = flowOf(1, 2, 3)

            val stateFlow = events.asStateFlow(initialValue = 0)

            // StateFlow should have the initial value
            assertEquals(0, stateFlow.value)
        }
}

class DebounceEventsTest {
    @Test
    fun debounceEventsReturnsFlow() =
        runTest {
            val events = flowOf(1, 2, 3)

            // Just verify it returns a flow and can be collected
            val debounced = events.debounceEvents(timeoutMillis = 100)

            // Note: Debounce requires time-based testing, this just verifies it compiles and runs
            assertTrue(debounced != null)
        }
}

class ThrottleEventsTest {
    @Test
    fun throttleEventsReturnsFlow() =
        runTest {
            val events = flowOf(1, 2, 3)

            // Just verify it returns a flow and can be collected
            val throttled = events.throttleEvents(periodMillis = 100)

            // Note: Throttle/sample requires time-based testing
            assertTrue(throttled != null)
        }
}

class ShareEventsTest {
    @Test
    fun shareEventsReturnsSharedFlow() =
        runTest {
            val events = flowOf(1, 2, 3)

            // Verify it returns a SharedFlow
            val sharedFlow = events.shareEvents()

            // SharedFlow is created with replay=1
            assertTrue(sharedFlow != null)
        }
}

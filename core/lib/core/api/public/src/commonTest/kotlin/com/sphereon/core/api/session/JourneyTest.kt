package com.sphereon.core.api.session

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventSubsystems
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JourneyTest {
    private val spec = JourneySpecReference("RFC", "1")

    private class RecordingCommand<I : Any, O : Any>(
        override val id: String,
        private val executed: MutableList<String>,
        private val accepts: (Any) -> Boolean = { true },
        private val body: (I) -> IdkResult<O, IdkError>,
    ) : Command<I, O, IdkError> {
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = accepts(args)

        override suspend fun execute(args: I): IdkResult<O, IdkError> {
            executed += "$id:$args"
            return body(args)
        }
    }

    private fun <I : Any, O : Any> step(
        id: String,
        command: Command<I, O, IdkError>,
        extensionPoint: String? = null,
    ) = JourneyStep(JourneyStepContract(id, command.id, listOf(spec), extensionPoint), command)

    @Test
    fun contractOrderMatchesCommandsThatActuallyExecute() = runTest {
        val executed = mutableListOf<String>()
        val journey =
            JourneyBuilder
                .start("test.journey.run", listOf(spec), step("first", RecordingCommand<String, Int>("test.steps.first", executed) { Ok(it.length) }), IdkErrorCommandErrorMapper)
                .then(step("second", RecordingCommand<Int, String>("test.steps.second", executed) { Ok("$it") }, extensionPoint = "authorize"))
                .build()

        val result = journey.execute("abc")

        assertTrue(result.isOk)
        assertEquals("3", result.value)
        assertEquals(listOf("test.steps.first:abc", "test.steps.second:3"), executed)
        assertEquals(listOf("first", "second"), journey.contract.steps.map { it.id })
        assertEquals(listOf("test.steps.first", "test.steps.second"), journey.contract.steps.map { it.commandId })
        assertEquals("authorize", journey.contract.steps[1].extensionPoint)
        assertEquals("test.journey.run", journey.id)
        assertEquals(EventSubsystems.CUSTOM, journey.subsystem)
    }

    @Test
    fun commandFailureStopsLaterStepsAndPreservesError() = runTest {
        val executed = mutableListOf<String>()
        val journey =
            JourneyBuilder
                .start(
                    "test.journey.run",
                    listOf(spec),
                    step("first", RecordingCommand<String, Int>("test.steps.first", executed) { Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "rejected")) }),
                    IdkErrorCommandErrorMapper,
                ).then(step("second", RecordingCommand<Int, String>("test.steps.second", executed) { Ok("unreachable") }))
                .build()

        val result = journey.execute("input")

        assertTrue(result.isErr)
        assertEquals("rejected", result.error.message.defaultMessage)
        assertEquals(listOf("test.steps.first:input"), executed)
    }

    @Test
    fun middleStepFailurePreventsEveryLaterStep() = runTest {
        val executed = mutableListOf<String>()
        val journey =
            JourneyBuilder
                .start("test.journey.run", listOf(spec), step("first", RecordingCommand<String, Int>("test.steps.first", executed) { Ok(1) }), IdkErrorCommandErrorMapper)
                .then(step("second", RecordingCommand<Int, Int>("test.steps.second", executed) { Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "middle")) }))
                .then(step("third", RecordingCommand<Int, String>("test.steps.third", executed) { Ok("unreachable") }))
                .build()

        val result = journey.execute("x")

        assertEquals("middle", result.error.message.defaultMessage)
        assertEquals(listOf("test.steps.first:x", "test.steps.second:1"), executed)
    }

    @Test
    fun unsupportedIntermediateStateUsesTheJourneyErrorMapperAndStops() = runTest {
        val executed = mutableListOf<String>()
        val mapperCalls = mutableListOf<String>()
        val mapper =
            object : CommandErrorMapper<IdkError> by IdkErrorCommandErrorMapper {
                override fun unsupportedArg(command: Any, arg: Any): IdkError {
                    mapperCalls += "unsupported:$arg"
                    return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "normalized")
                }
            }
        val journey =
            JourneyBuilder
                .start("test.journey.run", listOf(spec), step("first", RecordingCommand<String, Int>("test.steps.first", executed) { Ok(7) }), mapper)
                .then(step("second", RecordingCommand<Int, String>("test.steps.second", executed, accepts = { it != 7 }) { Ok("unreachable") }))
                .build()

        val result = journey.execute("x")

        assertEquals("normalized", result.error.message.defaultMessage)
        assertEquals(listOf("unsupported:7"), mapperCalls)
        assertEquals(listOf("test.steps.first:x"), executed)
    }

    @Test
    fun journeySupportIsTheFirstStepSupport() = runTest {
        val journey =
            JourneyBuilder
                .start(
                    "test.journey.run",
                    listOf(spec),
                    step("first", RecordingCommand<String, Int>("test.steps.first", mutableListOf(), accepts = { it is String }) { Ok(1) }),
                    IdkErrorCommandErrorMapper,
                ).build()

        assertTrue(journey.supports("raw"))
        assertFalse(journey.supports(42))
    }

    @Test
    fun stepMetadataMustNameTheExecutedCommand() {
        val command = RecordingCommand<String, Int>("test.steps.first", mutableListOf()) { Ok(1) }

        assertFailsWith<IllegalArgumentException> {
            JourneyStep(JourneyStepContract("first", "test.steps.other", listOf(spec)), command)
        }
    }

    @Test
    fun duplicateStepIdsAreRejected() {
        val executed = mutableListOf<String>()
        val builder =
            JourneyBuilder.start(
                "test.journey.run",
                listOf(spec),
                step("first", RecordingCommand<String, String>("test.steps.first", executed) { Ok(it) }),
                IdkErrorCommandErrorMapper,
            )

        assertFailsWith<IllegalArgumentException> {
            builder.then(step("first", RecordingCommand<String, String>("test.steps.again", executed) { Ok(it) }))
        }
    }

    @Test
    fun buildersAreImmutableSoBranchesKeepTheirOwnContracts() = runTest {
        val executed = mutableListOf<String>()
        val base =
            JourneyBuilder.start(
                "test.journey.run",
                listOf(spec),
                step("first", RecordingCommand<String, String>("test.steps.first", executed) { Ok(it) }),
                IdkErrorCommandErrorMapper,
            )
        val left = base.then(step("left", RecordingCommand<String, String>("test.steps.left", executed) { Ok("L") })).build()
        val right = base.then(step("right", RecordingCommand<String, String>("test.steps.right", executed) { Ok("R") })).build()

        assertEquals(listOf("first", "left"), left.contract.steps.map { it.id })
        assertEquals(listOf("first", "right"), right.contract.steps.map { it.id })
        assertEquals("R", right.execute("x").value)
        assertEquals(listOf("test.steps.first:x", "test.steps.right:x"), executed)
    }
}

/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.defaults.session

import com.sphereon.core.api.session.SessionScopeResolutionOutcome
import com.sphereon.core.api.session.SessionScopeResolutionPhase
import com.sphereon.core.api.session.SessionScopeResolutionSource
import com.sphereon.core.api.session.SessionScopeResolutionTelemetry
import software.amazon.app.platform.scope.Scoped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionScopeTimingAttemptTest {
    @Test
    fun newlyCreatedSessionReportsEveryExecutedPhaseInOrderWithNonNegativeIntegerDurations() {
        val telemetry = CapturingResolutionTelemetry()
        val timing = timing(telemetry)
        val phases =
            listOf(
                SessionScopeResolutionPhase.CONTEXT_SCOPE_RESOLUTION,
                SessionScopeResolutionPhase.SESSION_CONTEXT_CREATION,
                SessionScopeResolutionPhase.METRO_GRAPH_CREATION,
                SessionScopeResolutionPhase.SCOPE_CREATION,
                SessionScopeResolutionPhase.SESSION_INSTANCE_INITIALIZATION,
                SessionScopeResolutionPhase.SCOPED_INSTANCE_MATERIALIZATION,
                SessionScopeResolutionPhase.SCOPED_INSTANCE_REGISTRATION,
            )

        phases.forEach { phase -> timing.measure(phase) { Unit } }
        timing.complete(SessionScopeResolutionOutcome.CREATE)

        assertEquals(phases, telemetry.phases.map { it.first })
        assertTrue(telemetry.phases.all { (_, durationUs) -> durationUs >= 0L })
        assertEquals(SessionScopeResolutionOutcome.CREATE, telemetry.outcome)
        assertTrue(telemetry.totalUs >= 0L)
    }

    @Test
    fun reusedSessionReportsOnlyLookupAndNoCreationOrMaterializationPhases() {
        val telemetry = CapturingResolutionTelemetry()
        val timing = timing(telemetry)

        timing.measure(SessionScopeResolutionPhase.CONTEXT_SCOPE_RESOLUTION) { Unit }
        timing.complete(SessionScopeResolutionOutcome.REUSE)

        assertEquals(
            listOf(SessionScopeResolutionPhase.CONTEXT_SCOPE_RESOLUTION),
            telemetry.phases.map { it.first },
        )
        assertFalse(telemetry.phases.any { it.first == SessionScopeResolutionPhase.METRO_GRAPH_CREATION })
        assertFalse(telemetry.phases.any { it.first == SessionScopeResolutionPhase.SCOPED_INSTANCE_MATERIALIZATION })
        assertEquals(SessionScopeResolutionOutcome.REUSE, telemetry.outcome)
    }

    @Test
    fun materializedInstancesAreEvaluatedOnceCountedAndRegisteredAsTheExactCollection() {
        val telemetry = CapturingResolutionTelemetry()
        val timing = timing(telemetry)
        val materialized = linkedSetOf(TestScoped("one"), TestScoped("two"))
        var evaluations = 0
        var registered: Set<TestScoped>? = null

        val returned =
            materializeAndRegisterScopedInstances(
                timing = timing,
                materialize = {
                    evaluations += 1
                    materialized
                },
                register = { registered = it },
            )

        assertEquals(1, evaluations)
        assertEquals(2, telemetry.capturedMaterializedInstanceCount)
        assertSame(materialized, returned)
        assertSame(materialized, registered)
        assertEquals(
            listOf(
                SessionScopeResolutionPhase.SCOPED_INSTANCE_MATERIALIZATION,
                SessionScopeResolutionPhase.SCOPED_INSTANCE_REGISTRATION,
            ),
            telemetry.phases.map { it.first },
        )
    }

    @Test
    fun graphCreationFailureReportsFailure() {
        val telemetry = CapturingResolutionTelemetry()
        var completionLine = ""
        val timing =
            SessionScopeTimingAttempt(
                source = SessionScopeResolutionSource.ID_SECURE_IDENTITY,
                telemetry = telemetry,
                emitLog = { completionLine = it },
                clock = IncrementingClock(),
            )
        val failure = IllegalStateException("graph failed")

        val thrown = assertFailsWith<IllegalStateException> {
            try {
                timing.measure(SessionScopeResolutionPhase.METRO_GRAPH_CREATION) { throw failure }
            } catch (caught: Throwable) {
                timing.complete(SessionScopeResolutionOutcome.FAILURE, caught)
                throw caught
            }
        }

        assertSame(failure, thrown)
        assertEquals(SessionScopeResolutionOutcome.FAILURE, telemetry.outcome)
        assertSame(failure, telemetry.failure)
        assertTrue(completionLine.startsWith("VDX_SESSION_SCOPE_TIMING outcome=failure "))
    }

    @Test
    fun materializationFailureReportsFailureAndNeverRegisters() {
        val telemetry = CapturingResolutionTelemetry()
        var completionLine = ""
        val timing =
            SessionScopeTimingAttempt(
                source = SessionScopeResolutionSource.ID_SECURE_IDENTITY,
                telemetry = telemetry,
                emitLog = { completionLine = it },
                clock = IncrementingClock(),
            )
        var registered = false

        assertFailsWith<IllegalStateException> {
            try {
                materializeAndRegisterScopedInstances<TestScoped>(
                    timing = timing,
                    materialize = { error("materialization failed") },
                    register = { registered = true },
                )
            } catch (caught: Throwable) {
                timing.complete(SessionScopeResolutionOutcome.FAILURE, caught)
                throw caught
            }
        }

        assertFalse(registered)
        assertEquals(
            listOf(SessionScopeResolutionPhase.SCOPED_INSTANCE_MATERIALIZATION),
            telemetry.phases.map { it.first },
        )
        assertEquals(SessionScopeResolutionOutcome.FAILURE, telemetry.outcome)
        assertTrue(completionLine.startsWith("VDX_SESSION_SCOPE_TIMING outcome=failure "))
    }

    @Test
    fun telemetryFailuresDoNotChangeMeasuredSessionBehavior() {
        val timing =
            SessionScopeTimingAttempt(
                source = SessionScopeResolutionSource.ID_SECURE_IDENTITY,
                telemetry = ThrowingResolutionTelemetry,
                emitLog = { error("log backend failed") },
                clock = IncrementingClock(),
            )

        val result = timing.measure(SessionScopeResolutionPhase.CONTEXT_SCOPE_RESOLUTION) { "session" }
        timing.setMaterializedInstanceCount(3)
        timing.setSecureDetailsPresent(true)
        timing.complete(SessionScopeResolutionOutcome.CREATE)

        assertEquals("session", result)
    }

    @Test
    fun completionLogUsesTheFixedSchemaWithoutIdentifiers() {
        var line = ""
        val timing =
            SessionScopeTimingAttempt(
                source = SessionScopeResolutionSource.CALLBACKS,
                telemetry = null,
                emitLog = { line = it },
                clock = IncrementingClock(),
            )
        timing.setMaterializedInstanceCount(4)
        timing.complete(SessionScopeResolutionOutcome.CREATE)

        assertTrue(line.startsWith("VDX_SESSION_SCOPE_TIMING outcome=create source=callbacks totalUs="))
        listOf(
            "contextScopeUs=0",
            "sessionContextUs=0",
            "metroGraphUs=0",
            "scopeCreationUs=0",
            "instanceInitializationUs=0",
            "scopedInstancesMaterializationUs=0",
            "scopedInstancesRegistrationUs=0",
            "materializedInstanceCount=4",
        ).forEach { field -> assertTrue(line.contains(field), field) }
        listOf("tenantId", "sessionId", "contextId", "principalId", "commandId", "traceId", "spanId", "class")
            .forEach { prohibited -> assertFalse(line.contains(prohibited), prohibited) }
    }

    @Test
    fun completionIsEmittedExactlyOncePerResolutionAttempt() {
        val telemetry = CapturingResolutionTelemetry()
        val lines = mutableListOf<String>()
        val timing =
            SessionScopeTimingAttempt(
                source = SessionScopeResolutionSource.CALLBACKS,
                telemetry = telemetry,
                emitLog = lines::add,
                clock = IncrementingClock(),
            )

        timing.complete(SessionScopeResolutionOutcome.CREATE)
        timing.complete(SessionScopeResolutionOutcome.FAILURE, IllegalStateException("late failure"))

        assertEquals(1, lines.size)
        assertEquals(SessionScopeResolutionOutcome.CREATE, telemetry.outcome)
    }

    @Test
    fun totalIncludesContextResolutionCompletedBeforeTheOwningTimingOperationStarts() {
        val telemetry = CapturingResolutionTelemetry()
        val timing =
            SessionScopeTimingAttempt(
                source = SessionScopeResolutionSource.BACKGROUND,
                telemetry = telemetry,
                emitLog = {},
                clock = IncrementingClock(),
                initialElapsedUs = 100L,
            )

        timing.recordPhase(SessionScopeResolutionPhase.CONTEXT_SCOPE_RESOLUTION, 100L)
        timing.complete(SessionScopeResolutionOutcome.REUSE)

        assertEquals(107L, telemetry.totalUs)
        assertTrue(telemetry.totalUs >= telemetry.phases.sumOf { it.second })
    }

    private fun timing(telemetry: CapturingResolutionTelemetry) =
        SessionScopeTimingAttempt(
            source = SessionScopeResolutionSource.ID_SECURE_IDENTITY,
            telemetry = telemetry,
            emitLog = {},
            clock = IncrementingClock(),
        )
}

private class IncrementingClock : SessionScopeMonotonicClock {
    private var current = 0L
    override fun nowUs(): Long = current.also { current += 7L }
}

private data class TestScoped(val id: String) : Scoped

private class CapturingResolutionTelemetry : SessionScopeResolutionTelemetry {
    val phases = mutableListOf<Pair<SessionScopeResolutionPhase, Long>>()
    var capturedMaterializedInstanceCount = 0
    var outcome: SessionScopeResolutionOutcome? = null
    var totalUs = -1L
    var failure: Throwable? = null

    override fun setSecureDetailsPresent(present: Boolean) = Unit

    override fun recordPhase(phase: SessionScopeResolutionPhase, durationUs: Long) {
        phases += phase to durationUs
    }

    override fun setMaterializedInstanceCount(count: Int) {
        capturedMaterializedInstanceCount = count
    }

    override fun complete(outcome: SessionScopeResolutionOutcome, totalUs: Long, failure: Throwable?) {
        this.outcome = outcome
        this.totalUs = totalUs
        this.failure = failure
    }
}

private object ThrowingResolutionTelemetry : SessionScopeResolutionTelemetry {
    override fun setSecureDetailsPresent(present: Boolean): Unit = error("telemetry failed")
    override fun recordPhase(phase: SessionScopeResolutionPhase, durationUs: Long): Unit = error("telemetry failed")
    override fun setMaterializedInstanceCount(count: Int): Unit = error("telemetry failed")
    override fun complete(outcome: SessionScopeResolutionOutcome, totalUs: Long, failure: Throwable?): Unit =
        error("telemetry failed")
}

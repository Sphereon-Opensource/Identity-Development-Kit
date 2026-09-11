/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.api.session

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding

/** Bounded origins for SessionScope resolution telemetry. */
enum class SessionScopeResolutionSource(val value: String) {
    CALLBACKS("callbacks"),
    ID_SECURE_IDENTITY("id-secure-identity"),
    BACKGROUND("background"),
    ANONYMOUS("anonymous"),
}

/** Fixed SessionScope phases. Values are safe as metric labels. */
enum class SessionScopeResolutionPhase(val value: String, val attributeName: String) {
    CONTEXT_SCOPE_RESOLUTION("context-scope-resolution", "session.phase.context_scope_resolution.us"),
    SESSION_CONTEXT_CREATION("session-context-creation", "session.phase.session_context_creation.us"),
    METRO_GRAPH_CREATION("metro-graph-creation", "session.phase.metro_graph_creation.us"),
    SCOPE_CREATION("scope-creation", "session.phase.scope_creation.us"),
    SESSION_INSTANCE_INITIALIZATION("session-instance-initialization", "session.phase.session_instance_initialization.us"),
    SCOPED_INSTANCE_MATERIALIZATION(
        "scoped-instance-materialization",
        "session.phase.scoped_instance_materialization.us",
    ),
    SCOPED_INSTANCE_REGISTRATION("scoped-instance-registration", "session.phase.scoped_instance_registration.us"),
    SELECTED_COMMAND_RESOLUTION("selected-command-resolution", "session.phase.selected_command_resolution.us"),
}

enum class SessionScopeResolutionOutcome(val value: String) {
    CREATE("create"),
    REUSE("reuse"),
    FAILURE("failure"),
}

/**
 * Typed boundary between IDK session management and an optional telemetry backend.
 * Implementations must fail open: telemetry must never change session behavior.
 */
interface SessionScopeTelemetry {
    fun startResolution(
        source: SessionScopeResolutionSource,
        secureDetailsPresent: Boolean,
        makeActive: Boolean,
    ): SessionScopeResolutionTelemetry

    fun startDestruction(): SessionScopeDestructionTelemetry
}

interface SessionScopeResolutionTelemetry {
    fun setSecureDetailsPresent(present: Boolean)

    fun recordPhase(phase: SessionScopeResolutionPhase, durationUs: Long)

    fun setMaterializedInstanceCount(count: Int)

    fun complete(
        outcome: SessionScopeResolutionOutcome,
        totalUs: Long,
        failure: Throwable? = null,
    )
}

interface SessionScopeDestructionTelemetry {
    fun complete(success: Boolean, totalUs: Long, failure: Throwable? = null)
}

@ContributesTo(AppScope::class)
interface SessionScopeTelemetryOptionalProvider {
    @OptionalBinding
    val optionalSessionScopeTelemetry: SessionScopeTelemetry? get() = null
}

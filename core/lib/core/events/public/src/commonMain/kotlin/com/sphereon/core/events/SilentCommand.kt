/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.session.Command
import com.sphereon.core.compat.JsExportCompat

/**
 * Marker interface for commands that should NOT emit events.
 *
 * Commands that implement this interface will be skipped by the
 * EventCommandExtension, preventing automatic event emission.
 * This is essential for event-related commands to avoid infinite loops.
 *
 * ## Use Cases
 *
 * 1. **Event System Commands**: Commands that store, query, or transmit
 *    events must not emit events about themselves, otherwise:
 *    - StoreEventCommand emits COMMAND_COMPLETED
 *    - Which triggers another StoreEventCommand
 *    - Which emits another COMMAND_COMPLETED
 *    - Infinite loop!
 *
 * 2. **High-Frequency Commands**: Commands that execute very frequently
 *    (health checks, metrics) may want to opt out of event emission
 *    to avoid noise.
 *
 * 3. **Internal Infrastructure**: System-level commands that should
 *    operate silently without audit trail.
 *
 * ## Usage
 *
 * ```kotlin
 * // Event storage command - must not emit events
 * interface StoreEventCommand : SilentCommand<StoreEventArgs, StoreEventResult, IdkError>
 *
 * // Query events command - must not emit events
 * interface QueryEventsCommand : SilentCommand<QueryEventsArgs, QueryEventsResult, IdkError>
 *
 * // Event transmission command - must not emit events
 * interface TransmitEventCommand : SilentCommand<TransmitEventArgs, TransmitEventResult, IdkError>
 * ```
 *
 * ## Implementation
 *
 * ```kotlin
 * @Inject
 * @SingleIn(SessionScope::class)
 * @ContributesBinding(SessionScope::class, binding = binding<StoreEventCommand>())
 * class StoreEventCommandImpl(
 *     execution: SessionExecution,
 *     private val eventStore: EventStore
 * ) : ExecutionScopedCommandAdapter<StoreEventArgs, StoreEventResult, IdkError>(
 *     id = "events.store",
 *     execution = execution
 * ), StoreEventCommand {
 *     // Implementation...
 * }
 * ```
 *
 * ## Alternative: CommandEventConfig
 *
 * For commands that aren't specifically event-related but should not
 * emit events, consider using [CommandEventConfig.excludePatterns]
 * instead of implementing SilentCommand:
 *
 * ```kotlin
 * val config = CommandEventConfig(
 *     excludePatterns = listOf("health.**", "metrics.**")
 * )
 * ```
 *
 * The difference:
 * - `SilentCommand`: Hard-coded, cannot be overridden by configuration
 * - `CommandEventConfig.excludePatterns`: Configurable, can be changed at runtime
 *
 * @see CommandEventConfig for configurable event emission control
 * @see EventService for event emission
 */
@JsExportCompat
interface SilentCommand<Arg : Any, SuccessResult : Any, ErrorResult : IdkErrorType> : Command<Arg, SuccessResult, ErrorResult>

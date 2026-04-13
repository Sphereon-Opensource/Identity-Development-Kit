/*
 * Copyright (c) 2025 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.events

import kotlinx.serialization.Serializable

/**
 * Configuration for automatic command event emission.
 *
 * This configuration controls which commands automatically emit
 * lifecycle events (started, completed, failed) when executed.
 * It provides fine-grained control through patterns and overrides.
 *
 * ## Pattern Matching
 *
 * Command IDs are matched against glob patterns:
 * - `*` matches any sequence except `.`
 * - `**` matches any sequence including `.`
 * - `?` matches a single character
 *
 * Examples:
 * - `party.**` matches `party.create`, `party.update`, `party.delete`
 * - `crypto.*` matches `crypto.sign`, `crypto.verify` (but not `crypto.kms.rotate`)
 * - `**.health` matches `system.health`, `api.v1.health`
 *
 * ## Evaluation Order
 *
 * 1. Check global `enabled` flag - if false, no events are emitted
 * 2. Check `commandOverrides` for exact command ID match
 * 3. Check `excludePatterns` - if any pattern matches, no event
 * 4. Check `includePatterns` - if any pattern matches, emit event
 *
 * ## Usage
 *
 * ```kotlin
 * val config = CommandEventConfig(
 *     enabled = true,
 *     includePatterns = listOf("party.**", "resource.**"),
 *     excludePatterns = listOf("**.health", "**.metrics"),
 *     commandOverrides = mapOf("party.list" to false),  // Never emit for this
 *     emitOnSuccess = true,
 *     emitOnFailure = true
 * )
 *
 * if (config.shouldEmit("party.create")) {
 *     // Emit event for this command
 * }
 * ```
 *
 * @see SilentCommand for commands that should never emit events
 */
@Serializable
data class CommandEventConfig(
    /**
     * Global enable/disable for all command events.
     * If false, no command events are emitted regardless of patterns.
     */
    val enabled: Boolean = true,

    /**
     * Include patterns for command IDs.
     * Commands matching any of these patterns will emit events
     * (unless excluded or overridden).
     * Default is ["**"] which matches all commands.
     */
    val includePatterns: List<String> = listOf("**"),

    /**
     * Exclude patterns for command IDs.
     * Commands matching any of these patterns will NOT emit events.
     * Exclusions are checked before inclusions.
     */
    val excludePatterns: List<String> = emptyList(),

    /**
     * Per-command overrides.
     * Map of command ID to enabled status.
     * These take precedence over patterns.
     */
    val commandOverrides: Map<String, Boolean> = emptyMap(),

    /**
     * Whether to emit events when commands succeed.
     */
    val emitOnSuccess: Boolean = true,

    /**
     * Whether to emit events when commands fail.
     */
    val emitOnFailure: Boolean = true,

    /**
     * Whether to emit COMMAND_STARTED events.
     * Set to false to only emit completion events.
     */
    val emitOnStart: Boolean = true,

    /**
     * Whether to sign command events.
     */
    val signEvents: Boolean = false,

    /**
     * Key alias for signing (null = use default).
     */
    val signingKeyAlias: String? = null,

    /**
     * Whether to encrypt command event payloads.
     */
    val encryptPayload: Boolean = false,

    /**
     * Key alias for encryption (null = use default).
     */
    val encryptionKeyAlias: String? = null
) {
    /**
     * Check if events should be emitted for a command.
     *
     * @param commandId The command ID to check
     * @return true if events should be emitted for this command
     */
    fun shouldEmit(commandId: String): Boolean {
        // 1. Check global enabled
        if (!enabled) return false

        // 2. Check per-command override
        commandOverrides[commandId]?.let { return it }

        // 3. Check exclude patterns first
        if (excludePatterns.any { matchGlob(commandId, it) }) return false

        // 4. Check include patterns
        return includePatterns.any { matchGlob(commandId, it) }
    }

    /**
     * Check if a started event should be emitted.
     */
    fun shouldEmitStart(commandId: String): Boolean =
        emitOnStart && shouldEmit(commandId)

    /**
     * Check if a success event should be emitted.
     */
    fun shouldEmitSuccess(commandId: String): Boolean =
        emitOnSuccess && shouldEmit(commandId)

    /**
     * Check if a failure event should be emitted.
     */
    fun shouldEmitFailure(commandId: String): Boolean =
        emitOnFailure && shouldEmit(commandId)

    companion object {
        /**
         * Default configuration that emits events for all commands.
         */
        val DEFAULT = CommandEventConfig()

        /**
         * Configuration that disables all command events.
         */
        val DISABLED = CommandEventConfig(enabled = false)

        /**
         * Configuration that only emits failure events.
         */
        val FAILURES_ONLY = CommandEventConfig(
            emitOnSuccess = false,
            emitOnStart = false,
            emitOnFailure = true
        )
    }
}

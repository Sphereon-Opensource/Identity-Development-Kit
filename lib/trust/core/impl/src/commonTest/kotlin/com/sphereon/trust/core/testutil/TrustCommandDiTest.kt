/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.testutil

import com.sphereon.trust.core.command.CheckRevocationCommand
import com.sphereon.trust.core.command.GetTrustAnchorsCommand
import com.sphereon.trust.core.command.RefreshTrustAnchorsCommand
import com.sphereon.trust.core.command.ValidateTrustCommand
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * DI integration test verifying that all trust commands are discoverable
 * through the SessionScopedCommandRegistry via @DependencyGraph wiring.
 *
 * Note: Commands from trust-did, trust-etsi, and trust-x509 modules are referenced
 * by their string COMMAND_IDs since those modules are not dependencies of trust-core-impl.
 */
class TrustCommandDiTest {
    // Command IDs from external trust modules (not importable here)
    private val validateX509TrustCommandId = "trust.x509.validate"
    private val validateDidTrustCommandId = "trust.did.validate"
    private val validateEtsiTrustCommandId = "trust.etsi.validate"
    private val resolveEtsiTrustListCommandId = "trust.etsi.resolve"

    private fun createContext() = TrustTestContext("trust-di-test", this)

    @Test
    fun coreCommandsAreRegistered() {
        val registry = createContext().commandRegistry

        assertTrue(
            registry.has(ValidateTrustCommand.COMMAND_ID),
            "ValidateTrustCommand should be registered",
        )
        assertTrue(
            registry.has(GetTrustAnchorsCommand.COMMAND_ID),
            "GetTrustAnchorsCommand should be registered",
        )
        assertTrue(
            registry.has(RefreshTrustAnchorsCommand.COMMAND_ID),
            "RefreshTrustAnchorsCommand should be registered",
        )
        assertTrue(
            registry.has(CheckRevocationCommand.COMMAND_ID),
            "CheckRevocationCommand should be registered",
        )
    }

    @Test
    fun x509CommandIsRegistered() {
        val registry = createContext().commandRegistry

        assertTrue(
            registry.has(validateX509TrustCommandId),
            "ValidateX509TrustCommand should be registered",
        )
        assertNotNull(registry.get(validateX509TrustCommandId))
    }

    @Test
    fun didCommandIsRegistered() {
        val registry = createContext().commandRegistry

        assertTrue(
            registry.has(validateDidTrustCommandId),
            "ValidateDidTrustCommand should be registered",
        )
        assertNotNull(registry.get(validateDidTrustCommandId))
    }

    @Test
    fun etsiValidateCommandIsRegistered() {
        val registry = createContext().commandRegistry

        assertTrue(
            registry.has(validateEtsiTrustCommandId),
            "ValidateEtsiTrustCommand should be registered",
        )
        assertNotNull(registry.get(validateEtsiTrustCommandId))
    }

    @Test
    fun etsiResolveCommandIsRegistered() {
        val registry = createContext().commandRegistry

        assertTrue(
            registry.has(resolveEtsiTrustListCommandId),
            "ResolveEtsiTrustListCommand should be registered",
        )
        assertNotNull(registry.get(resolveEtsiTrustListCommandId))
    }

    @Test
    fun allTrustCommandsDiscoverable() {
        val registry = createContext().commandRegistry
        val trustCommands = registry.listCommandIds().filter { it.startsWith("trust.") }

        // 4 core + x509 + did + etsi validate + etsi resolve = 8
        // (oidfed command impl lives in the OpenID Federation repo)
        assertTrue(
            trustCommands.size >= 8,
            "Expected at least 8 trust commands, found ${trustCommands.size}: $trustCommands",
        )
    }

    @Test
    fun commandIdsFollowTrustConvention() {
        val registry = createContext().commandRegistry
        val trustCommands = registry.listCommandIds().filter { it.startsWith("trust.") }

        for (commandId in trustCommands) {
            val segments = commandId.split(".")
            assertTrue(
                segments.size >= 3,
                "Command ID should have at least 3 segments (trust.{service}.{action}): $commandId",
            )
        }
    }
}
